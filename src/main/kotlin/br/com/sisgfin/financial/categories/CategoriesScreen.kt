package br.com.sisgfin.financial.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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

@Composable
fun CategoriesScreen(
    viewModel: ExpenseCategoryViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    CrudEventEffects(viewModel)

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        CrudToolbar(
            title = "Plano de Contas",
            subtitle = "Categorias de despesas e receitas",
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = { viewModel.search(it) },
            newItemLabel = "Nova Categoria",
            onNewItemClick = {
                viewModel.openNew()
                onShowRightPanel {
                    CategoryDetailsPanel(viewModel, onCloseRightPanel)
                }
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
                EmptyState("Nenhuma categoria cadastrada.")
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WsElevated)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableHeaderCell("CÓD.",      Modifier.weight(0.7f))
                        TableHeaderCell("GRUPO",     Modifier.weight(1.5f))
                        TableHeaderCell("NOME",      Modifier.weight(2.5f))
                        TableHeaderCell("TIPO",      Modifier.weight(0.8f), TextAlign.Center)
                        TableHeaderCell("STATUS",    Modifier.weight(0.6f), TextAlign.Center)
                    }
                    HorizontalDivider(color = WsBorder)
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.items) { item ->
                            CategoryRow(
                                item = item,
                                isSelected = uiState.selectedItem?.id == item.id,
                                onSingleClick = {
                                    viewModel.select(item)
                                    onShowRightPanel {
                                        CategoryDetailsPanel(viewModel, onCloseRightPanel)
                                    }
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
            CategoryPopup(
                item = uiState.selectedItem,
                onSave = { viewModel.save(it) },
                onCancel = { viewModel.closeDialog() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryRow(
    item: ExpenseCategory,
    isSelected: Boolean,
    onSingleClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    val bg = when {
        isSelected -> WsAccent.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onSingleClick, onDoubleClick = onDoubleClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.code,
            modifier = Modifier.weight(0.7f),
            style = MaterialTheme.typography.bodyMedium,
            color = WsAccent)
        Text(item.groupName ?: "-",
            modifier = Modifier.weight(1.5f),
            style = MaterialTheme.typography.bodyMedium,
            color = WsTextSecondary)
        Text(item.name,
            modifier = Modifier.weight(2.5f),
            style = MaterialTheme.typography.bodyLarge)
        Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
            val (label, color) = if (item.isIncome)
                "Receita" to WsSuccess
            else
                "Despesa" to WsDanger
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
        }
        Box(modifier = Modifier.weight(0.6f), contentAlignment = Alignment.Center) {
            val statusColor = if (item.isActive) WsSuccess else WsTextDisabled
            Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun CategoryDetailsPanel(viewModel: ExpenseCategoryViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.selectedItem ?: return

    var code      by remember(item.id) { mutableStateOf(item.code) }
    var name      by remember(item.id) { mutableStateOf(item.name) }
    var groupCode by remember(item.id) { mutableStateOf(item.groupCode ?: "") }
    var groupName by remember(item.id) { mutableStateOf(item.groupName ?: "") }
    var isIncome  by remember(item.id) { mutableStateOf(item.isIncome) }

    val isDirty = code != item.code || name != item.name ||
        groupCode != (item.groupCode ?: "") ||
        groupName != (item.groupName ?: "") ||
        isIncome != item.isIncome

    BaseCrudPanel(
        title = "Detalhes da Categoria",
        subtitle = item.displayName,
        onClose = onClose,
        isLoading = uiState.isLoading,
        isDirty = isDirty,
        errorMessage = uiState.errorMessage,
        onSave = {
            viewModel.save(
                item.copy(
                    code = code,
                    name = name,
                    groupCode = groupCode.ifBlank { null },
                    groupName = groupName.ifBlank { null },
                    isIncome = isIncome
                )
            )
        }
    ) {
        DetailSection("Identificação") {
            WsTextField("CÓDIGO (ex: 5.13)", code) { code = it }
            WsTextField("NOME DA CATEGORIA", name) { name = it }
        }
        DetailSection("Grupo") {
            WsTextField("CÓDIGO DO GRUPO (ex: 5)", groupCode) { groupCode = it }
            WsTextField("NOME DO GRUPO (ex: Materiais de Consumo)", groupName) { groupName = it }
        }
        DetailSection("Classificação") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked = isIncome,
                    onCheckedChange = { isIncome = it },
                    colors = CheckboxDefaults.colors(checkedColor = WsAccent)
                )
                Text(
                    if (isIncome) "Receita" else "Despesa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextPrimary
                )
            }
        }
    }
}

@Composable
fun CategoryPopup(
    item: ExpenseCategory?,
    onSave: (ExpenseCategory) -> Unit,
    onCancel: () -> Unit
) {
    if (item == null) return
    var code  by remember { mutableStateOf(item.code) }
    var name  by remember { mutableStateOf(item.name) }

    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(8.dp),
        containerColor = WsSurface,
        modifier = Modifier.width(480.dp),
        title = { Text(if (item.id == 0) "Nova Categoria" else "Editar Categoria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WsTextField("CÓDIGO (ex: 5.13)", code) { code = it }
                WsTextField("NOME", name) { name = it }
            }
        },
        confirmButton = {
            WsButton("Confirmar", onClick = {
                onSave(item.copy(code = code, name = name))
            })
        },
        dismissButton = {
            WsButton("Cancelar", variant = WsButtonVariant.TERTIARY, onClick = onCancel)
        }
    )
}
