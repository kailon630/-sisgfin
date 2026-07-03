package br.com.sisgfin.financial.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.*
import br.com.sisgfin.core.ui.keyboard.KeyboardShortcuts
import br.com.sisgfin.core.ui.notifications.CrudEventEffects
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.money.toMoney
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// ── Agrupamento temporal (Ajuste 3) ──────────────────────────────────────────

private data class TxGroup(val label: String, val badgeColor: Color, val items: List<Transaction>)

@Composable
private fun groupByTimeSection(items: List<Transaction>): List<TxGroup> {
    val today    = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val weekEnd  = today.with(DayOfWeek.SUNDAY)
    val active   = setOf(TransactionStatus.PENDING, TransactionStatus.PARTIAL, TransactionStatus.SCHEDULED)

    val overdue    = items.filter { it.status == TransactionStatus.OVERDUE }
    val dueToday   = items.filter { it.status in active && it.dueDate.toLocalDate() == today }
    val dueTomorrow = items.filter { it.status in active && it.dueDate.toLocalDate() == tomorrow }
    val thisWeek   = items.filter {
        it.status in active && it.dueDate.toLocalDate().let { d -> d > tomorrow && d <= weekEnd }
    }
    val later = items.filter { it.status in active && it.dueDate.toLocalDate() > weekEnd }

    return buildList {
        if (overdue.isNotEmpty())     add(TxGroup("Vencidos (${overdue.size})",      WsDanger,        overdue))
        if (dueToday.isNotEmpty())    add(TxGroup("Hoje (${dueToday.size})",          WsWarning,       dueToday))
        if (dueTomorrow.isNotEmpty()) add(TxGroup("Amanhã (${dueTomorrow.size})",     WsAccent,        dueTomorrow))
        if (thisWeek.isNotEmpty())    add(TxGroup("Esta semana (${thisWeek.size})",   WsTextSecondary, thisWeek))
        if (later.isNotEmpty())       add(TxGroup("Próximas (${later.size})",         WsTextSecondary, later))
    }
}

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listFilter by viewModel.listFilter.collectAsState()
    val transferDialogVisible by viewModel.transferDialogVisible.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    var contextMenuTx by remember { mutableStateOf<Transaction?>(null) }
    var contextMenuExpanded by remember { mutableStateOf(false) }

    CrudEventEffects(viewModel)

    // Recarrega contas/fornecedores/categorias sempre que a tela é exibida,
    // pois o ViewModel é singleton e dados criados em outras telas não atualizam automaticamente.
    LaunchedEffect(Unit) { viewModel.loadReferenceData() }

    fun openPanel(tx: Transaction) {
        viewModel.selectTransaction(tx)
        onShowRightPanel {
            TransactionDetailsPanel(
                viewModel = viewModel,
                onClose = onCloseRightPanel,
                onOpenQuickEdit = { viewModel.openDialog(tx) }
            )
        }
    }

    fun openNewPanel() {
        viewModel.openNew()
        onShowRightPanel {
            TransactionDetailsPanel(viewModel = viewModel, onClose = onCloseRightPanel, onOpenQuickEdit = null)
        }
    }

    fun openExpensePanel() {
        viewModel.openNewExpense()
        onShowRightPanel {
            TransactionDetailsPanel(viewModel = viewModel, onClose = onCloseRightPanel, onOpenQuickEdit = null)
        }
    }

    fun openIncomePanel() {
        viewModel.openNewIncome()
        onShowRightPanel {
            TransactionDetailsPanel(viewModel = viewModel, onClose = onCloseRightPanel, onOpenQuickEdit = null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    KeyboardShortcuts.isEscape(event) -> {
                        viewModel.closeDialog()
                        onCloseRightPanel()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.F -> {
                        searchFocus.requestFocus()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.D -> {
                        uiState.selectedItem?.let { viewModel.duplicateTransaction(it.id) }
                        true
                    }
                    event.key == Key.Enter -> {
                        val target = uiState.selectedItem ?: uiState.items.firstOrNull()
                        target?.let { openPanel(it) }
                        true
                    }
                    event.key == Key.Delete -> {
                        uiState.selectedItem?.let { viewModel.cancelTransaction(it.id) }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Movimentações Financeiras", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Contas a pagar e receber — ciclo operacional",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { viewModel.openTransferDialog() },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(36.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WsBorderLight),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WsTextSecondary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Transferência", style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp))
                }
                // Ajuste 2: dois botões explícitos com tipo pré-selecionado
                OutlinedButton(
                    onClick = { openExpensePanel() },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(36.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WsDanger.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WsDanger),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Despesa", style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp))
                }
                WsButton(
                    label = "Receita",
                    icon = Icons.Default.Add,
                    containerColor = WsSuccess,
                    onClick = { openIncomePanel() }
                )
                WsIconButton(Icons.Default.Refresh) { viewModel.load() }
            }
        }

        TransactionFilterBar(
            listFilter = listFilter,
            searchQuery = searchQuery,
            searchFocus = searchFocus,
            onSearchChange = {
                searchQuery = it
                viewModel.applySearchToService(it)
            },
            onFilter = { viewModel.applyQuickFilter(it) },
            onClear = {
                searchQuery = ""
                viewModel.clearFilters()
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
                .background(WsSurface)
        ) {
            if (uiState.items.isEmpty() && !uiState.isLoading) {
                EmptyState("Nenhuma transação encontrada.")
            } else {
                // Ajuste 3: cabeçalho fixo + corpo lazy (agrupado ou plano)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(WsElevated).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableHeaderCell("TIPO", Modifier.weight(0.9f))
                        TableHeaderCell("DESCRIÇÃO", Modifier.weight(2.2f))
                        TableHeaderCell("VENCIMENTO", Modifier.weight(1f))
                        TableHeaderCell("VALOR", Modifier.weight(1f), TextAlign.End)
                        TableHeaderCell("STATUS", Modifier.weight(1f), TextAlign.Center)
                    }
                    HorizontalDivider(color = WsBorder)

                    if (listFilter is TransactionListFilter.ActionRequired) {
                        // Tabela agrupada por seção temporal
                        GroupedTransactionTable(
                            groups = groupByTimeSection(uiState.items),
                            selectedId = uiState.selectedItem?.id,
                            onSingleClick = { openPanel(it) },
                            onDoubleClick = { viewModel.openDialog(it) },
                            onContextMenu = {
                                contextMenuTx = it
                                contextMenuExpanded = true
                                viewModel.selectTransaction(it)
                            }
                        )
                    } else {
                        // Tabela plana (filtros específicos)
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(uiState.items, key = { it.id }) { item ->
                                TransactionRow(
                                    item = item,
                                    isSelected = uiState.selectedItem?.id == item.id,
                                    onSingleClick  = { openPanel(item) },
                                    onDoubleClick  = { viewModel.openDialog(item) },
                                    onContextMenu  = {
                                        contextMenuTx = item
                                        contextMenuExpanded = true
                                        viewModel.selectTransaction(item)
                                    }
                                )
                                HorizontalDivider(color = WsBorder.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isDialogVisible) {
            TransactionQuickPopup(
                item = uiState.selectedItem,
                onSave = { viewModel.save(it) },
                onCancel = { viewModel.closeDialog() }
            )
        }

        if (transferDialogVisible) {
            TransferDialog(
                accounts = accounts,
                onConfirm = { srcId, dstId, amount, date, desc, notes ->
                    viewModel.createTransfer(srcId, dstId, amount, date, desc, notes)
                },
                onDismiss = { viewModel.closeTransferDialog() }
            )
        }

        TransactionContextMenu(
            expanded = contextMenuExpanded,
            transaction = contextMenuTx,
            onDismiss = { contextMenuExpanded = false },
            onEdit = {
                contextMenuTx?.let { viewModel.openDialog(it) }
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
    }
}

@Composable
private fun TransactionFilterBar(
    listFilter: TransactionListFilter,
    searchQuery: String,
    searchFocus: FocusRequester,
    onSearchChange: (String) -> Unit,
    onFilter: (TransactionListFilter) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.width(280.dp).height(36.dp),
                shape = RoundedCornerShape(6.dp),
                color = WsSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, WsBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = WsTextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        placeholder = { Text("Buscar... (Ctrl+F)") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = WsTextPrimary,
                            unfocusedTextColor = WsTextPrimary,
                            focusedPlaceholderColor = WsTextDisabled,
                            unfocusedPlaceholderColor = WsTextDisabled,
                            cursorColor = WsAccent
                        )
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            // Ajuste 1: "A pagar" como chip padrão (PENDING + OVERDUE + PARTIAL)
            WsFilterChip(
                selected = listFilter is TransactionListFilter.ActionRequired,
                onClick = { onFilter(TransactionListFilter.ActionRequired) },
                label = { Text("A pagar") }
            )
            WsFilterChip(
                selected = listFilter is TransactionListFilter.All,
                onClick = onClear,
                label = { Text("Todas") }
            )
            WsFilterChip(
                selected = listFilter is TransactionListFilter.DueToday,
                onClick = { onFilter(TransactionListFilter.DueToday) },
                label = { Text("Vence hoje") }
            )
            WsFilterChip(
                selected = listFilter is TransactionListFilter.Overdue,
                onClick = { onFilter(TransactionListFilter.Overdue) },
                label = { Text("Vencidas") }
            )
            WsFilterChip(
                selected = listFilter is TransactionListFilter.Paid,
                onClick = { onFilter(TransactionListFilter.Paid) },
                label = { Text("Pagas") }
            )
            WsFilterChip(
                selected = listFilter is TransactionListFilter.ByType && listFilter.type == TransactionType.EXPENSE,
                onClick = { onFilter(TransactionListFilter.ByType(TransactionType.EXPENSE)) },
                label = { Text("Despesas") }
            )
            WsFilterChip(
                selected = listFilter is TransactionListFilter.ByType && listFilter.type == TransactionType.INCOME,
                onClick = { onFilter(TransactionListFilter.ByType(TransactionType.INCOME)) },
                label = { Text("Receitas") }
            )
            WsFilterChip(
                selected = listFilter is TransactionListFilter.DuePeriod,
                onClick = {
                    val now = LocalDate.now()
                    onFilter(TransactionListFilter.DuePeriod(now, now.plusDays(30)))
                },
                label = { Text("30 dias") }
            )
        }
    }
}

// ── Tabela agrupada (Ajuste 3) ───────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupedTransactionTable(
    groups: List<TxGroup>,
    selectedId: Int?,
    onSingleClick: (Transaction) -> Unit,
    onDoubleClick: (Transaction) -> Unit,
    onContextMenu: (Transaction) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groups.forEach { group ->
            stickyHeader(key = "header_${group.label}") {
                GroupHeader(group)
            }
            items(group.items, key = { it.id }) { item ->
                TransactionRow(
                    item          = item,
                    isSelected    = selectedId == item.id,
                    onSingleClick = { onSingleClick(item) },
                    onDoubleClick = { onDoubleClick(item) },
                    onContextMenu = { onContextMenu(item) }
                )
                HorizontalDivider(color = WsBorder.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun GroupHeader(group: TxGroup) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WsElevated)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(group.badgeColor, CircleShape)
            )
            Text(
                text  = group.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize      = 11.sp,
                    letterSpacing = 0.6.sp
                ),
                color = group.badgeColor
            )
        }
        HorizontalDivider(color = WsBorder)
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    item: Transaction,
    isSelected: Boolean,
    onSingleClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onContextMenu: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val statusAccent = when (item.status) {
        TransactionStatus.OVERDUE -> WsDanger
        TransactionStatus.PARTIAL -> WsWarning
        TransactionStatus.PAID -> WsSuccess.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val bg = when {
        isSelected -> WsAccent.copy(alpha = 0.1f)
        isHovered -> WsElevated
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(statusAccent)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .hoverable(interactionSource)
                .background(bg)
                .combinedClickable(
                    onClick = onSingleClick,
                    onDoubleClick = onDoubleClick,
                    onLongClick = onContextMenu
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransactionTypeLabel(item.type, Modifier.weight(0.9f))
            Text(
                item.description,
                modifier = Modifier.weight(2.2f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (item.status == TransactionStatus.PAID) WsTextSecondary else Color.Unspecified
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.dueDate.format(dateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.status == TransactionStatus.OVERDUE) WsDanger else WsTextSecondary
                )
                item.paidAmount?.let {
                    Text(
                        "Pago: ${MoneyFormatter.format(it)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = WsTextDisabled
                    )
                }
            }
            Text(
                MoneyFormatter.format(item.amount),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                color = when (item.type) {
                    TransactionType.INCOME -> WsSuccess
                    TransactionType.EXPENSE -> WsTextPrimary
                    else -> WsTextSecondary
                }
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TransactionStatusBadge(item.status)
            }
        }
    }
}

@Composable
private fun TransactionContextMenu(
    expanded: Boolean,
    transaction: Transaction?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    onDuplicate: () -> Unit,
    onDetails: () -> Unit
) {
    if (!expanded || transaction == null) return
    DropdownMenu(expanded = true, onDismissRequest = onDismiss, containerColor = WsElevated) {
        DropdownMenuItem(text = { Text("Abrir detalhes", color = WsTextPrimary) }, onClick = onDetails)
        DropdownMenuItem(text = { Text("Editar", color = WsTextPrimary) }, onClick = onEdit)
        if (transaction.status != TransactionStatus.PAID && transaction.status != TransactionStatus.CANCELED) {
            DropdownMenuItem(text = { Text("Quitar", color = WsTextPrimary) }, onClick = onPay)
        }
        DropdownMenuItem(text = { Text("Duplicar", color = WsTextPrimary) }, onClick = onDuplicate)
        if (transaction.status != TransactionStatus.CANCELED) {
            DropdownMenuItem(text = { Text("Cancelar", color = WsTextPrimary) }, onClick = onCancel)
        }
    }
}

@Composable
fun TransactionQuickPopup(
    item: Transaction?,
    onSave: (Transaction) -> Unit,
    onCancel: () -> Unit
) {
    if (item == null) return
    var description by remember(item.id) { mutableStateOf(item.description) }
    var amount by remember(item.id) { mutableStateOf(item.amount.toString()) }

    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(8.dp),
        containerColor = WsSurface,
        modifier = Modifier.width(520.dp).padding(16.dp),
        title = { Text("Edição rápida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WsTextField("DESCRIÇÃO", description) { description = it }
                WsTextField("VALOR", amount) { amount = it }
                TransactionStatusBadge(item.status)
                Text("Status alterado apenas via quitação/cancelamento.", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
            }
        },
        confirmButton = {
            WsButton("Salvar") {
                onSave(item.copy(description = description, amount = amount.toMoney()))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
    )
}

@Composable
private fun TransferDialog(
    accounts: List<br.com.sisgfin.FinancialAccount>,
    onConfirm: (sourceId: Int, destId: Int, amount: br.com.sisgfin.financial.money.Money, date: LocalDateTime, description: String, notes: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val accountOptions = remember(accounts) { accounts.map { it.id to it.name } }

    var sourceAccountId by remember { mutableStateOf<Int?>(null) }
    var destAccountId   by remember { mutableStateOf<Int?>(null) }
    var amountText      by remember { mutableStateOf("") }
    var dateText        by remember { mutableStateOf(LocalDate.now().format(dateFormatter)) }
    var description     by remember { mutableStateOf("") }
    var notes           by remember { mutableStateOf("") }
    var errorMsg        by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = WsSurface,
        modifier = Modifier.width(560.dp),
        title = { Text("Nova Transferência") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                WsSelectField(
                    label = "CONTA ORIGEM",
                    options = accountOptions,
                    selectedId = sourceAccountId,
                    onSelect = { sourceAccountId = it },
                    placeholder = "Selecionar conta de origem...",
                    nullable = false
                )
                WsSelectField(
                    label = "CONTA DESTINO",
                    options = accountOptions.filter { it.first != sourceAccountId },
                    selectedId = destAccountId,
                    onSelect = { destAccountId = it },
                    placeholder = "Selecionar conta de destino...",
                    nullable = false
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        WsTextField("VALOR (R$)", amountText) { amountText = it }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        WsTextField("DATA (DD/MM/AAAA)", dateText) { dateText = it }
                    }
                }
                WsTextField("DESCRIÇÃO", description) { description = it }
                WsTextField("OBSERVAÇÕES (opcional)", notes) { notes = it }
                errorMsg?.let {
                    Text(it, color = WsDanger, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            WsButton("Confirmar Transferência") {
                errorMsg = null
                val srcId = sourceAccountId
                val dstId = destAccountId
                if (srcId == null) { errorMsg = "Selecione a conta de origem."; return@WsButton }
                if (dstId == null) { errorMsg = "Selecione a conta de destino."; return@WsButton }
                if (srcId == dstId) { errorMsg = "A conta de origem e destino não podem ser iguais."; return@WsButton }
                val amount = runCatching { amountText.toMoney() }.getOrElse {
                    errorMsg = "Valor inválido."; return@WsButton
                }
                if (amount.isZero() || amount.isNegative()) { errorMsg = "O valor deve ser positivo."; return@WsButton }
                val date = runCatching { LocalDate.parse(dateText, dateFormatter).atStartOfDay() }.getOrElse {
                    errorMsg = "Data inválida. Use o formato DD/MM/AAAA."; return@WsButton
                }
                val desc = description.trim()
                if (desc.isBlank()) { errorMsg = "Informe uma descrição."; return@WsButton }
                onConfirm(srcId, dstId, amount, date, desc, notes.trim().ifBlank { null })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
