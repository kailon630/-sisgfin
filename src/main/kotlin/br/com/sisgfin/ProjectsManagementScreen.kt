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
import br.com.sisgfin.projects.CostCenterViewModel
import br.com.sisgfin.core.ui.notifications.CrudEventEffects
import br.com.sisgfin.core.ui.panel.BaseCrudPanel
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun CostCentersScreen(
    viewModel: CostCenterViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    CrudEventEffects(viewModel)

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        CrudToolbar(
            title = "Centros de Custo",
            subtitle = "Projetos, convênios e agrupamentos financeiros",
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = { viewModel.search(it) },
            newItemLabel = "Novo Centro de Custo",
            onNewItemClick = {
                viewModel.openNew()
                onShowRightPanel { CostCenterDetailsPanel(viewModel, onCloseRightPanel) }
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
                EmptyState("Nenhum centro de custo cadastrado.")
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(WsElevated).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableHeaderCell("CÓDIGO", Modifier.weight(1f))
                        TableHeaderCell("NOME DO CENTRO DE CUSTO", Modifier.weight(2.5f))
                        TableHeaderCell("VIGÊNCIA", Modifier.weight(1.5f))
                        TableHeaderCell("STATUS", Modifier.weight(0.8f), TextAlign.Center)
                    }
                    HorizontalDivider(color = WsBorder)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.items) { item ->
                            CostCenterRow(
                                item = item,
                                isSelected = uiState.selectedItem?.id == item.id,
                                onSingleClick = {
                                    viewModel.select(item)
                                    onShowRightPanel { CostCenterDetailsPanel(viewModel, onCloseRightPanel) }
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
            CostCenterPopup(
                item = uiState.selectedItem,
                onSave = { viewModel.save(it) },
                onCancel = { viewModel.closeDialog() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CostCenterRow(item: CostCenter, isSelected: Boolean, onSingleClick: () -> Unit, onDoubleClick: () -> Unit) {
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
        Text(item.code, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = WsAccent)
        Text(item.name, modifier = Modifier.weight(2.5f), style = MaterialTheme.typography.bodyLarge)

        val dateRange = if (item.startDate != null) {
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            "${item.startDate.format(formatter)} - ${item.endDate?.format(formatter) ?: "Indet."}"
        } else "Não definida"

        Text(dateRange, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium)

        Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
            val statusColor = if (item.isActive) WsSuccess else WsTextDisabled
            Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun CostCenterDetailsPanel(viewModel: CostCenterViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.selectedItem ?: return

    var code by remember(item.id) { mutableStateOf(item.code) }
    var name by remember(item.id) { mutableStateOf(item.name) }
    var description by remember(item.id) { mutableStateOf(item.description ?: "") }

    BaseCrudPanel(
        title = "Detalhes do Centro de Custo",
        subtitle = "Configurações do centro de custo",
        onClose = onClose,
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        onSave = {
            viewModel.save(item.copy(code = code, name = name, description = description))
        }
    ) {
        DetailSection("Informações Gerais") {
            WsTextField("CÓDIGO", code) { code = it }
            WsTextField("NOME DO CENTRO DE CUSTO", name) { name = it }
        }
        DetailSection("Descrição") {
            WsTextField("DETALHAMENTO", description) { description = it }
        }
        DetailSection("Vigência (Beta)") {
            Text(
                "As datas de início e término serão configuradas na próxima versão do motor financeiro.",
                style = MaterialTheme.typography.bodySmall,
                color = WsTextDisabled
            )
        }
    }
}

@Composable
fun CostCenterPopup(item: CostCenter?, onSave: (CostCenter) -> Unit, onCancel: () -> Unit) {
    if (item == null) return
    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(8.dp),
        containerColor = WsSurface,
        title = { Text("Edição de Centro de Custo") },
        text = { Text("Utilize o painel lateral para edição detalhada.") },
        confirmButton = { WsButton("Fechar", onClick = onCancel) }
    )
}
