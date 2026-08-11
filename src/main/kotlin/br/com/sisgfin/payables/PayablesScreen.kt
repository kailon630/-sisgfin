package br.com.sisgfin.payables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import br.com.sisgfin.*
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.SummaryTile
import br.com.sisgfin.financial.transactions.SummaryTileRow
import br.com.sisgfin.financial.transactions.SummaryTileTone
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionContextMenu
import br.com.sisgfin.financial.transactions.TransactionDetailsPanel
import br.com.sisgfin.financial.transactions.TransactionListView
import br.com.sisgfin.financial.transactions.TransactionQuickPopup
import br.com.sisgfin.financial.transactions.TransactionsViewModel
import br.com.sisgfin.financial.transactions.TotalsFooter

@Composable
fun PayablesScreen(
    viewModel: PayablesViewModel,
    transactionsViewModel: TransactionsViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val txUiState by transactionsViewModel.uiState.collectAsState()
    var contextMenuTx by remember { mutableStateOf<Transaction?>(null) }
    var contextMenuExpanded by remember { mutableStateOf(false) }
    val canPay = viewModel.canConfirmPayment()

    LaunchedEffect(Unit) { transactionsViewModel.loadReferenceData() }

    fun openPanel(tx: Transaction) {
        viewModel.selectTransaction(tx)
        transactionsViewModel.selectTransaction(tx)
        onShowRightPanel {
            TransactionDetailsPanel(
                viewModel = transactionsViewModel,
                onClose = {
                    onCloseRightPanel()
                    viewModel.load()
                },
                onOpenQuickEdit = { transactionsViewModel.openDialog(tx) }
            )
        }
    }

    fun openNewExpensePanel() {
        transactionsViewModel.openNewExpense()
        onShowRightPanel {
            TransactionDetailsPanel(
                viewModel = transactionsViewModel,
                onClose = { onCloseRightPanel(); viewModel.load() },
                onOpenQuickEdit = null
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Contas a Pagar", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Despesas pendentes de pagamento",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WsButton(
                    text    = "Nova Despesa",
                    icon    = Icons.Default.Add,
                    variant = WsButtonVariant.DANGER,
                    onClick = { openNewExpensePanel() }
                )
                WsIconButton(Icons.Default.Refresh, onClick = { viewModel.load() })
            }
        }

        // ── Tiles ────────────────────────────────────────────────────────────
        val s = uiState.summary
        val tileFilter = uiState.tileFilter
        SummaryTileRow(
            tiles = listOf(
                SummaryTile(
                    label = "VENCIDOS",
                    amount = s.overdue,
                    count = s.overdueCount,
                    tone = SummaryTileTone.DANGER,
                    isSelected = tileFilter == PayablesTileFilter.OVERDUE,
                    onClick = { viewModel.selectTile(PayablesTileFilter.OVERDUE) }
                ),
                SummaryTile(
                    label = "VENCE HOJE",
                    amount = s.dueToday,
                    count = s.dueTodayCount,
                    tone = SummaryTileTone.WARNING,
                    isSelected = tileFilter == PayablesTileFilter.TODAY,
                    onClick = { viewModel.selectTile(PayablesTileFilter.TODAY) }
                ),
                SummaryTile(
                    label = "ESTA SEMANA",
                    amount = s.thisWeek,
                    count = s.thisWeekCount,
                    tone = SummaryTileTone.NEUTRAL,
                    isSelected = tileFilter == PayablesTileFilter.THIS_WEEK,
                    onClick = { viewModel.selectTile(PayablesTileFilter.THIS_WEEK) }
                ),
                SummaryTile(
                    label = "TOTAL EM ABERTO",
                    amount = s.total,
                    count = s.totalCount,
                    tone = SummaryTileTone.NEUTRAL,
                    isSelected = tileFilter == PayablesTileFilter.ALL,
                    onClick = { viewModel.selectTile(PayablesTileFilter.ALL) }
                )
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ── Erro de operação ─────────────────────────────────────────────────
        uiState.errorMessage?.let { err ->
            Text(err, color = WsDanger, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp))
        }

        // ── Table ────────────────────────────────────────────────────────────
        val displayed = uiState.items
        val displayedTotal = remember(displayed) {
            displayed.fold(Money.ZERO) { acc, tx -> acc + tx.amount }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
                .background(WsSurface)
        ) {
            if (displayed.isEmpty() && !uiState.isLoading) {
                EmptyState(
                    if (tileFilter == PayablesTileFilter.ALL)
                        "Nenhuma despesa em aberto."
                    else
                        "Nenhuma despesa neste período."
                )
            } else {
                TransactionListView(
                    items            = displayed,
                    selectedId       = uiState.selectedItem?.id,
                    grouped          = true,
                    counterparties   = uiState.counterparties,
                    onRowClick       = { openPanel(it) },
                    onRowDoubleClick = { openPanel(it) },
                    onContextMenu    = {
                        contextMenuTx = it
                        contextMenuExpanded = true
                        viewModel.selectTransaction(it)
                    }
                )
            }
        }

        TotalsFooter(displayed = displayedTotal, count = displayed.size)
    }

    // ── Context menu ─────────────────────────────────────────────────────────
    TransactionContextMenu(
        expanded    = contextMenuExpanded,
        transaction = contextMenuTx,
        canPay      = canPay,
        onDismiss   = { contextMenuExpanded = false },
        onEdit      = {
            contextMenuTx?.let { transactionsViewModel.openDialog(it) }
            contextMenuExpanded = false
        },
        onPay = {
            contextMenuTx?.let { viewModel.markAsPaidFull(it.id) }
            contextMenuExpanded = false
        },
        onCancel = {
            contextMenuTx?.let { viewModel.cancelTransaction(it.id) }
            contextMenuExpanded = false
        },
        onDuplicate = {
            contextMenuTx?.let { viewModel.duplicateTransaction(it.id) }
            contextMenuExpanded = false
        },
        onDetails = {
            contextMenuTx?.let { openPanel(it) }
            contextMenuExpanded = false
        }
    )

    // ── Quick edit popup (driven by transactionsViewModel) ───────────────────
    if (txUiState.isDialogVisible) {
        TransactionQuickPopup(
            item = txUiState.selectedItem,
            onSave = { transactionsViewModel.save(it); viewModel.load() },
            onCancel = { transactionsViewModel.closeDialog() }
        )
    }
}
