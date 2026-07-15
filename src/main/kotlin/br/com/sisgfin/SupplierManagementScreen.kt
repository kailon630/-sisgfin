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
import br.com.sisgfin.suppliers.SupplierViewModel
import br.com.sisgfin.core.ui.notifications.CrudEventEffects
import br.com.sisgfin.core.ui.panel.BaseCrudPanel

@Composable
fun SupplierManagementScreen(
    viewModel: SupplierViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    CrudEventEffects(viewModel)

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        CrudToolbar(
            title = "Fornecedores e Credores",
            subtitle = "Gestão de favorecidos financeiros e prestadores de serviço",
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = { viewModel.search(it) },
            newItemLabel = "Novo Fornecedor",
            onNewItemClick = {
                viewModel.openNew()
                onShowRightPanel { SupplierDetailsPanel(viewModel, onCloseRightPanel) }
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
                EmptyState("Nenhum fornecedor cadastrado.")
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(WsElevated).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableHeaderCell("NOME / RAZÃO SOCIAL", Modifier.weight(2.5f))
                        TableHeaderCell("DOCUMENTO", Modifier.weight(1.2f))
                        TableHeaderCell("TELEFONE", Modifier.weight(1f))
                        TableHeaderCell("STATUS", Modifier.weight(0.8f), TextAlign.Center)
                    }
                    HorizontalDivider(color = WsBorder)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.items) { item ->
                            SupplierRow(
                                item = item,
                                isSelected = uiState.selectedItem?.id == item.id,
                                onSingleClick = {
                                    viewModel.select(item)
                                    onShowRightPanel { SupplierDetailsPanel(viewModel, onCloseRightPanel) }
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
            SupplierPopup(
                item = uiState.selectedItem,
                onSave = { viewModel.save(it) },
                onCancel = { viewModel.closeDialog() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SupplierRow(item: Supplier, isSelected: Boolean, onSingleClick: () -> Unit, onDoubleClick: () -> Unit) {
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
        Column(modifier = Modifier.weight(2.5f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            if (!item.tradeName.isNullOrBlank()) {
                Text(item.tradeName, style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
            }
        }
        Text(item.document, modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
        Text(item.phone ?: "-", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        
        Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
            val statusColor = if (item.isActive) WsSuccess else WsTextDisabled
            Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun SupplierDetailsPanel(viewModel: SupplierViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.selectedItem ?: return

    var name by remember(item.id) { mutableStateOf(item.name) }
    var tradeName by remember(item.id) { mutableStateOf(item.tradeName ?: "") }
    var document by remember(item.id) { mutableStateOf(item.document) }
    var email by remember(item.id) { mutableStateOf(item.email ?: "") }
    var phone by remember(item.id) { mutableStateOf(item.phone ?: "") }
    var pixKey by remember(item.id) { mutableStateOf(item.pixKey ?: "") }

    val isDirty = name != item.name || tradeName != (item.tradeName ?: "") ||
        document != item.document || email != (item.email ?: "") ||
        phone != (item.phone ?: "") || pixKey != (item.pixKey ?: "")

    BaseCrudPanel(
        title = "Detalhes do Fornecedor",
        subtitle = "Informações operacionais",
        onClose = onClose,
        isLoading = uiState.isLoading,
        isDirty = isDirty,
        errorMessage = uiState.errorMessage,
        onSave = {
            viewModel.save(
                item.copy(
                    name = name,
                    tradeName = tradeName,
                    document = document,
                    email = email,
                    phone = phone,
                    pixKey = pixKey
                )
            )
        }
    ) {
        DetailSection("Dados Identificatórios") {
            WsTextField("NOME / RAZÃO SOCIAL", name) { name = it }
            WsTextField("NOME FANTASIA", tradeName) { tradeName = it }
            WsDocumentField(
                value = document,
                onValueChange = { document = it },
                stateKey = item.id
            )
        }
        DetailSection("Contato") {
            WsTextField("E-MAIL", email) { email = it }
            WsTextField("TELEFONE", phone) { phone = it }
        }
        DetailSection("Financeiro") {
            WsTextField("CHAVE PIX", pixKey) { pixKey = it }
        }
    }
}

@Composable
fun SupplierPopup(item: Supplier?, onSave: (Supplier) -> Unit, onCancel: () -> Unit) {
    if (item == null) return
    var name by remember { mutableStateOf(item.name) }
    var document by remember { mutableStateOf(item.document) }
    var bank by remember { mutableStateOf(item.bank ?: "") }
    var agency by remember { mutableStateOf(item.agency ?: "") }
    var account by remember { mutableStateOf(item.account ?: "") }

    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(8.dp),
        containerColor = WsSurface,
        modifier = Modifier.width(600.dp).padding(16.dp),
        title = { Text("Edição de Fornecedor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WsTextField("NOME", name) { name = it }
                WsDocumentField(
                    value = document,
                    onValueChange = { document = it },
                    stateKey = item.id
                )
                HorizontalDivider(color = WsBorder)
                Text("Dados Bancários", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
                WsTextField("BANCO", bank) { bank = it }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WsTextField("AGÊNCIA", agency, Modifier.weight(1f)) { agency = it }
                    WsTextField("CONTA", account, Modifier.weight(1f)) { account = it }
                }
            }
        },
        confirmButton = {
            WsButton("Confirmar", onClick = { onSave(item.copy(name = name, document = document, bank = bank, agency = agency, account = account)) })
        },
        dismissButton = { WsButton("Cancelar", variant = WsButtonVariant.TERTIARY, onClick = onCancel) }
    )
}
