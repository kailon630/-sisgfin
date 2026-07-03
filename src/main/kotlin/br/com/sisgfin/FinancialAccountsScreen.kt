package br.com.sisgfin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.WsFilterChip
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.money.toMoney
import br.com.sisgfin.accounts.FinancialAccountViewModel
import br.com.sisgfin.core.ui.notifications.CrudEventEffects
import br.com.sisgfin.core.ui.panel.BaseCrudPanel
import java.util.*

@Composable
fun FinancialAccountsScreen(
    viewModel: FinancialAccountViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    CrudEventEffects(viewModel)

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        CrudToolbar(
            title = "Contas e Caixas",
            subtitle = "Gerenciamento de contas bancárias e disponibilidades financeiras",
            searchQuery = "",
            onSearchQueryChange = {},
            newItemLabel = "Nova Conta",
            onNewItemClick = {
                viewModel.openNew()
                onShowRightPanel { FinancialAccountDetailsPanel(viewModel, onCloseRightPanel) }
            },
            onRefreshClick = { viewModel.load() }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
                .background(WsSurface)
        ) {
            if (uiState.items.isEmpty() && !uiState.isLoading) {
                EmptyState("Nenhuma conta financeira cadastrada.")
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(WsElevated).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableHeaderCell("IDENTIFICAÇÃO DA CONTA", Modifier.weight(2f))
                        TableHeaderCell("BANCO / TIPO", Modifier.weight(1.5f))
                        TableHeaderCell("AGÊNCIA / CONTA", Modifier.weight(1.5f))
                        TableHeaderCell("SALDO INICIAL", Modifier.weight(1f), TextAlign.End)
                        TableHeaderCell("STATUS", Modifier.weight(0.8f), TextAlign.Center)
                    }
                    HorizontalDivider(color = WsBorder)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.items) { item ->
                            FinancialAccountRow(
                                item = item,
                                isSelected = uiState.selectedItem?.id == item.id,
                                onSingleClick = {
                                    viewModel.select(item)
                                    onShowRightPanel { FinancialAccountDetailsPanel(viewModel, onCloseRightPanel) }
                                },
                                onDoubleClick = { viewModel.openDialog(item) }
                            )
                            HorizontalDivider(color = WsBorder.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        if (uiState.isDialogVisible) {
            FinancialAccountPopup(
                item = uiState.selectedItem,
                onSave = { viewModel.save(it) },
                onCancel = { viewModel.closeDialog() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FinancialAccountRow(item: FinancialAccount, isSelected: Boolean, onSingleClick: () -> Unit, onDoubleClick: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val bg = when {
        isSelected -> WsAccent.copy(alpha = 0.1f)
        isHovered -> WsElevated
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier.fillMaxWidth().background(bg).combinedClickable(
            onClick = onSingleClick,
            onDoubleClick = onDoubleClick
        ).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.name, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyLarge)
        
        Column(modifier = Modifier.weight(1.5f)) {
            Text(item.bankName ?: item.investmentBroker ?: "Interno", style = MaterialTheme.typography.bodyMedium)
            Text(item.accountType.displayName, style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
        }

        Text("${item.agency ?: "-"} / ${item.accountNumber ?: "-"}", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium)
        
        Text(
            text = MoneyFormatter.format(item.initialBalance),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End
        )
        
        Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
            val statusColor = if (item.isActive) WsSuccess else WsTextDisabled
            Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun FinancialAccountDetailsPanel(viewModel: FinancialAccountViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.selectedItem ?: return

    var name by remember(item.id) { mutableStateOf(item.name) }
    var bankName by remember(item.id) { mutableStateOf(item.bankName ?: "") }
    var agency by remember(item.id) { mutableStateOf(item.agency ?: "") }
    var accountNumber by remember(item.id) { mutableStateOf(item.accountNumber ?: "") }
    var accountType by remember(item.id) { mutableStateOf(item.accountType) }
    var initialBalance by remember(item.id) { mutableStateOf(item.initialBalance.toString()) }
    var investmentBroker by remember(item.id) { mutableStateOf(item.investmentBroker ?: "") }

    BaseCrudPanel(
        title = "Detalhes da Conta",
        subtitle = "Configurações financeiras",
        onClose = onClose,
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        onSave = {
            viewModel.save(
                item.copy(
                    name = name,
                    bankName = bankName.ifBlank { null },
                    agency = agency.ifBlank { null },
                    accountNumber = accountNumber.ifBlank { null },
                    accountType = accountType,
                    initialBalance = initialBalance.toMoney(),
                    investmentBroker = investmentBroker.ifBlank { null }
                )
            )
        }
    ) {
        DetailSection("Identificação") {
            WsTextField("NOME DA CONTA (EX: ITAU PRINCIPAL)", name) { name = it }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TIPO DE CONTA", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinancialAccountType.values().forEach { type ->
                        WsFilterChip(
                            selected = accountType == type,
                            onClick = { accountType = type },
                            label = { Text(type.displayName) }
                        )
                    }
                }
            }
        }
        if (accountType == FinancialAccountType.INVESTMENT) {
            DetailSection("Aplicação") {
                WsTextField("CORRETORA / FUNDO", investmentBroker) { investmentBroker = it }
            }
        } else {
            DetailSection("Dados Bancários") {
                WsTextField("NOME DO BANCO", bankName) { bankName = it }
                WsTextField("AGÊNCIA", agency) { agency = it }
                WsTextField("NÚMERO DA CONTA", accountNumber) { accountNumber = it }
            }
        }
        DetailSection("Configuração Inicial") {
            WsTextField("SALDO DE ABERTURA (R$)", initialBalance) { initialBalance = it }
        }
    }
}

@Composable
fun FinancialAccountPopup(item: FinancialAccount?, onSave: (FinancialAccount) -> Unit, onCancel: () -> Unit) {
    if (item == null) return
    // Logic similar to DetailsPanel but in AlertDialog
    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(8.dp),
        containerColor = WsSurface,
        title = { Text("Edição de Conta") },
        text = { Text("Utilize o painel lateral para edição completa.") },
        confirmButton = { WsButton("Fechar", onClick = onCancel) }
    )
}
