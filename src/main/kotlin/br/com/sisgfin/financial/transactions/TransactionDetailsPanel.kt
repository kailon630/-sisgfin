package br.com.sisgfin.financial.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import br.com.sisgfin.*
import br.com.sisgfin.suppliers.EntityType
import br.com.sisgfin.budget.BudgetBalance
import br.com.sisgfin.core.ui.panel.BaseCrudPanel
import br.com.sisgfin.contracts.Contract
import br.com.sisgfin.recurrence.RecurrenceInterval
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.money.toMoney
import br.com.sisgfin.financial.transactions.timeline.TransactionTimelineEvent
import br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val timelineFormatter = DateTimeFormatter.ofPattern("dd/MM — HH:mm")

@Composable
fun TransactionDetailsPanel(
    viewModel: TransactionsViewModel,
    onClose: () -> Unit,
    onOpenQuickEdit: (() -> Unit)?
) {
    val uiState by viewModel.uiState.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val costCenters by viewModel.costCenters.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val timeline by viewModel.timeline.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val budgetBalance by viewModel.budgetBalance.collectAsState()
    val item = uiState.selectedItem ?: return

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showReversalDialog by remember { mutableStateOf(false) }

    // Form state — reset when item changes
    var description by remember(item.id) { mutableStateOf(item.description) }
    var amountStr by remember(item.id) { mutableStateOf(item.amount.toString()) }
    var type by remember(item.id) { mutableStateOf(item.type) }
    var accountId by remember(item.id) { mutableStateOf(item.accountId) }
    var supplierId by remember(item.id) { mutableStateOf(item.supplierId) }
    var costCenterId by remember(item.id) { mutableStateOf(item.costCenterId) }
    var categoryId by remember(item.id) { mutableStateOf(item.categoryId) }
    var issueDate by remember(item.id) { mutableStateOf(item.issueDate.toLocalDate().format(dateFormatter)) }
    var dueDate by remember(item.id) { mutableStateOf(item.dueDate.toLocalDate().format(dateFormatter)) }
    var documentType by remember(item.id) { mutableStateOf(item.documentType ?: "") }
    var documentNumber by remember(item.id) { mutableStateOf(item.documentNumber ?: "") }
    var installmentTotalStr by remember(item.id) {
        mutableStateOf(item.installmentTotal?.toString() ?: "")
    }
    var notes by remember(item.id) { mutableStateOf(item.notes ?: "") }
    // Fase 7-B: contrato vinculado
    var contractId by remember(item.id) { mutableStateOf(item.contractId) }
    // Fase 7-A: toggle de recorrência (apenas em novos lançamentos)
    var makeRecurring   by remember(item.id) { mutableStateOf(false) }
    var recurInterval   by remember(item.id) { mutableStateOf(RecurrenceInterval.MENSAL) }
    var recurDayOfMonth by remember(item.id) { mutableStateOf(item.dueDate.dayOfMonth.toString()) }

    // Auto-seleciona a primeira conta disponível para novos lançamentos
    LaunchedEffect(accounts) {
        if (accountId == 0 && accounts.isNotEmpty()) {
            accountId = accounts.first().id
        }
    }

    // RN-26: dispara consulta sempre que projeto ou categoria mudam no formulário
    LaunchedEffect(costCenterId, categoryId) {
        viewModel.queryBudgetBalance(costCenterId, categoryId)
    }

    val actions = viewModel.getAvailableActions(item.status, item.type)
    val accountName = accounts.find { it.id == item.accountId }?.name ?: "—"
    val supplierName = suppliers.find { it.id == item.supplierId }?.name
    val costCenterName = costCenters.find { it.id == item.costCenterId }?.name
    val categoryName = categories.find { it.id == item.categoryId }?.name
    val canEdit = item.id == 0 || !TransactionStateMachine.isTerminal(item.status)

    val accountOptions = accounts.map { it.id to it.name }
    val counterpartLabel = if (type == TransactionType.INCOME) "CLIENTE" else "FORNECEDOR"
    val supplierOptions = suppliers
        .filter { s ->
            if (type == TransactionType.INCOME)
                s.entityType == EntityType.CLIENTE || s.entityType == EntityType.AMBOS
            else
                s.entityType == EntityType.FORNECEDOR || s.entityType == EntityType.AMBOS
        }
        .map { it.id to it.name }
    val costCenterOptions = costCenters.map { it.id to it.name }
    val categoryOptions = categories.map { it.id to it.name }

    BaseCrudPanel(
        title = if (item.id == 0) "Nova Transação" else item.description,
        subtitle = if (item.installmentTotal != null)
            "Parcela ${item.installmentCurrent}/${item.installmentTotal}"
        else "Resumo financeiro e ciclo operacional",
        onClose = onClose,
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage ?: operationError,
        onSave = if (canEdit) {
            {
                val accId = accountId.takeIf { it > 0 } ?: accounts.firstOrNull()?.id ?: 0
                val installTotal = installmentTotalStr.toIntOrNull()?.takeIf { it > 1 }
                viewModel.saveWithRecurrence(
                    item = item.copy(
                        description = description,
                        amount = amountStr.toMoney(),
                        type = type,
                        status = if (item.id == 0) TransactionStatus.PENDING else item.status,
                        accountId = accId,
                        supplierId = supplierId,
                        costCenterId = costCenterId,
                        categoryId = categoryId,
                        issueDate = parseDate(issueDate).atStartOfDay(),
                        dueDate = parseDate(dueDate).atStartOfDay(),
                        documentType = documentType.ifBlank { null },
                        documentNumber = documentNumber.ifBlank { null },
                        installmentTotal = installTotal,
                        notes = notes.ifBlank { null },
                        paymentDate = item.paymentDate,
                        paidAmount = item.paidAmount,
                        contractId = contractId
                    ),
                    recurringInterval = if (makeRecurring) recurInterval else null,
                    recurringDayOfMonth = if (makeRecurring) recurDayOfMonth.toIntOrNull()?.coerceIn(1, 31) else null
                )
            }
        } else null,
        showFooter = canEdit
    ) {
        // Status/type row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransactionStatusBadge(item.status)
            TransactionTypeLabel(item.type)
        }

        // Summary (read-only)
        DetailSection("Resumo") {
            SummaryRow("Valor total", MoneyFormatter.format(item.amount))
            item.paidAmount?.let { SummaryRow("Valor pago", MoneyFormatter.format(it)) }
            SummaryRow("Emissão", item.issueDate.format(dateFormatter))
            SummaryRow("Vencimento", item.dueDate.format(dateFormatter))
            item.paymentDate?.let { SummaryRow("Pagamento", it.format(dateFormatter)) }
            SummaryRow("Conta", accountName)
            supplierName?.let { SummaryRow("Fornecedor", it) }
            costCenterName?.let { SummaryRow("Centro de Custo", it) }
            categoryName?.let { SummaryRow("Categoria", it) }
            item.documentType?.let { dt ->
                SummaryRow("Documento", "$dt ${item.documentNumber ?: ""}".trim())
            }
            if (item.installmentTotal != null) {
                SummaryRow(
                    "Parcela",
                    "${item.installmentCurrent}/${item.installmentTotal}"
                )
            }
        }

        // Quick action buttons (RN-12 aware)
        if (item.id != 0) {
            DetailSection("Ações rápidas") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (TransactionAction.MarkPaid in actions) {
                        WsButton("Quitar", icon = Icons.Default.Check, onClick = { showPaymentDialog = true })
                    }
                    if (TransactionAction.Cancel in actions) {
                        WsOutlinedButton(
                            onClick = { viewModel.cancelTransaction(item.id) },
                            contentColor = WsDanger
                        ) {
                            Text("Cancelar")
                        }
                    }
                    if (TransactionAction.Duplicate in actions) {
                        WsIconButton(Icons.Default.ContentCopy, onClick = { viewModel.duplicateTransaction(item.id) })
                    }
                    if (TransactionAction.Edit in actions && onOpenQuickEdit != null) {
                        WsIconButton(Icons.Default.Edit, onClick = onOpenQuickEdit)
                    }
                    // RN-14: botão Estornar — só visível quando status=PAID e canConfirmPayment
                    if (TransactionAction.Reverse in actions) {
                        OutlinedButton(
                            onClick = { showReversalDialog = true },
                            border = androidx.compose.foundation.BorderStroke(1.dp, WsWarning)
                        ) {
                            Icon(
                                Icons.Default.Undo, null,
                                modifier = Modifier.size(16.dp),
                                tint = WsWarning
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Estornar", color = WsWarning)
                        }
                    }
                    // RN-31: comprovante PDF — apenas PAID
                    if (item.status == TransactionStatus.PAID) {
                        WsOutlinedButton(onClick = { viewModel.exportReceipt(item.id) }) {
                            Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Comprovante")
                        }
                    }
                }
            }
        }

        // Editable form (only when not terminal)
        if (canEdit) {
            DetailSection("Dados gerais") {
                WsTextField("DESCRIÇÃO", description) { description = it }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WsTextField("VALOR (R$)", amountStr, modifier = Modifier.weight(1f)) { amountStr = it }
                    WsTextField("PARCELAS", installmentTotalStr, modifier = Modifier.weight(1f)) {
                        installmentTotalStr = it
                    }
                }

                // RN-27: aviso de estouro de rubrica (não bloqueia)
                BudgetOverrunWarning(amountStr, budgetBalance)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WsTextField("EMISSÃO (DD/MM/AAAA)", issueDate, modifier = Modifier.weight(1f)) { issueDate = it }
                    WsTextField("VENCIMENTO (DD/MM/AAAA)", dueDate, modifier = Modifier.weight(1f)) { dueDate = it }
                }

                // Tipo
                Text("TIPO", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.values().forEach { t ->
                        WsFilterChip(
                            selected = type == t,
                            onClick = {
                                if (type != t) {
                                    type = t
                                    supplierId = null
                                }
                            },
                            label = { Text(t.displayName) }
                        )
                    }
                }

                // Conta (chips quando poucos, dropdown quando muitos)
                if (accounts.isEmpty()) {
                    Text("CONTA", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(WsDanger.copy(alpha = 0.08f))
                            .border(1.dp, WsDanger.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = WsDanger, modifier = Modifier.size(16.dp))
                        Text(
                            "Nenhuma conta cadastrada. Acesse 'Contas e Caixas' no menu lateral antes de lançar.",
                            style = MaterialTheme.typography.labelMedium,
                            color = WsDanger
                        )
                    }
                } else if (accounts.size <= 4) {
                    Text("CONTA", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.forEach { acc ->
                            WsFilterChip(
                                selected = accountId == acc.id,
                                onClick = { accountId = acc.id },
                                label = { Text(acc.name) }
                            )
                        }
                    }
                } else {
                    WsSelectField(
                        label = "CONTA",
                        options = accountOptions,
                        selectedId = accountId.takeIf { it > 0 },
                        onSelect = { accountId = it ?: 0 },
                        nullable = false
                    )
                }
            }

            DetailSection("Vínculos") {
                // Fase 7-B: seletor de contrato (apenas em novos lançamentos)
                val contracts by viewModel.contracts.collectAsState()
                val activeContracts = contracts.filter { it.status == br.com.sisgfin.contracts.ContractStatus.VIGENTE && it.type == type }
                if (item.id == 0 && activeContracts.isNotEmpty()) {
                    WsSelectField(
                        label = "CONTRATO (OPCIONAL)",
                        options = activeContracts.map { it.id to "CT-${it.number}: ${it.description}" },
                        selectedId = contractId,
                        onSelect = { id ->
                            contractId = id
                            // Auto-preenche fornecedor/cliente ao selecionar contrato
                            if (id != null && supplierId == null) {
                                supplierId = activeContracts.find { it.id == id }?.contractorId
                            }
                        }
                    )
                    // Alerta de extrapolação
                    val wouldExceed by viewModel.contractWouldExceed.collectAsState()
                    if (wouldExceed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                                .background(WsWarning.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = WsWarning,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Este lançamento excederá o valor total do contrato.",
                                style = MaterialTheme.typography.labelSmall, color = WsWarning)
                        }
                    }
                }
                if (supplierOptions.isNotEmpty()) {
                    WsSelectField(
                        label = counterpartLabel,
                        options = supplierOptions,
                        selectedId = supplierId,
                        onSelect = { supplierId = it }
                    )
                }
                if (costCenterOptions.isNotEmpty()) {
                    WsSelectField(
                        label = "CENTRO DE CUSTO",
                        options = costCenterOptions,
                        selectedId = costCenterId,
                        onSelect = { costCenterId = it }
                    )
                }
                if (categoryOptions.isNotEmpty()) {
                    WsSelectField(
                        label = "CATEGORIA",
                        options = categoryOptions,
                        selectedId = categoryId,
                        onSelect = { categoryId = it }
                    )
                }

                // RN-25/26: saldo de rubrica em tempo real
                BudgetBalanceBanner(budgetBalance, costCenterId, categoryId)
            }

            DetailSection("Documento") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WsTextField("TIPO (NF, RPA...)", documentType, modifier = Modifier.weight(1f)) { documentType = it }
                    WsTextField("NÚMERO", documentNumber, modifier = Modifier.weight(1f)) { documentNumber = it }
                }
                WsTextField("OBSERVAÇÕES", notes) { notes = it }
            }

            // Fase 7-A: toggle de recorrência (apenas novos lançamentos, não parcelados)
            if (item.id == 0 && installmentTotalStr.isBlank()) {
                DetailSection("Recorrência") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(
                            checked = makeRecurring,
                            onCheckedChange = { makeRecurring = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = WsAccent, checkedTrackColor = WsAccent.copy(alpha = 0.4f))
                        )
                        Text(
                            if (makeRecurring) "Gerar automaticamente a cada período" else "Lançamento único (sem recorrência)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (makeRecurring) {
                        Text("INTERVALO", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RecurrenceInterval.values().forEach { iv ->
                                WsFilterChip(
                                    selected = recurInterval == iv,
                                    onClick  = { recurInterval = iv },
                                    label    = {
                                        Text(iv.displayName, style = MaterialTheme.typography.labelMedium)
                                    }
                                )
                            }
                        }
                        WsTextField("DIA DO MÊS", recurDayOfMonth) { recurDayOfMonth = it }
                        Text(
                            "Um template de recorrência será criado. Os próximos lançamentos serão gerados automaticamente.",
                            style = MaterialTheme.typography.labelSmall,
                            color = WsTextSecondary
                        )
                    }
                }
            }
        }

        TimelineSection(timeline)
    }

    // Modal: baixa rápida (RN-16)
    if (showPaymentDialog) {
        PaymentRecordDialog(
            issueDate = item.issueDate,
            totalAmount = item.amount,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { date, paid ->
                viewModel.recordPayment(item.id, date, paid)
                showPaymentDialog = false
            }
        )
    }

    // Modal: estorno (RN-14/22/23)
    if (showReversalDialog) {
        ReversalDialog(
            transactionDescription = item.description,
            amount = item.amount,
            onDismiss = { showReversalDialog = false },
            onConfirm = { justification ->
                viewModel.reverseTransaction(item.id, justification)
                showReversalDialog = false
            }
        )
    }
}

// RN-27: aviso quando o valor do lançamento ultrapassa o saldo disponível da rubrica
@Composable
private fun BudgetOverrunWarning(amountStr: String, balance: BudgetBalance?) {
    if (balance == null) return

    val parsedAmount = remember(amountStr) {
        amountStr.replace(",", ".").toBigDecimalOrNull()
            ?.let { br.com.sisgfin.financial.money.Money(it) }
            ?: br.com.sisgfin.financial.money.Money.ZERO
    }

    // Sem valor digitado ou zero — sem aviso
    if (parsedAmount.isZero()) return

    val projected = balance.realized + parsedAmount
    val wouldExceed = projected > balance.annualAmount
    if (!wouldExceed) return

    val excess = projected - balance.annualAmount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(WsWarning.copy(alpha = 0.08f))
            .border(1.dp, WsWarning.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Warning, null, tint = WsWarning, modifier = Modifier.size(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Este lançamento ultrapassa o orçamento da rubrica",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = WsWarning
            )
            Text(
                "Saldo disponível: ${MoneyFormatter.format(balance.available)} — " +
                "estouro de ${MoneyFormatter.format(excess)}",
                style = MaterialTheme.typography.labelSmall,
                color = WsWarning.copy(alpha = 0.85f)
            )
        }
    }
}

// RN-25/26: banner de saldo de rubrica no formulário de lançamento
@Composable
private fun BudgetBalanceBanner(
    balance: BudgetBalance?,
    costCenterId: Int?,
    categoryId: Int?
) {
    // Só exibe quando projeto e categoria estão selecionados
    if (costCenterId == null || costCenterId == 0 || categoryId == null || categoryId == 0) return

    if (balance == null) {
        // Rubrica sem orçamento cadastrado — aviso neutro
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, WsBorder, RoundedCornerShape(6.dp))
                .background(WsElevated)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                "Nenhum orçamento cadastrado para esta rubrica em ${java.time.LocalDate.now().year}.",
                style = MaterialTheme.typography.labelMedium,
                color = WsTextSecondary
            )
        }
        return
    }

    val accentColor: Color = when {
        balance.isOverBudget       -> WsDanger
        balance.utilizationPct >= 80.0 -> WsWarning
        else                       -> WsSuccess
    }
    val borderColor = accentColor.copy(alpha = 0.4f)
    val bgColor     = accentColor.copy(alpha = 0.05f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "RUBRICA ${java.time.LocalDate.now().year}",
            style = MaterialTheme.typography.labelSmall,
            color = WsTextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BudgetFigure("Dotado",     MoneyFormatter.format(balance.annualAmount), WsTextSecondary)
            BudgetFigure("Realizado",  MoneyFormatter.format(balance.realized),     WsWarning)
            BudgetFigure(
                label = "Disponível",
                value = MoneyFormatter.format(balance.available),
                color = accentColor
            )
        }

        // Barra de progresso
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(WsBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(minOf(balance.utilizationPct / 100.0, 1.0).toFloat())
                        .fillMaxHeight()
                        .background(accentColor)
                )
            }
            Text(
                if (balance.isOverBudget)
                    "Orçamento ultrapassado em ${MoneyFormatter.format(balance.available.abs())}"
                else
                    "${"%.1f".format(balance.utilizationPct)}% utilizado",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = if (balance.isOverBudget) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun BudgetFigure(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TimelineSection(events: List<TransactionTimelineEvent>) {
    if (events.isEmpty()) return
    DetailSection("Linha do tempo") {
        events.forEach { event ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    event.createdAt.format(timelineFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = WsAccent,
                    modifier = Modifier.width(100.dp)
                )
                Column {
                    Text(event.eventType.displayLabel, style = MaterialTheme.typography.bodyMedium)
                    Text(event.message, style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
                }
            }
        }
    }
}

// Modal de baixa rápida com validação RN-16
@Composable
fun PaymentRecordDialog(
    issueDate: LocalDateTime,
    totalAmount: Money,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime, Money) -> Unit
) {
    var paidStr by remember { mutableStateOf(totalAmount.toString()) }
    var payDate by remember { mutableStateOf(LocalDate.now().format(dateFormatter)) }

    val parsedDate = parseDate(payDate)
    val dateError = if (parsedDate < issueDate.toLocalDate()) {
        "Data de pagamento não pode ser anterior à data de emissão"
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        containerColor = WsSurface,
        title = { Text("Registrar pagamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Total: ${MoneyFormatter.format(totalAmount)}", color = WsTextSecondary)
                WsTextField("VALOR PAGO (R$)", paidStr) { paidStr = it }
                WsTextField("DATA DE PAGAMENTO (DD/MM/AAAA)", payDate) { payDate = it }
                if (dateError != null) {
                    Text(
                        dateError,
                        style = MaterialTheme.typography.labelMedium,
                        color = WsDanger
                    )
                }
            }
        },
        confirmButton = {
            WsButton("Confirmar", onClick = {
                if (dateError == null) {
                    onConfirm(parsedDate.atStartOfDay(), paidStr.toMoney())
                }
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// Modal de estorno — campo de justificativa obrigatório (RN-14/22/23)
@Composable
fun ReversalDialog(
    transactionDescription: String,
    amount: Money,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var justification by remember { mutableStateOf("") }
    val canConfirm = justification.trim().length >= 10

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        containerColor = WsSurface,
        title = { Text("Estornar lançamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Lançamento: $transactionDescription",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextSecondary
                )
                Text(
                    "Valor: ${MoneyFormatter.format(amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextSecondary
                )
                HorizontalDivider(color = WsBorder)
                Text(
                    "Esta operação é irreversível. Um lançamento de estorno será criado automaticamente.",
                    style = MaterialTheme.typography.labelMedium,
                    color = WsWarning
                )
                OutlinedTextField(
                    value = justification,
                    onValueChange = { justification = it },
                    label = { Text("Justificativa (obrigatório)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    isError = justification.isNotEmpty() && !canConfirm,
                    supportingText = if (justification.isNotEmpty() && !canConfirm) {
                        { Text("Mínimo 10 caracteres") }
                    } else null,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WsAccent,
                        unfocusedBorderColor = WsBorder,
                        errorBorderColor = WsDanger
                    )
                )
            }
        },
        confirmButton = {
            WsButton("Confirmar estorno", onClick = {
                if (canConfirm) onConfirm(justification.trim())
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun parseDate(value: String): LocalDate =
    try {
        LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (_: Exception) {
        try { LocalDate.parse(value) } catch (_: Exception) { LocalDate.now() }
    }
