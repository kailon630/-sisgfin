package br.com.sisgfin.recurrence

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.*
import br.com.sisgfin.core.ui.panel.BaseCrudPanel
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun RecurringScreen(
    viewModel: RecurringViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Feedback via snackbar
    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Recorrências", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Despesas e receitas geradas automaticamente em periodicidade fixa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WsOutlinedButton(
                    onClick = {
                        viewModel.openNew(TransactionType.EXPENSE)
                        onShowRightPanel {
                            RecurringDetailsPanel(viewModel, onCloseRightPanel)
                        }
                    },
                    contentColor = WsDanger
                ) {
                    Icon(Icons.Default.RemoveCircleOutline, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+ Despesa Recorrente")
                }
                WsButton(
                    text = "+ Receita Recorrente",
                    onClick = {
                        viewModel.openNew(TransactionType.INCOME)
                        onShowRightPanel {
                            RecurringDetailsPanel(viewModel, onCloseRightPanel)
                        }
                    }
                )
                WsIconButton(Icons.Default.Refresh, onClick = { viewModel.load() })
            }
        }

        // Mensagem de sucesso/erro
        uiState.successMessage?.let { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(WsSuccess.copy(alpha = 0.1f))
                    .border(1.dp, WsSuccess.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = WsSuccess, modifier = Modifier.size(16.dp))
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = WsSuccess)
            }
        }
        uiState.errorMessage?.let { err ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(WsDanger.copy(alpha = 0.1f))
                    .border(1.dp, WsDanger.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Error, null, tint = WsDanger, modifier = Modifier.size(16.dp))
                Text(err, style = MaterialTheme.typography.bodyMedium, color = WsDanger)
            }
        }

        // Tabela
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
                .background(WsSurface)
        ) {
            if (uiState.templates.isEmpty() && !uiState.isLoading) {
                EmptyState("Nenhuma recorrência cadastrada.\nUse os botões acima para criar despesas ou receitas automáticas.")
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().background(WsElevated).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableHeaderCell("DESCRIÇÃO",   Modifier.weight(2.5f))
                        TableHeaderCell("TIPO",        Modifier.weight(0.8f), TextAlign.Center)
                        TableHeaderCell("INTERVALO",   Modifier.weight(1f))
                        TableHeaderCell("VALOR",       Modifier.weight(1f), TextAlign.End)
                        TableHeaderCell("DIA",         Modifier.weight(0.5f), TextAlign.Center)
                        TableHeaderCell("INÍCIO",      Modifier.weight(0.9f), TextAlign.Center)
                        TableHeaderCell("STATUS",      Modifier.weight(0.7f), TextAlign.Center)
                    }
                    HorizontalDivider(color = WsBorder)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.templates) { tmpl ->
                            RecurringRow(
                                template   = tmpl,
                                isSelected = uiState.selectedTemplate?.id == tmpl.id,
                                onClick = {
                                    viewModel.select(tmpl)
                                    onShowRightPanel {
                                        RecurringDetailsPanel(viewModel, onCloseRightPanel)
                                    }
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
private fun RecurringRow(
    template: RecurrenceTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) WsAccent.copy(alpha = 0.1f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Descrição
        Column(modifier = Modifier.weight(2.5f)) {
            Text(template.description, style = MaterialTheme.typography.bodyLarge)
        }

        // Tipo badge
        Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
            val (label, color) = if (template.type == TransactionType.INCOME)
                "Receita" to WsSuccess else "Despesa" to WsDanger
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }

        Text(
            template.interval.displayName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = WsTextSecondary
        )
        Text(
            MoneyFormatter.format(template.amount),
            modifier = Modifier.weight(1f),
            style = WsMoneyStyle,
            textAlign = TextAlign.End
        )
        Text(
            "dia ${template.dayOfMonth}",
            modifier = Modifier.weight(0.5f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Text(
            template.startsAt.format(dateFmt),
            modifier = Modifier.weight(0.9f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        // Status badge
        Box(modifier = Modifier.weight(0.7f), contentAlignment = Alignment.Center) {
            val (label, color) = if (template.isActive)
                "Ativa" to WsSuccess else "Pausada" to WsTextDisabled
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

@Composable
fun RecurringDetailsPanel(viewModel: RecurringViewModel, onClose: () -> Unit) {
    val uiState   by viewModel.uiState.collectAsState()
    val accounts  by viewModel.accounts.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val template  = uiState.selectedTemplate ?: return
    val isNew     = template.id == 0

    var description  by remember(template.id) { mutableStateOf(template.description) }
    var amountStr    by remember(template.id) { mutableStateOf(template.amount.value.toPlainString()) }
    var type         by remember(template.id) { mutableStateOf(template.type) }
    var interval     by remember(template.id) { mutableStateOf(template.interval) }
    var dayOfMonth   by remember(template.id) { mutableStateOf(template.dayOfMonth.toString()) }
    var accountId    by remember(template.id) { mutableStateOf(template.accountId) }
    var supplierId   by remember(template.id) { mutableStateOf(template.supplierId) }
    var categoryId   by remember(template.id) { mutableStateOf(template.categoryId) }
    var documentType by remember(template.id) { mutableStateOf(template.documentType ?: "") }
    var notes        by remember(template.id) { mutableStateOf(template.notes ?: "") }
    var startsAt     by remember(template.id) { mutableStateOf(template.startsAt.toLocalDate().format(dateFmt)) }
    var endsAt       by remember(template.id) { mutableStateOf(template.endsAt?.toLocalDate()?.format(dateFmt) ?: "") }

    var showCancelFutureDialog by remember { mutableStateOf(false) }

    LaunchedEffect(accounts) {
        if (accountId == 0 && accounts.isNotEmpty()) accountId = accounts.first().id
    }

    BaseCrudPanel(
        title = if (isNew) "Nova Recorrência" else template.description,
        subtitle = if (isNew) "Configure a regra de geração automática" else "Editar template de recorrência",
        onClose = onClose,
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        onSave = {
            val day  = dayOfMonth.toIntOrNull()?.coerceIn(1, 31) ?: template.dayOfMonth
            val amt  = amountStr.replace(",", ".").toBigDecimalOrNull()
                ?: template.amount.value
            viewModel.save(
                template.copy(
                    description  = description,
                    amount       = br.com.sisgfin.financial.money.Money(amt),
                    type         = type,
                    interval     = interval,
                    dayOfMonth   = day,
                    accountId    = accountId.takeIf { it > 0 } ?: template.accountId,
                    supplierId   = supplierId,
                    categoryId   = categoryId,
                    documentType = documentType.ifBlank { null },
                    notes        = notes.ifBlank { null },
                    startsAt     = parseDate(startsAt).atStartOfDay(),
                    endsAt       = endsAt.ifBlank { null }?.let { parseDate(it).atStartOfDay() }
                )
            )
        }
    ) {
        DetailSection("Dados gerais") {
            WsTextField("DESCRIÇÃO", description) { description = it }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WsTextField("VALOR (R$)", amountStr, modifier = Modifier.weight(1f)) { amountStr = it }
                WsTextField("DIA DO MÊS", dayOfMonth, modifier = Modifier.weight(1f)) { dayOfMonth = it }
            }

            // Tipo
            Text("TIPO", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { t ->
                    WsFilterChip(
                        selected = type == t,
                        onClick  = { type = t },
                        label    = { Text(t.displayName) }
                    )
                }
            }

            // Intervalo
            Text("INTERVALO", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RecurrenceInterval.values().forEach { iv ->
                    WsFilterChip(
                        selected = interval == iv,
                        onClick  = { interval = iv },
                        label    = { Text(iv.displayName, fontSize = 11.sp) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WsTextField("INÍCIO (DD/MM/AAAA)", startsAt, modifier = Modifier.weight(1f)) { startsAt = it }
                WsTextField("ENCERRAMENTO (opcional)", endsAt, modifier = Modifier.weight(1f)) { endsAt = it }
            }
        }

        DetailSection("Vínculos") {
            if (accounts.isNotEmpty()) {
                val accountOptions = accounts.map { it.id to it.name }
                WsSelectField(
                    label      = "CONTA",
                    options    = accountOptions,
                    selectedId = accountId.takeIf { it > 0 },
                    onSelect   = { accountId = it ?: 0 },
                    nullable   = false
                )
            }
            if (suppliers.isNotEmpty()) {
                val supplierOptions = suppliers.map { it.id to it.name }
                WsSelectField(
                    label      = "FORNECEDOR",
                    options    = supplierOptions,
                    selectedId = supplierId,
                    onSelect   = { supplierId = it }
                )
            }
            if (categories.isNotEmpty()) {
                val categoryOptions = categories.map { it.id to it.name }
                WsSelectField(
                    label      = "CATEGORIA",
                    options    = categoryOptions,
                    selectedId = categoryId,
                    onSelect   = { categoryId = it }
                )
            }
            WsTextField("TIPO DE DOCUMENTO (ex: NF)", documentType) { documentType = it }
            WsTextField("OBSERVAÇÕES", notes) { notes = it }
        }

        // Ações de controle (apenas templates existentes)
        if (!isNew) {
            DetailSection("Controle") {
                if (template.isActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WsOutlinedButton(
                            onClick = { viewModel.pause(template.id) },
                            contentColor = WsWarning
                        ) {
                            Icon(Icons.Default.Pause, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pausar")
                        }
                        WsOutlinedButton(
                            onClick = { showCancelFutureDialog = true },
                            contentColor = WsDanger
                        ) {
                            Icon(Icons.Default.CancelScheduleSend, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cancelar Futuras")
                        }
                    }
                    Text(
                        "Pausar mantém os lançamentos já gerados. Cancelar Futuras também cancela os PENDENTES.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WsTextSecondary
                    )
                } else {
                    WsButton("Reativar Recorrência", icon = Icons.Default.PlayArrow, onClick = {
                        viewModel.resume(template.id)
                    })
                    Text(
                        "Reativar gera automaticamente os próximos lançamentos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WsTextSecondary
                    )
                }
            }

            // Histórico de lançamentos gerados
            if (uiState.history.isNotEmpty()) {
                DetailSection("Histórico gerado (${uiState.history.size})") {
                    uiState.history.take(15).forEach { tx ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tx.dueDate.format(dateFmt),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                MoneyFormatter.format(tx.amount),
                                style = WsMoneyStyle
                            )
                            Spacer(Modifier.width(8.dp))
                            val (statusLabel, statusColor) = when (tx.status) {
                                TransactionStatus.PAID     -> "Pago" to WsSuccess
                                TransactionStatus.OVERDUE  -> "Vencido" to WsDanger
                                TransactionStatus.CANCELED -> "Cancelado" to WsTextDisabled
                                else                       -> "Pendente" to WsWarning
                            }
                            Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor)
                        }
                    }
                    if (uiState.history.size > 15) {
                        Text(
                            "+ ${uiState.history.size - 15} lançamentos anteriores",
                            style = MaterialTheme.typography.labelSmall,
                            color = WsTextSecondary
                        )
                    }
                }
            }
        }
    }

    // Confirmar cancelamento de futuras
    if (showCancelFutureDialog) {
        AlertDialog(
            onDismissRequest = { showCancelFutureDialog = false },
            containerColor   = WsSurface,
            shape            = RoundedCornerShape(8.dp),
            title            = { Text("Cancelar lançamentos futuros") },
            text             = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Esta ação irá pausar a recorrência \"${template.description}\" e cancelar todos os lançamentos PENDENTES gerados por ela a partir de hoje.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Lançamentos já PAGOS não serão afetados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WsTextSecondary
                    )
                }
            },
            confirmButton = {
                WsButton("Confirmar", onClick = {
                    viewModel.cancelFuture(template.id)
                    showCancelFutureDialog = false
                    onClose()
                })
            },
            dismissButton = {
                TextButton(onClick = { showCancelFutureDialog = false }) { Text("Voltar") }
            }
        )
    }
}

private fun parseDate(value: String): LocalDate =
    try {
        LocalDate.parse(value.trim(), dateFmt)
    } catch (_: Exception) {
        LocalDate.now()
    }
