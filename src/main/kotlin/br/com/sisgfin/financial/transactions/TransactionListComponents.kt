package br.com.sisgfin.financial.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.*
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import java.time.DayOfWeek
import java.time.LocalDate

// ── Agrupamento temporal ──────────────────────────────────────────────────────

data class TxGroup(val label: String, val badgeColor: Color, val items: List<Transaction>)

@Composable
fun groupByTimeSection(items: List<Transaction>): List<TxGroup> {
    val today     = LocalDate.now()
    val tomorrow  = today.plusDays(1)
    val weekEnd   = today.with(DayOfWeek.SUNDAY)
    val active    = setOf(TransactionStatus.PENDING, TransactionStatus.PARTIAL, TransactionStatus.SCHEDULED)

    val overdue   = items.filter { it.status == TransactionStatus.OVERDUE }
    val dueToday  = items.filter { it.status in active && it.dueDate.toLocalDate() == today }
    val dueTomorrow = items.filter { it.status in active && it.dueDate.toLocalDate() == tomorrow }
    val thisWeek  = items.filter {
        it.status in active && it.dueDate.toLocalDate().let { d -> d > tomorrow && d <= weekEnd }
    }
    val later     = items.filter { it.status in active && it.dueDate.toLocalDate() > weekEnd }

    return buildList {
        if (overdue.isNotEmpty())     add(TxGroup("Vencidos (${overdue.size})",       WsDanger,        overdue))
        if (dueToday.isNotEmpty())    add(TxGroup("Hoje (${dueToday.size})",           WsWarning,       dueToday))
        if (dueTomorrow.isNotEmpty()) add(TxGroup("Amanhã (${dueTomorrow.size})",      WsAccent,        dueTomorrow))
        if (thisWeek.isNotEmpty())    add(TxGroup("Esta semana (${thisWeek.size})",    WsTextSecondary, thisWeek))
        if (later.isNotEmpty())       add(TxGroup("Próximas (${later.size})",          WsTextSecondary, later))
    }
}

// ── TransactionListView ───────────────────────────────────────────────────────

/**
 * Tabela de lançamentos reutilizável.
 * [grouped] = true → agrupamento temporal (A Pagar, A Receber).
 * [grouped] = false → lista plana (Lançamentos, Extrato).
 * [selectable] reservado para F2 (checkboxes de seleção múltipla).
 */
@Composable
fun TransactionListView(
    items: List<Transaction>,
    selectedId: Int?,
    grouped: Boolean = true,
    selectable: Boolean = false,
    counterparties: CounterpartyMap = CounterpartyMap.EMPTY,
    onRowClick: (Transaction) -> Unit,
    onRowDoubleClick: (Transaction) -> Unit,
    onContextMenu: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        TransactionTableHeader()
        HorizontalDivider(color = WsBorder)

        if (grouped) {
            GroupedTransactionTable(
                groups         = groupByTimeSection(items),
                selectedId     = selectedId,
                counterparties = counterparties,
                onSingleClick  = onRowClick,
                onDoubleClick  = onRowDoubleClick,
                onContextMenu  = onContextMenu
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    TransactionRow(
                        item             = item,
                        isSelected       = selectedId == item.id,
                        counterpartyName = counterparties.nameFor(item),
                        onSingleClick    = { onRowClick(item) },
                        onDoubleClick    = { onRowDoubleClick(item) },
                        onContextMenu    = { onContextMenu(item) }
                    )
                    HorizontalDivider(color = WsBorder.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
internal fun TransactionTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WsElevated)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableHeaderCell("TIPO",       Modifier.weight(0.9f))
        TableHeaderCell("DESCRIÇÃO",  Modifier.weight(2.2f))
        TableHeaderCell("VENCIMENTO", Modifier.weight(1f))
        TableHeaderCell("VALOR",      Modifier.weight(1f), TextAlign.End)
        TableHeaderCell("STATUS",     Modifier.weight(1f), TextAlign.Center)
    }
}

// ── Tabela agrupada ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupedTransactionTable(
    groups: List<TxGroup>,
    selectedId: Int?,
    counterparties: CounterpartyMap = CounterpartyMap.EMPTY,
    onSingleClick: (Transaction) -> Unit,
    onDoubleClick: (Transaction) -> Unit,
    onContextMenu: (Transaction) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groups.forEach { group ->
            stickyHeader(key = "header_${group.label}") {
                GroupHeader(group)
            }
            items(group.items, key = { it.id }) { item ->
                TransactionRow(
                    item             = item,
                    isSelected       = selectedId == item.id,
                    counterpartyName = counterparties.nameFor(item),
                    onSingleClick    = { onSingleClick(item) },
                    onDoubleClick    = { onDoubleClick(item) },
                    onContextMenu    = { onContextMenu(item) }
                )
                HorizontalDivider(color = WsBorder.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
internal fun GroupHeader(group: TxGroup) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WsElevated)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(group.badgeColor, CircleShape)
            )
            Text(
                text  = group.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize      = 11.sp,
                    letterSpacing = 0.6.sp
                ),
                color = group.badgeColor
            )
        }
        HorizontalDivider(color = WsBorder)
    }
}

// ── SummaryTileRow ────────────────────────────────────────────────────────────

enum class SummaryTileTone { NEUTRAL, DANGER, WARNING, SUCCESS }

data class SummaryTile(
    val label: String,
    val amount: Money,
    val count: Int,
    val tone: SummaryTileTone = SummaryTileTone.NEUTRAL,
    val isSelected: Boolean = false,
    val onClick: () -> Unit = {}
)

@Composable
fun SummaryTileRow(
    tiles: List<SummaryTile>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tiles.forEach { tile ->
            SummaryTileCard(tile = tile, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryTileCard(tile: SummaryTile, modifier: Modifier = Modifier) {
    val toneColor = when (tile.tone) {
        SummaryTileTone.DANGER  -> WsDanger
        SummaryTileTone.WARNING -> WsWarning
        SummaryTileTone.SUCCESS -> WsSuccess
        SummaryTileTone.NEUTRAL -> WsAccent
    }
    val borderColor = if (tile.isSelected) toneColor else WsBorder
    val bgColor     = if (tile.isSelected) toneColor.copy(alpha = 0.08f) else WsSurface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { tile.onClick() }
            .padding(12.dp)
    ) {
        Column {
            Text(
                tile.label,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                color = if (tile.isSelected) toneColor else WsTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                MoneyFormatter.format(tile.amount),
                style = MaterialTheme.typography.titleMedium,
                color = if (tile.isSelected) toneColor else WsTextPrimary
            )
            Text(
                "${tile.count} título${if (tile.count != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = WsTextSecondary
            )
        }
    }
}

// ── TotalsFooter ──────────────────────────────────────────────────────────────

/**
 * Rodapé fixo de totais para telas de lista de lançamentos.
 * [selected] e [selectedCount] ficam visíveis apenas quando > 0.
 * [actions] slot composable para botões de ação em lote (ex: Quitar selecionados).
 */
@Composable
fun TotalsFooter(
    displayed: Money,
    count: Int,
    selected: Money? = null,
    selectedCount: Int = 0,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    HorizontalDivider(color = WsBorder)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WsElevated)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selected != null && selectedCount > 0) {
            Text(
                "$selectedCount selecionado${if (selectedCount != 1) "s" else ""}" +
                    " · ${MoneyFormatter.format(selected)}",
                style = MaterialTheme.typography.bodyMedium,
                color = WsAccent
            )
        }
        actions()
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Total: ${MoneyFormatter.format(displayed)}" +
                " ($count lançamento${if (count != 1) "s" else ""})",
            style = MaterialTheme.typography.bodySmall,
            color = WsTextSecondary
        )
    }
}
