package br.com.sisgfin.balances

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.*
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun BalancesScreen(viewModel: BalancePanelViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Painel de Saldos", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Posição financeira em tempo real de todas as contas ativas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextSecondary
                )
            }
            WsIconButton(Icons.Default.Refresh, onClick = { viewModel.load() })
        }

        // Totalizador geral
        if (uiState.summaries.isNotEmpty()) {
            val totalBalance = uiState.summaries.fold(Money.ZERO) { acc, s -> acc + s.currentBalance }
            val totalPending = uiState.summaries.fold(Money.ZERO) { acc, s -> acc + s.pendingTotal }
            val totalOverdue = uiState.summaries.fold(Money.ZERO) { acc, s -> acc + s.overdueTotal }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryTile(
                    label = "Saldo Total",
                    value = MoneyFormatter.format(totalBalance),
                    icon = Icons.Outlined.AccountBalance,
                    valueColor = if (totalBalance.value >= java.math.BigDecimal.ZERO) WsSuccess else WsDanger,
                    modifier = Modifier.weight(1f)
                )
                SummaryTile(
                    label = "A Pagar",
                    value = MoneyFormatter.format(totalPending),
                    icon = Icons.Outlined.PendingActions,
                    valueColor = WsWarning,
                    modifier = Modifier.weight(1f)
                )
                SummaryTile(
                    label = "Vencido",
                    value = MoneyFormatter.format(totalOverdue),
                    icon = Icons.Outlined.ErrorOutline,
                    valueColor = if (totalOverdue.value > java.math.BigDecimal.ZERO) WsDanger else WsTextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Lista de contas
        if (uiState.isLoading) {
            WsLoaderFullscreen()
        } else if (uiState.errorMessage != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.errorMessage!!, color = WsDanger)
            }
        } else if (uiState.summaries.isEmpty()) {
            EmptyState("Nenhuma conta ativa encontrada.")
        } else {
            // Cabeçalho da tabela
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .border(1.dp, WsBorder, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(WsElevated)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableHeaderCell("CONTA", Modifier.weight(2f))
                    TableHeaderCell("TIPO", Modifier.weight(1f))
                    TableHeaderCell("SALDO ATUAL", Modifier.weight(1.2f), TextAlign.End)
                    TableHeaderCell("A PAGAR", Modifier.weight(1f), TextAlign.End)
                    TableHeaderCell("VENCIDO", Modifier.weight(1f), TextAlign.End)
                    TableHeaderCell("ÚLTIMO PAGTO", Modifier.weight(1.2f), TextAlign.Center)
                }
            }
            HorizontalDivider(color = WsBorder)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .border(1.dp, WsBorder, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(WsSurface)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(uiState.summaries) { summary ->
                        AccountBalanceRow(summary)
                        HorizontalDivider(color = WsBorder.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
    icon: ImageVector,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
            .background(WsSurface)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, null, tint = WsTextSecondary, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = valueColor
            )
        }
    }
}

@Composable
private fun AccountBalanceRow(summary: AccountBalanceSummary) {
    val balanceColor = when {
        summary.currentBalance.value > java.math.BigDecimal.ZERO -> WsSuccess
        summary.currentBalance.value < java.math.BigDecimal.ZERO -> WsDanger
        else -> WsTextSecondary
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Nome + banco/corretora
        Column(modifier = Modifier.weight(2f)) {
            Text(
                summary.account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            val subtitle = summary.account.investmentBroker
                ?: summary.account.bankName
                ?: "—"
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
        }

        // Tipo
        Text(
            summary.account.accountType.displayName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = WsTextSecondary
        )

        // Saldo atual
        Text(
            MoneyFormatter.format(summary.currentBalance),
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = balanceColor,
            textAlign = TextAlign.End
        )

        // A pagar (PENDING)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(
                MoneyFormatter.format(summary.pendingTotal),
                style = MaterialTheme.typography.bodyMedium,
                color = if (summary.pendingCount > 0) WsWarning else WsTextSecondary,
                textAlign = TextAlign.End
            )
            if (summary.pendingCount > 0) {
                Text(
                    "${summary.pendingCount} lançamento${if (summary.pendingCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = WsTextDisabled,
                    textAlign = TextAlign.End
                )
            }
        }

        // Vencido (OVERDUE)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(
                MoneyFormatter.format(summary.overdueTotal),
                style = MaterialTheme.typography.bodyMedium,
                color = if (summary.overdueCount > 0) WsDanger else WsTextSecondary,
                textAlign = TextAlign.End
            )
            if (summary.overdueCount > 0) {
                Text(
                    "${summary.overdueCount} lançamento${if (summary.overdueCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = WsTextDisabled,
                    textAlign = TextAlign.End
                )
            }
        }

        // Último pagamento
        Text(
            summary.lastPaymentDate?.format(dateFormatter) ?: "—",
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.labelMedium,
            color = WsTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
