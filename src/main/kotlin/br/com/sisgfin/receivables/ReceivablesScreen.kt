package br.com.sisgfin.receivables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.*
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun ReceivablesScreen(viewModel: ReceivablesViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        WsLoaderFullscreen()
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Contas a Receber", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${uiState.totalCount} recebível(is) em aberto · ${MoneyFormatter.format(uiState.grandTotal)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextSecondary
                )
            }
            WsIconButton(Icons.Outlined.Refresh, onClick = { viewModel.load() })
        }

        // Aging tiles
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val agingColors = listOf(WsTextSecondary, WsWarning, Color(0xFFE09A30), WsDanger)
            uiState.aging.forEachIndexed { i, bucket ->
                AgingTile(
                    label = bucket.label,
                    count = bucket.count,
                    total = bucket.total,
                    accentColor = agingColors.getOrElse(i) { WsDanger },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        uiState.errorMessage?.let { err ->
            Text(err, color = WsDanger, style = MaterialTheme.typography.bodyMedium)
        }

        // Grouped list
        if (uiState.groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.AccountBalanceWallet, null, tint = WsTextDisabled, modifier = Modifier.size(40.dp))
                    Text("Nenhum recebível em aberto.", color = WsTextSecondary)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
                    .background(WsSurface)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(WsElevated).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableHeaderCell("CLIENTE / DESCRIÇÃO", Modifier.weight(2.5f))
                        TableHeaderCell("VENCIMENTO", Modifier.weight(1f))
                        TableHeaderCell("ATRASO", Modifier.weight(0.8f), TextAlign.Center)
                        TableHeaderCell("STATUS", Modifier.weight(0.8f), TextAlign.Center)
                        TableHeaderCell("VALOR", Modifier.weight(1f), TextAlign.End)
                    }
                    HorizontalDivider(color = WsBorder)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        uiState.groups.forEach { group ->
                            item {
                                GroupHeaderRow(group)
                            }
                            items(group.items) { tx ->
                                ReceivableRow(tx)
                                HorizontalDivider(color = WsBorder.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgingTile(
    label: String,
    count: Int,
    total: Money,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
            .background(WsSurface)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = WsTextDisabled, letterSpacing = 0.8.sp)
            Text(
                MoneyFormatter.format(total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (count > 0) accentColor else WsTextSecondary
            )
            Text("$count item(s)", style = MaterialTheme.typography.bodySmall, color = WsTextSecondary)
        }
    }
}

@Composable
private fun GroupHeaderRow(group: ReceivablesGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WsElevated.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            group.clientName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = WsTextPrimary
        )
        Text(
            "${group.items.size} item(s) · ${MoneyFormatter.format(group.total)}",
            style = MaterialTheme.typography.labelSmall,
            color = WsTextSecondary
        )
    }
}

@Composable
private fun ReceivableRow(tx: Transaction) {
    val today = LocalDate.now()
    val dueDate = tx.dueDate.toLocalDate()
    val daysOverdue = ChronoUnit.DAYS.between(dueDate, today).toInt().coerceAtLeast(0)
    val isOverdue = tx.status == TransactionStatus.OVERDUE

    val rowBg = if (isOverdue) WsDanger.copy(alpha = 0.06f) else Color.Transparent
    val dueDateColor = if (isOverdue) WsDanger else WsTextPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(2.5f)) {
            Text(tx.description, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            dueDate.format(dateFmt),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = dueDateColor
        )
        Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
            if (daysOverdue > 0) {
                Text(
                    "${daysOverdue}d",
                    style = MaterialTheme.typography.labelMedium,
                    color = WsDanger,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text("—", style = MaterialTheme.typography.bodySmall, color = WsTextDisabled)
            }
        }
        Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
            StatusBadge(tx.status)
        }
        Text(
            MoneyFormatter.format(tx.amount),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            color = WsSuccess
        )
    }
}

@Composable
private fun StatusBadge(status: TransactionStatus) {
    val (bg, fg) = when (status) {
        TransactionStatus.OVERDUE -> WsDanger.copy(alpha = 0.15f) to WsDanger
        TransactionStatus.PARTIAL -> WsWarning.copy(alpha = 0.15f) to WsWarning
        else -> WsTextDisabled.copy(alpha = 0.15f) to WsTextSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(status.displayName, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}
