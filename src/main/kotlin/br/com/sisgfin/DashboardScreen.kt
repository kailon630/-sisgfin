package br.com.sisgfin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.cashflow.DailyCashFlowEntry
import br.com.sisgfin.dashboard.DashboardViewModel
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionType
import br.com.sisgfin.presentation.state.AccountBalance
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        WsLoaderFullscreen()
        return
    }

    uiState.errorMessage?.let { err ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Error, null, tint = WsDanger, modifier = Modifier.size(32.dp))
                Text(err, color = WsDanger)
                WsButton("Tentar novamente", icon = Icons.Default.Refresh, onClick = { viewModel.load() })
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

        // ── Cabeçalho ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Painel Financeiro", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Visão consolidada — motor financeiro ativo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextSecondary
                )
            }
            WsIconButton(Icons.Default.Refresh, onClick = { viewModel.load() })
        }

        // ── KPI tiles ──────────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiTile(
                label  = "SALDO CONSOLIDADO",
                value  = MoneyFormatter.format(uiState.consolidatedBalance),
                sub    = "${uiState.accountBalances.size} conta(s) ativa(s)",
                color  = WsAccent,
                icon   = Icons.Outlined.AccountBalance,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label  = "RECEITA DO MÊS",
                value  = MoneyFormatter.format(uiState.monthIncome),
                sub    = "lançamentos liquidados",
                color  = WsSuccess,
                icon   = Icons.Default.TrendingUp,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label  = "DESPESA DO MÊS",
                value  = MoneyFormatter.format(uiState.monthExpense),
                sub    = "saldo: ${MoneyFormatter.format(uiState.monthBalance)}",
                color  = WsDanger,
                icon   = Icons.Default.TrendingDown,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label  = "VENCIDOS",
                value  = "${uiState.overdueCount}",
                sub    = MoneyFormatter.format(uiState.overdueAmount),
                color  = WsDanger,
                icon   = Icons.Outlined.Warning,
                modifier = Modifier.weight(1f),
                alert  = uiState.overdueCount > 0
            )
            KpiTile(
                label  = "A VENCER (7 DIAS)",
                value  = "${uiState.dueSoonCount}",
                sub    = MoneyFormatter.format(uiState.dueSoonAmount),
                color  = WsWarning,
                icon   = Icons.Outlined.CalendarMonth,
                modifier = Modifier.weight(1f),
                alert  = uiState.dueSoonCount > 0
            )
            KpiTile(
                label  = "RECEBÍVEIS VENCIDOS",
                value  = "${uiState.overdueReceivablesCount}",
                sub    = MoneyFormatter.format(uiState.overdueReceivablesAmount),
                color  = WsInfo,
                icon   = Icons.Default.TrendingUp,
                modifier = Modifier.weight(1f),
                alert  = uiState.overdueReceivablesCount > 0
            )
        }

        // ── Corpo principal ────────────────────────────────────────────────────
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

            // Coluna principal — últimas liquidações
            Column(modifier = Modifier.weight(0.65f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Últimas Liquidações",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                RecentPaidTable(uiState.recentPaid)
            }

            // Coluna lateral — alertas + saldos por conta
            Column(modifier = Modifier.weight(0.35f), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Saldos por conta
                DashboardCard(
                    title = "Saldos por Conta",
                    icon  = Icons.Outlined.AccountBalance,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.accountBalances.isEmpty()) {
                        Text("Nenhuma conta ativa.", style = MaterialTheme.typography.bodySmall, color = WsTextSecondary)
                    } else {
                        uiState.accountBalances.forEach { ab ->
                            AccountBalanceRow(ab)
                            HorizontalDivider(color = WsBorder.copy(alpha = 0.3f))
                        }
                    }
                }

                // Vencidos
                if (uiState.overdueItems.isNotEmpty()) {
                    DashboardCard(
                        title = "Vencidos (${uiState.overdueCount})",
                        icon  = Icons.Outlined.Warning,
                        titleColor = WsDanger,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.overdueItems.take(5).forEach { tx ->
                            AlertTransactionRow(tx, WsDanger)
                            HorizontalDivider(color = WsBorder.copy(alpha = 0.3f))
                        }
                        if (uiState.overdueCount > 5) {
                            Text(
                                "… e mais ${uiState.overdueCount - 5} vencido(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = WsTextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // A vencer em 7 dias
                if (uiState.dueSoonItems.isNotEmpty()) {
                    DashboardCard(
                        title = "A Vencer em 7 Dias (${uiState.dueSoonCount})",
                        icon  = Icons.Outlined.CalendarMonth,
                        titleColor = WsWarning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.dueSoonItems.take(5).forEach { tx ->
                            AlertTransactionRow(tx, WsWarning)
                            HorizontalDivider(color = WsBorder.copy(alpha = 0.3f))
                        }
                        if (uiState.dueSoonCount > 5) {
                            Text(
                                "… e mais ${uiState.dueSoonCount - 5} a vencer",
                                style = MaterialTheme.typography.labelSmall,
                                color = WsTextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Recebíveis vencidos
                if (uiState.overdueReceivables.isNotEmpty()) {
                    DashboardCard(
                        title = "Recebíveis Vencidos (${uiState.overdueReceivablesCount})",
                        icon  = Icons.Default.TrendingUp,
                        titleColor = WsInfo,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.overdueReceivables.take(5).forEach { tx ->
                            AlertTransactionRow(tx, WsInfo)
                            HorizontalDivider(color = WsBorder.copy(alpha = 0.3f))
                        }
                        if (uiState.overdueReceivablesCount > 5) {
                            Text(
                                "… e mais ${uiState.overdueReceivablesCount - 5} recebível(is)",
                                style = MaterialTheme.typography.labelSmall,
                                color = WsTextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Projeção de caixa — próximos dias com saldo acumulado
                if (uiState.cashFlowPreview.isNotEmpty()) {
                    val projOk = !uiState.projectedBalance.isNegative()
                    DashboardCard(
                        title = "Projeção — Próximos Dias",
                        icon  = Icons.AutoMirrored.Outlined.ShowChart,
                        titleColor = WsAccent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.cashFlowPreview.forEach { entry ->
                            CashFlowPreviewRow(entry)
                            HorizontalDivider(color = WsBorder.copy(alpha = 0.3f))
                        }
                        // Badge de saldo projetado final
                        val badgeColor = if (projOk) WsSuccess else WsDanger
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Saldo projetado (7 dias)",
                                style = MaterialTheme.typography.labelSmall,
                                color = WsTextSecondary
                            )
                            Text(
                                MoneyFormatter.format(uiState.projectedBalance),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                ),
                                color = badgeColor
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Componentes ───────────────────────────────────────────────────────────────

// Item 13: ícone aumentado de 14dp → 20dp (watermark sutil no canto)
// Item 12: altura wrapContent + min 96dp em vez de fixo 100dp
@Composable
fun KpiTile(
    label: String,
    value: String,
    sub: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    alert: Boolean = false
) {
    val borderColor = if (alert) color else WsBorder
    Surface(
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (alert) color.copy(alpha = 0.05f) else WsSurface,
        border = androidx.compose.foundation.BorderStroke(if (alert) 1.5.dp else 1.dp, borderColor)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            // Ícone como watermark no canto superior direito
            Icon(
                icon,
                contentDescription = null,
                tint = color.copy(alpha = 0.18f),
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.TopEnd)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (alert) color else WsTextSecondary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    style = WsMoneyStyleLarge.copy(
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(sub, style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
            }
        }
    }
}

@Composable
private fun RecentPaidTable(transactions: List<Transaction>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
            .background(WsSurface)
    ) {
        if (transactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum lançamento liquidado.", color = WsTextSecondary,
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(WsElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableHeaderCell("DESCRIÇÃO",   Modifier.weight(2.5f))
                    TableHeaderCell("TIPO",        Modifier.width(90.dp))
                    TableHeaderCell("DATA PAG.",   Modifier.width(100.dp))
                    TableHeaderCell("VALOR",       Modifier.width(110.dp), TextAlign.End)
                }
                HorizontalDivider(color = WsBorder)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(transactions, key = { it.id }) { tx ->
                        RecentPaidRow(tx)
                        HorizontalDivider(color = WsBorder.copy(alpha = 0.35f))
                    }
                }
            }
        }
    }
}

// Item 1 + 3: WsTableRow com hover + MoneyText com fonte monospace
@Composable
private fun RecentPaidRow(tx: Transaction) {
    val typeColor = when (tx.type) {
        TransactionType.INCOME, TransactionType.REVERSAL -> WsSuccess
        TransactionType.EXPENSE                          -> WsDanger
        else                                             -> WsTextSecondary
    }
    val value   = tx.paidAmount ?: tx.amount
    val payDate = tx.paymentDate?.format(dateFmt) ?: tx.dueDate.format(dateFmt)

    WsTableRow {
        Text(
            tx.description,
            modifier = Modifier.weight(2.5f),
            style    = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(modifier = Modifier.width(90.dp)) {
            Surface(shape = RoundedCornerShape(4.dp), color = typeColor.copy(alpha = 0.1f)) {
                Text(
                    tx.type.displayName,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = typeColor
                )
            }
        }
        Text(
            payDate,
            modifier = Modifier.width(100.dp),
            style    = MaterialTheme.typography.bodySmall,
            color    = WsTextSecondary
        )
        // Item 1: MoneyText com fonte monospace alinha valores na coluna
        MoneyText(
            amount    = value,
            modifier  = Modifier.width(110.dp),
            color     = typeColor,
            textAlign = TextAlign.End
        )
    }
}

// Item 12: cards com heightIn(max) para evitar overflow quando há muitos itens
@Composable
private fun DashboardCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
    maxContentHeight: androidx.compose.ui.unit.Dp = 220.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val effectiveTitleColor = if (titleColor == Color.Unspecified) WsTextPrimary else titleColor
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
            .background(WsSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = effectiveTitleColor.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = effectiveTitleColor
            )
        }
        HorizontalDivider(color = WsBorder)
        // Item 12: limita altura máxima do conteúdo com scroll se necessário
        Column(
            modifier = Modifier
                .heightIn(max = maxContentHeight)
                .then(Modifier),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content
        )
    }
}

@Composable
private fun AccountBalanceRow(ab: AccountBalance) {
    val balColor = when {
        ab.balance.isNegative() -> WsDanger
        ab.balance.isZero()     -> WsTextSecondary
        else                    -> WsSuccess
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            ab.account.name,
            modifier = Modifier.weight(1f),
            style    = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Item 1: MoneyText com monospace para alinhamento de saldos
        MoneyText(amount = ab.balance, color = balColor, textAlign = TextAlign.End)
    }
}

@Composable
private fun AlertTransactionRow(tx: Transaction, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.description,
                style    = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Vence: ${tx.dueDate.format(dateFmt)}",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor
            )
        }
        Spacer(Modifier.width(8.dp))
        // Item 1: MoneyText para alinhamento consistente
        MoneyText(amount = tx.amount, color = accentColor, textAlign = TextAlign.End)
    }
}

@Composable
fun KpiCard(label: String, value: String, color: Color, modifier: Modifier, icon: ImageVector? = null) {
    KpiTile(
        label = label, value = value, sub = "", color = color,
        icon = icon ?: Icons.Default.Info, modifier = modifier
    )
}

@Composable
fun DashboardSidebarSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    DashboardCard(title = title, icon = icon, content = content)
}

@Composable
fun AlertItem(title: String, desc: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)))
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
        }
    }
}

@Composable
private fun CashFlowPreviewRow(entry: DailyCashFlowEntry) {
    val isToday = entry.date == LocalDate.now()
    val balColor = when {
        entry.projectedBalance.isNegative() -> WsDanger
        else -> WsSuccess
    }
    val dayName = entry.date.dayOfWeek
        .getDisplayName(TextStyle.SHORT, Locale("pt", "BR"))
        .replaceFirstChar { it.uppercase() }
    val dateStr = "$dayName, ${entry.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isToday) "Hoje, ${entry.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}" else dateStr,
                style = MaterialTheme.typography.bodySmall,
                color = if (isToday) WsAccent else WsTextPrimary
            )
            Text(
                "${entry.transactions.size} compromisso${if (entry.transactions.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = WsTextSecondary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "−${MoneyFormatter.format(entry.totalOutflow)}",
                style = MaterialTheme.typography.labelSmall,
                color = WsDanger
            )
            Text(
                MoneyFormatter.format(entry.projectedBalance),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = balColor
            )
        }
    }
}

@Composable
fun EventItem(date: String, title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(date, style = MaterialTheme.typography.labelMedium, color = WsAccent, modifier = Modifier.width(45.dp))
        Text(title, style = MaterialTheme.typography.bodySmall)
    }
}
