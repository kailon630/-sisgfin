package br.com.sisgfin.contracts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.*
import br.com.sisgfin.core.ui.panel.BaseCrudPanel
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.money.toMoney
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun ContractsScreen(
    viewModel: ContractViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Contratos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${uiState.contracts.size} contrato(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = WsTextSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WsOutlinedButton(
                    onClick = {
                        viewModel.openNew(TransactionType.EXPENSE)
                        onShowRightPanel { ContractDetailsPanel(viewModel, onCloseRightPanel) }
                    },
                    contentColor = WsDanger
                ) {
                    Text("+ Despesa")
                }
                WsButton(
                    text = "+ Receita",
                    onClick = {
                        viewModel.openNew(TransactionType.INCOME)
                        onShowRightPanel { ContractDetailsPanel(viewModel, onCloseRightPanel) }
                    }
                )
            }
        }

        // Filtro por status
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WsFilterChip(
                selected = uiState.statusFilter == null,
                onClick  = { viewModel.applyStatusFilter(null) },
                label    = { Text("Todos", style = MaterialTheme.typography.labelMedium) }
            )
            ContractStatus.values().forEach { s ->
                WsFilterChip(
                    selected = uiState.statusFilter == s,
                    onClick  = { viewModel.applyStatusFilter(s) },
                    label    = { Text(s.displayName, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        // Mensagens de feedback
        uiState.successMessage?.let {
            Text(it, color = WsSuccess, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        }
        uiState.errorMessage?.let {
            Text(it, color = WsDanger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (uiState.isLoading) {
            WsLoaderFullscreen()
            return@Column
        }

        // Cabeçalho da tabela
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WsElevated, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TableHeader("Nº / Objeto",  Modifier.weight(2.5f))
            TableHeader("Contratado",   Modifier.weight(2f))
            TableHeader("Valor Total",  Modifier.weight(1.5f))
            TableHeader("Status",       Modifier.weight(1f))
            TableHeader("Vigência",     Modifier.weight(1.5f))
        }

        if (uiState.contracts.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhum contrato encontrado.", color = WsTextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, WsBorder, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            ) {
                items(uiState.contracts, key = { it.id }) { contract ->
                    ContractRow(
                        contract  = contract,
                        suppliers = viewModel.suppliers.collectAsState().value,
                        onClick   = {
                            viewModel.select(contract)
                            onShowRightPanel { ContractDetailsPanel(viewModel, onCloseRightPanel) }
                        }
                    )
                    HorizontalDivider(color = WsBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun ContractRow(
    contract:  Contract,
    suppliers: List<br.com.sisgfin.Supplier>,
    onClick:   () -> Unit
) {
    val contractorName = suppliers.find { it.id == contract.contractorId }?.name ?: "—"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WsSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(2.5f)) {
            Text(contract.number, style = MaterialTheme.typography.bodySmall, color = WsTextSecondary)
            Text(contract.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Text(contractorName, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text(MoneyFormatter.format(contract.totalValue), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium)
        ContractStatusBadge(contract.status, modifier = Modifier.weight(1f))
        Column(modifier = Modifier.weight(1.5f)) {
            Text(contract.startDate.format(dateFmt), style = MaterialTheme.typography.bodySmall)
            contract.endDate?.let {
                Text(it.format(dateFmt), style = MaterialTheme.typography.bodySmall, color = WsTextSecondary)
            }
        }
    }
}

@Composable
fun ContractDetailsPanel(
    viewModel: ContractViewModel,
    onClose: () -> Unit
) {
    val uiState   by viewModel.uiState.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val contract  = uiState.selectedContract ?: return
    val execution = uiState.execution
    val isNew     = contract.id == 0

    var number       by remember(contract.id) { mutableStateOf(contract.number) }
    var description  by remember(contract.id) { mutableStateOf(contract.description) }
    var contractorId by remember(contract.id) { mutableStateOf(contract.contractorId) }
    var type         by remember(contract.id) { mutableStateOf(contract.type) }
    var totalValue   by remember(contract.id) { mutableStateOf(contract.totalValue.toString()) }
    var startDate    by remember(contract.id) { mutableStateOf(contract.startDate.format(dateFmt)) }
    var endDate      by remember(contract.id) { mutableStateOf(contract.endDate?.format(dateFmt) ?: "") }
    var notes        by remember(contract.id) { mutableStateOf(contract.notes ?: "") }
    var showStatusDialog by remember { mutableStateOf(false) }
    var targetStatus     by remember { mutableStateOf(ContractStatus.ENCERRADO) }

    val canEdit = isNew || contract.status == ContractStatus.VIGENTE || contract.status == ContractStatus.SUSPENSO

    BaseCrudPanel(
        title     = if (isNew) "Novo Contrato" else "CT ${contract.number}",
        subtitle  = if (isNew) "Preencha os dados do contrato" else contract.description,
        onClose   = {
            viewModel.clearSelection()
            onClose()
        },
        isLoading    = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        onSave = if (canEdit) {
            {
                viewModel.save(
                    contract.copy(
                        number       = number,
                        description  = description,
                        contractorId = contractorId,
                        type         = type,
                        totalValue   = totalValue.toMoney(),
                        startDate    = parseDate(startDate),
                        endDate      = endDate.takeIf { it.isNotBlank() }?.let { parseDate(it) },
                        notes        = notes.ifBlank { null }
                    )
                )
                onClose()
            }
        } else null,
        showFooter = canEdit
    ) {
        // Badge de status e tipo
        if (!isNew) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContractStatusBadge(contract.status)
                Text(
                    if (contract.type == TransactionType.EXPENSE) "DESPESA" else "RECEITA",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (contract.type == TransactionType.EXPENSE) WsDanger else WsSuccess
                )
            }
        }

        // Execução financeira
        if (!isNew && execution != null) {
            DetailSection("Execução Financeira") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExecutionTile("Total",     execution.totalValue,  Modifier.weight(1f))
                    ExecutionTile("Consumido", execution.consumed,    Modifier.weight(1f))
                    ExecutionTile("Saldo",     execution.remaining,   Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                val progressColor = when {
                    execution.percentUsed >= 100.0 -> WsDanger
                    execution.percentUsed >= 80.0  -> WsWarning
                    else                            -> WsSuccess
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Consumo", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
                        Text("${"%.1f".format(execution.percentUsed)}%", style = MaterialTheme.typography.labelMedium, color = progressColor)
                    }
                    LinearProgressIndicator(
                        progress = { (execution.percentUsed / 100.0).coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color    = progressColor,
                        trackColor = WsBorder
                    )
                }
            }
        }

        // Dados gerais
        DetailSection("Dados Gerais") {
            if (isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WsFilterChip(
                        selected = type == TransactionType.EXPENSE,
                        onClick  = { type = TransactionType.EXPENSE },
                        label    = { Text("Despesa", style = MaterialTheme.typography.labelMedium) }
                    )
                    WsFilterChip(
                        selected = type == TransactionType.INCOME,
                        onClick  = { type = TransactionType.INCOME },
                        label    = { Text("Receita", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            WsTextField("NÚMERO  (ex: CT-001/2025)", number, enabled = canEdit) { number = it }
            WsTextField("OBJETO / DESCRIÇÃO", description, enabled = canEdit) { description = it }
            WsSelectField(
                label      = "CONTRATADO",
                options    = suppliers.map { it.id to it.name },
                selectedId = contractorId.takeIf { it > 0 },
                onSelect   = { contractorId = it ?: 0 },
                enabled    = canEdit
            )
            WsTextField("VALOR TOTAL (R$)", totalValue, enabled = canEdit) { totalValue = it }
        }

        // Vigência
        DetailSection("Vigência") {
            WsTextField("DATA DE INÍCIO  (dd/MM/yyyy)", startDate, enabled = canEdit) { startDate = it }
            WsTextField("DATA DE ENCERRAMENTO  (opcional, dd/MM/yyyy)", endDate, enabled = canEdit) { endDate = it }
        }

        // Observações
        if (canEdit || notes.isNotBlank()) {
            DetailSection("Observações") {
                WsTextField("NOTAS", notes, enabled = canEdit) { notes = it }
            }
        }

        // Controle de status
        if (!isNew) {
            DetailSection("Controle de Status") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (contract.status) {
                        ContractStatus.VIGENTE -> {
                            WsOutlinedButton(
                                onClick      = {
                                    viewModel.updateStatus(contract.id, ContractStatus.SUSPENSO)
                                    onClose()
                                },
                                contentColor = WsWarning
                            ) { Text("Suspender") }
                            WsOutlinedButton(
                                onClick      = {
                                    targetStatus = ContractStatus.ENCERRADO
                                    showStatusDialog = true
                                },
                                contentColor = WsDanger
                            ) { Text("Encerrar") }
                        }
                        ContractStatus.SUSPENSO -> {
                            WsButton("Reativar", onClick = {
                                viewModel.updateStatus(contract.id, ContractStatus.VIGENTE)
                                onClose()
                            })
                            WsOutlinedButton(
                                onClick      = {
                                    targetStatus = ContractStatus.CANCELADO
                                    showStatusDialog = true
                                },
                                contentColor = WsDanger
                            ) { Text("Cancelar Contrato") }
                        }
                        else -> {
                            Text(
                                "Contrato ${contract.status.displayName.lowercase()}. Nenhuma ação disponível.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WsTextSecondary
                            )
                        }
                    }
                }
            }

            // Últimas transações vinculadas
            if (uiState.recentTransactions.isNotEmpty()) {
                DetailSection("Últimos Lançamentos") {
                    uiState.recentTransactions.forEach { tx ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.description, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text(tx.dueDate.format(dateFmt), style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
                            }
                            Text(
                                MoneyFormatter.format(tx.amount),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (tx.type == TransactionType.EXPENSE) WsDanger else WsSuccess
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text("Confirmar ${targetStatus.displayName}") },
            text  = { Text("Deseja marcar este contrato como '${targetStatus.displayName}'? Novos lançamentos não poderão ser vinculados.") },
            confirmButton = {
                TextButton(onClick = {
                    showStatusDialog = false
                    viewModel.updateStatus(contract.id, targetStatus)
                    onClose()
                }) { Text("Confirmar", color = WsDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showStatusDialog = false }) { Text("Cancelar") }
            },
            containerColor = WsSurface
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

@Composable
fun ContractStatusBadge(status: ContractStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(status.color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            status.displayName,
            style      = MaterialTheme.typography.labelSmall,
            color      = status.color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ExecutionTile(label: String, value: Money, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(WsElevated, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
        Text(MoneyFormatter.format(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style    = MaterialTheme.typography.labelSmall,
        color    = WsTextDisabled,
        letterSpacing = 0.5.sp
    )
}

private val panelDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun parseDate(s: String): LocalDateTime = runCatching {
    java.time.LocalDate.parse(s, panelDateFmt).atStartOfDay()
}.getOrDefault(LocalDateTime.now())
