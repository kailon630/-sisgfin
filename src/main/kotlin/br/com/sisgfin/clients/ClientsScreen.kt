package br.com.sisgfin.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.sisgfin.*
import br.com.sisgfin.core.ui.notifications.CrudEventEffects
import br.com.sisgfin.core.ui.panel.BaseCrudPanel
import br.com.sisgfin.suppliers.EntityType

@Composable
fun ClientsScreen(
    viewModel: ClientsViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    CrudEventEffects(viewModel)

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        CrudToolbar(
            title = "Clientes",
            subtitle = "Contrapartes de receitas e recebimentos",
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = { viewModel.search(it) },
            newItemLabel = "Novo Cliente",
            onNewItemClick = {
                viewModel.openNew()
                onShowRightPanel { ClientDetailsPanel(viewModel, onCloseRightPanel) }
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
                EmptyState("Nenhum cliente cadastrado.")
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(WsElevated).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableHeaderCell("NOME / RAZÃO SOCIAL", Modifier.weight(2.5f))
                        TableHeaderCell("DOCUMENTO", Modifier.weight(1.2f))
                        TableHeaderCell("TIPO", Modifier.weight(1f))
                        TableHeaderCell("TELEFONE", Modifier.weight(1f))
                        TableHeaderCell("STATUS", Modifier.weight(0.8f), TextAlign.Center)
                    }
                    HorizontalDivider(color = WsBorder)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.items) { item ->
                            ClientRow(
                                item = item,
                                isSelected = uiState.selectedItem?.id == item.id,
                                onClick = {
                                    viewModel.select(item)
                                    onShowRightPanel { ClientDetailsPanel(viewModel, onCloseRightPanel) }
                                }
                            )
                            HorizontalDivider(color = WsBorder.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientRow(item: Supplier, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) WsAccent.copy(alpha = 0.1f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(2.5f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            if (!item.tradeName.isNullOrBlank())
                Text(item.tradeName, style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
        }
        Text(item.document, modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
        Text(item.entityType.displayName, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
        Text(item.phone ?: "-", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
            val statusColor = if (item.isActive) WsSuccess else WsTextDisabled
            Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun ClientDetailsPanel(viewModel: ClientsViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.selectedItem ?: return

    var name       by remember(item.id) { mutableStateOf(item.name) }
    var tradeName  by remember(item.id) { mutableStateOf(item.tradeName ?: "") }
    var document   by remember(item.id) { mutableStateOf(item.document) }
    var email      by remember(item.id) { mutableStateOf(item.email ?: "") }
    var phone      by remember(item.id) { mutableStateOf(item.phone ?: "") }
    var pixKey     by remember(item.id) { mutableStateOf(item.pixKey ?: "") }
    var entityType by remember(item.id) { mutableStateOf(item.entityType) }

    val isDirty = name != item.name || tradeName != (item.tradeName ?: "") ||
        document != item.document || email != (item.email ?: "") ||
        phone != (item.phone ?: "") || pixKey != (item.pixKey ?: "") ||
        entityType != item.entityType

    BaseCrudPanel(
        title = if (item.id == 0) "Novo Cliente" else "Detalhes do Cliente",
        subtitle = if (item.id == 0) "Preencha os dados do cliente" else item.name,
        onClose = onClose,
        isLoading = uiState.isLoading,
        isDirty = isDirty,
        errorMessage = uiState.errorMessage,
        onSave = {
            viewModel.save(
                item.copy(
                    name = name,
                    tradeName = tradeName.ifBlank { null },
                    document = document,
                    email = email.ifBlank { null },
                    phone = phone.ifBlank { null },
                    pixKey = pixKey.ifBlank { null },
                    entityType = entityType
                )
            )
        }
    ) {
        DetailSection("Dados Identificatórios") {
            WsTextField("NOME / RAZÃO SOCIAL", name) { name = it }
            WsTextField("NOME FANTASIA", tradeName) { tradeName = it }
            WsTextField("DOCUMENTO (CPF/CNPJ)", document) { document = it }
        }
        DetailSection("Tipo de Parceiro") {
            Text("PAPEL", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(EntityType.CLIENTE, EntityType.AMBOS).forEach { et ->
                    WsFilterChip(
                        selected = entityType == et,
                        onClick = { entityType = et },
                        label = { Text(et.displayName) }
                    )
                }
            }
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
