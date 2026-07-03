package br.com.sisgfin.statement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.*
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.transactions.TransactionType
import br.com.sisgfin.financial.transactions.TransactionTypeLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun StatementScreen(viewModel: StatementViewModel) {
    val state by viewModel.uiState.collectAsState()

    // Auto-dismiss export message
    LaunchedEffect(state.exportMessage) {
        if (state.exportMessage != null) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearExportMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Extrato por Conta", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Lançamentos liquidados com saldo acumulado",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextSecondary
                )
            }
            // Export buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.exportExcel() },
                    enabled = state.entries.isNotEmpty(),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WsBorderLight),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WsTextSecondary)
                ) {
                    Icon(Icons.Outlined.TableChart, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Excel")
                }
                WsButton("PDF", icon = Icons.Default.PictureAsPdf, onClick = { viewModel.exportPdf() })
            }
        }

        // Export feedback
        state.exportMessage?.let { msg ->
            val isError = msg.startsWith("Erro")
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(6.dp),
                color = if (isError) WsDanger.copy(alpha = 0.12f) else WsSuccess.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isError) WsDanger else WsSuccess)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                        null,
                        tint = if (isError) WsDanger else WsSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(msg, style = MaterialTheme.typography.labelMedium, color = WsTextPrimary, modifier = Modifier.weight(1f))
                }
            }
        }

        // Filter bar
        StatementFilterBar(
            accounts = state.accounts,
            costCenters = state.costCenters,
            categories = state.categories,
            filter = state.filter,
            onFilterChange = { viewModel.applyFilter(it) }
        )

        Spacer(Modifier.height(16.dp))

        if (state.isLoading) {
            WsLoaderFullscreen()
        } else if (state.filter.accountId == null) {
            EmptyState("Selecione uma conta para visualizar o extrato.")
        } else if (state.entries.isEmpty()) {
            EmptyState("Nenhum lançamento liquidado encontrado para os filtros selecionados.")
        } else {
            StatementTable(state)
        }
    }
}

@Composable
private fun StatementFilterBar(
    accounts: List<FinancialAccount>,
    costCenters: List<CostCenter>,
    categories: List<br.com.sisgfin.financial.categories.ExpenseCategory>,
    filter: StatementFilter,
    onFilterChange: (StatementFilter) -> Unit
) {
    var fromStr by remember(filter.from) { mutableStateOf(filter.from?.format(dateFmt) ?: "") }
    var toStr   by remember(filter.to)   { mutableStateOf(filter.to?.format(dateFmt) ?: "") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = WsSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, WsBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Row 1: conta + período
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.weight(1.5f)) {
                    WsSelectField(
                        label = "CONTA",
                        options = accounts.map { it.id to it.name },
                        selectedId = filter.accountId,
                        onSelect = { onFilterChange(filter.copy(accountId = it)) },
                        nullable = false,
                        placeholder = "Selecionar conta..."
                    )
                }
                WsTextField(
                    "DE (DD/MM/AAAA)",
                    fromStr,
                    modifier = Modifier.weight(1f)
                ) { fromStr = it }
                WsTextField(
                    "ATÉ (DD/MM/AAAA)",
                    toStr,
                    modifier = Modifier.weight(1f)
                ) { toStr = it }
                WsButton("Aplicar", icon = Icons.Default.Search, onClick = {
                    onFilterChange(
                        filter.copy(
                            from = parseLocalDate(fromStr),
                            to = parseLocalDate(toStr)
                        )
                    )
                })
                if (filter.from != null || filter.to != null || filter.type != null || filter.costCenterId != null || filter.categoryId != null) {
                    WsIconButton(Icons.Default.Close, onClick = {
                        onFilterChange(StatementFilter(accountId = filter.accountId))
                    })
                }
            }
            // Row 2: tipo + projeto + categoria
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                // Tipo chips
                Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TIPO", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WsFilterChip(
                            selected = filter.type == null,
                            onClick = { onFilterChange(filter.copy(type = null)) },
                            label = { Text("Todos", fontSize = 11.sp) }
                        )
                        listOf(
                            TransactionType.INCOME,
                            TransactionType.EXPENSE,
                            TransactionType.TRANSFER,
                            TransactionType.ADJUSTMENT
                        ).forEach { t ->
                            WsFilterChip(
                                selected = filter.type == t,
                                onClick = { onFilterChange(filter.copy(type = if (filter.type == t) null else t)) },
                                label = { Text(t.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                // Centro de Custo
                if (costCenters.isNotEmpty()) {
                    Box(modifier = Modifier.weight(1f)) {
                        WsSelectField(
                            label = "CENTRO DE CUSTO",
                            options = costCenters.map { it.id to it.name },
                            selectedId = filter.costCenterId,
                            onSelect = { onFilterChange(filter.copy(costCenterId = it)) }
                        )
                    }
                }
                // Categoria
                if (categories.isNotEmpty()) {
                    Box(modifier = Modifier.weight(1f)) {
                        WsSelectField(
                            label = "CATEGORIA",
                            options = categories.map { it.id to it.displayName },
                            selectedId = filter.categoryId,
                            onSelect = { onFilterChange(filter.copy(categoryId = it)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementTable(state: StatementUiState) {
    val listState = rememberLazyListState()
    val totalCredits = state.entries.filter { it.isCredit }.fold(br.com.sisgfin.financial.money.Money.ZERO) { a, e -> a + e.signedAmount.abs() }
    val totalDebits  = state.entries.filterNot { it.isCredit }.fold(br.com.sisgfin.financial.money.Money.ZERO) { a, e -> a + e.signedAmount.abs() }
    val closingBalance = state.entries.lastOrNull()?.runningBalance ?: state.openingBalance

    Column(modifier = Modifier.fillMaxSize()) {
        // Opening balance banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(WsElevated)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Saldo de abertura", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
            Text(
                MoneyFormatter.format(state.openingBalance),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (state.openingBalance.isNegative()) WsDanger else WsTextPrimary
            )
        }

        // Table
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
                .background(WsSurface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().background(WsElevated).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableHeaderCell("DATA PAG.", Modifier.width(90.dp))
                    TableHeaderCell("DESCRIÇÃO", Modifier.weight(2f))
                    TableHeaderCell("TIPO", Modifier.width(90.dp))
                    TableHeaderCell("DOCUMENTO", Modifier.width(100.dp))
                    TableHeaderCell("DÉBITO", Modifier.width(110.dp), TextAlign.End)
                    TableHeaderCell("CRÉDITO", Modifier.width(110.dp), TextAlign.End)
                    TableHeaderCell("SALDO", Modifier.width(120.dp), TextAlign.End)
                }
                HorizontalDivider(color = WsBorder)

                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(state.entries) { entry ->
                        StatementRow(entry)
                        HorizontalDivider(color = WsBorder.copy(alpha = 0.35f))
                    }
                }

                // Footer totals
                HorizontalDivider(color = WsBorder)
                Row(
                    modifier = Modifier.fillMaxWidth().background(WsElevated).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${state.entries.size} lançamentos",
                        modifier = Modifier.weight(2f).padding(start = 90.dp + 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = WsTextSecondary
                    )
                    Spacer(Modifier.width(90.dp + 100.dp)) // tipo + doc
                    Text(
                        MoneyFormatter.format(totalDebits),
                        modifier = Modifier.width(110.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = WsDanger,
                        textAlign = TextAlign.End
                    )
                    Text(
                        MoneyFormatter.format(totalCredits),
                        modifier = Modifier.width(110.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = WsSuccess,
                        textAlign = TextAlign.End
                    )
                    Text(
                        MoneyFormatter.format(closingBalance),
                        modifier = Modifier.width(120.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (closingBalance.isNegative()) WsDanger else WsSuccess,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun StatementRow(entry: StatementEntry) {
    val tx = entry.transaction
    val payDate = (tx.paymentDate ?: tx.dueDate).format(dateFmt)
    val docLabel = listOfNotNull(tx.documentType, tx.documentNumber).joinToString(" ").ifBlank { "—" }
    val amountColor = if (entry.isCredit) WsSuccess else WsDanger
    val balanceColor = if (entry.runningBalance.isNegative()) WsDanger
                       else if (entry.runningBalance.isZero()) WsTextSecondary
                       else WsTextPrimary

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(payDate, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
        Text(
            tx.description,
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Tipo badge
        Box(modifier = Modifier.width(90.dp)) {
            TransactionTypeLabel(tx.type)
        }
        Text(docLabel, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.labelMedium, color = WsTextSecondary, maxLines = 1)
        // Débito
        Text(
            if (!entry.isCredit) MoneyFormatter.format(entry.signedAmount.abs()) else "",
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = WsDanger,
            textAlign = TextAlign.End
        )
        // Crédito
        Text(
            if (entry.isCredit) MoneyFormatter.format(entry.signedAmount.abs()) else "",
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = WsSuccess,
            textAlign = TextAlign.End
        )
        // Saldo acumulado
        Text(
            MoneyFormatter.format(entry.runningBalance),
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = balanceColor,
            textAlign = TextAlign.End
        )
    }
}

private fun parseLocalDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim(), dateFmt) }.getOrNull()
