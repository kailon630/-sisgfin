package br.com.sisgfin.reports

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.*
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun ReportsScreen(viewModel: ReportsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    // Auto-dismiss export message
    LaunchedEffect(state.exportMessage) {
        if (state.exportMessage != null) {
            kotlinx.coroutines.delay(6000)
            viewModel.clearExportMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Title
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Relatórios", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Livro Diário, Balancete e Demonstrativo Financeiro",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextSecondary
                )
            }
        }

        // Export feedback banner
        state.exportMessage?.let { msg ->
            val isError = msg.startsWith("Erro")
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(6.dp),
                color = if (isError) WsDanger.copy(alpha = 0.1f) else WsSuccess.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isError) WsDanger else WsSuccess)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                        null, tint = if (isError) WsDanger else WsSuccess, modifier = Modifier.size(16.dp)
                    )
                    Text(msg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                }
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            containerColor = WsSurface,
            contentColor = WsAccent
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Livro Diário", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Balancete", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
            }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                Text("Demonstrativo", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
            }
        }

        AnimatedContent(targetState = selectedTab) { tab ->
            when (tab) {
                0 -> LivroDiarioTab(state, viewModel)
                1 -> BalanceteTab(state, viewModel)
                2 -> DemonstrativoTab(state, viewModel)
            }
        }
    }
}

// ── Livro Diário ─────────────────────────────────────────────────────────────

@Composable
private fun LivroDiarioTab(state: ReportsUiState, viewModel: ReportsViewModel) {
    val filter = state.livroDiarioFilter

    var fromStr by remember(filter.from) { mutableStateOf(filter.from.format(dateFmt)) }
    var toStr   by remember(filter.to)   { mutableStateOf(filter.to.format(dateFmt)) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Filter bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = WsSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, WsBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    WsTextField("DE (DD/MM/AAAA)", fromStr, modifier = Modifier.weight(1f)) { fromStr = it }
                    WsTextField("ATÉ (DD/MM/AAAA)", toStr, modifier = Modifier.weight(1f)) { toStr = it }
                    Box(modifier = Modifier.weight(1.5f)) {
                        WsSelectField(
                            label = "CONTA (opcional)",
                            options = state.accounts.map { it.id to it.name },
                            selectedId = filter.accountId,
                            onSelect = {
                                viewModel.applyLivroDiarioFilter(filter.copy(accountId = it))
                            },
                            nullable = true,
                            placeholder = "Todas as contas"
                        )
                    }
                    WsButton("Filtrar", icon = Icons.Default.Search, onClick = {
                        val from = parseDate(fromStr) ?: filter.from
                        val to   = parseDate(toStr)   ?: filter.to
                        viewModel.applyLivroDiarioFilter(filter.copy(from = from, to = to))
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WsOutlinedButton(
                        onClick = { viewModel.exportLivroDiarioPdf() },
                        enabled = state.livroDiarioEntries.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exportar PDF")
                    }
                    WsOutlinedButton(
                        onClick = { viewModel.exportLivroDiarioExcel() },
                        enabled = state.livroDiarioEntries.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.TableChart, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exportar Excel")
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.livroDiarioEntries.isNotEmpty()) {
                        Text(
                            "${state.livroDiarioEntries.size} lançamento(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = WsTextSecondary,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }

        // Table
        if (state.isLoading) {
            WsLoaderFullscreen()
        } else if (state.livroDiarioEntries.isEmpty()) {
            EmptyState("Nenhum lançamento liquidado encontrado para o período selecionado.")
        } else {
            LivroDiarioTable(state.livroDiarioEntries)
        }
    }
}

@Composable
private fun LivroDiarioTable(entries: List<LivroDiarioEntry>) {
    val total = entries.fold(Money.ZERO) { acc, e -> acc + (e.transaction.paidAmount ?: e.transaction.amount) }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                TableHeaderCell("N°",            Modifier.width(44.dp))
                TableHeaderCell("DATA PAG.",     Modifier.width(90.dp))
                TableHeaderCell("HISTÓRICO",      Modifier.weight(3f))
                TableHeaderCell("TIPO",           Modifier.width(90.dp))
                TableHeaderCell("CONTA",          Modifier.width(130.dp))
                TableHeaderCell("VALOR",          Modifier.width(120.dp), TextAlign.End)
            }
            HorizontalDivider(color = WsBorder)

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(entries) { entry ->
                    LivroDiarioRow(entry)
                    HorizontalDivider(color = WsBorder.copy(alpha = 0.35f))
                }
            }

            // Footer
            HorizontalDivider(color = WsBorder)
            Row(
                modifier = Modifier.fillMaxWidth().background(WsElevated)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${entries.size} lançamento(s)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = WsTextSecondary
                )
                Text(
                    MoneyFormatter.format(total),
                    modifier = Modifier.width(120.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun LivroDiarioRow(entry: LivroDiarioEntry) {
    val tx    = entry.transaction
    val value = tx.paidAmount ?: tx.amount
    val payDate = (tx.paymentDate ?: tx.dueDate).format(dateFmt)
    val typeColor = when (tx.type) {
        TransactionType.INCOME, TransactionType.REVERSAL -> WsSuccess
        TransactionType.EXPENSE -> WsDanger
        else -> WsTextSecondary
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${entry.transaction.id}",
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.labelMedium,
            color = WsTextDisabled
        )
        Text(payDate, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall,
            color = WsTextSecondary)
        Text(
            entry.tcespDesc,
            modifier = Modifier.weight(3f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Box(modifier = Modifier.width(90.dp)) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = typeColor.copy(alpha = 0.1f)
            ) {
                Text(
                    tx.type.displayName,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColor,
                    maxLines = 1
                )
            }
        }
        Text(
            entry.accountName,
            modifier = Modifier.width(130.dp),
            style = MaterialTheme.typography.bodySmall,
            color = WsTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            MoneyFormatter.format(value),
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = typeColor,
            textAlign = TextAlign.End
        )
    }
}

// ── Balancete ─────────────────────────────────────────────────────────────────

@Composable
private fun BalanceteTab(state: ReportsUiState, viewModel: ReportsViewModel) {
    val filter = state.balanceteFilter
    var yearStr by remember(filter.year) { mutableStateOf(filter.year.toString()) }

    val monthOptions: List<Pair<Int, String>> = (1..12).map { it to MESES_PT[it - 1] }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Filter bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = WsSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, WsBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    WsTextField("ANO", yearStr, modifier = Modifier.width(100.dp)) { yearStr = it }
                    Box(modifier = Modifier.width(160.dp)) {
                        WsSelectField(
                            label = "MÊS (opcional)",
                            options = monthOptions,
                            selectedId = filter.month,
                            onSelect = {
                                val year = yearStr.toIntOrNull() ?: filter.year
                                viewModel.applyBalanceteFilter(filter.copy(year = year, month = it))
                            },
                            nullable = true,
                            placeholder = "Acumulado anual"
                        )
                    }
                    WsButton("Filtrar", icon = Icons.Default.Search, onClick = {
                        val year = yearStr.toIntOrNull() ?: filter.year
                        viewModel.applyBalanceteFilter(filter.copy(year = year))
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WsOutlinedButton(
                        onClick = { viewModel.exportBalancetePdf() },
                        enabled = state.balanceteRows.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exportar PDF")
                    }
                    WsOutlinedButton(
                        onClick = { viewModel.exportBalanceteExcel() },
                        enabled = state.balanceteRows.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.TableChart, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exportar Excel")
                    }
                    Spacer(Modifier.weight(1f))
                    val periodLabel = filter.month?.let { MESES_PT[it - 1] } ?: "Acumulado ${filter.year}"
                    Text(
                        periodLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = WsTextSecondary,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }

        // Table
        if (state.isLoading) {
            WsLoaderFullscreen()
        } else if (state.balanceteRows.isEmpty()) {
            EmptyState("Nenhuma dotação encontrada para o período. Cadastre itens de orçamento em Orçamento.")
        } else {
            BalanceteTable(state.balanceteRows, filter)
        }
    }
}

@Composable
private fun BalanceteTable(rows: List<BalanceteRow>, filter: BalanceteFilter) {
    var totMonthly = Money.ZERO; var totAnnual  = Money.ZERO
    var totReal    = Money.ZERO; var totBalance = Money.ZERO
    rows.forEach { r ->
        totMonthly += r.monthlyAmount; totAnnual  += r.annualAmount
        totReal    += r.realized;      totBalance += r.balance
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                TableHeaderCell("CENTRO DE CUSTO", Modifier.weight(1.5f))
                TableHeaderCell("CATEGORIA",        Modifier.weight(1.8f))
                TableHeaderCell("DOT. MÊS",         Modifier.width(110.dp), TextAlign.End)
                TableHeaderCell("DOT. ANUAL",        Modifier.width(110.dp), TextAlign.End)
                TableHeaderCell("REALIZADO",          Modifier.width(110.dp), TextAlign.End)
                TableHeaderCell("SALDO",              Modifier.width(110.dp), TextAlign.End)
                TableHeaderCell("% UTIL",             Modifier.width(70.dp),  TextAlign.Center)
            }
            HorizontalDivider(color = WsBorder)

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(rows) { row ->
                    BalanceteRow(row)
                    HorizontalDivider(color = WsBorder.copy(alpha = 0.35f))
                }
            }

            // Footer totals
            HorizontalDivider(color = WsBorder)
            Row(
                modifier = Modifier.fillMaxWidth().background(WsElevated)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TOTAL", modifier = Modifier.weight(1.5f),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.weight(1.8f))
                MoneyCell(totMonthly, bold = true, width = 110)
                MoneyCell(totAnnual,  bold = true, width = 110)
                MoneyCell(totReal,    bold = true, width = 110)
                MoneyCell(totBalance, bold = true, width = 110, colored = true)
                Spacer(Modifier.width(70.dp))
            }
        }
    }
}

@Composable
private fun BalanceteRow(row: BalanceteRow) {
    val pctColor = when {
        row.utilizationPct > 100 -> WsDanger
        row.utilizationPct > 85  -> WsWarning
        else -> WsTextSecondary
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.5f)) {
            Text(row.costCenterName, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.costCenterCode.isNotBlank())
                Text(row.costCenterCode, style = MaterialTheme.typography.labelSmall, color = WsAccent)
        }
        Column(modifier = Modifier.weight(1.8f)) {
            Text(row.categoryName, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.categoryCode.isNotBlank())
                Text(row.categoryCode, style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
        }
        MoneyCell(row.monthlyAmount, width = 110)
        MoneyCell(row.annualAmount,  width = 110)
        MoneyCell(row.realized,      width = 110)
        MoneyCell(row.balance,       width = 110, colored = true)

        // % bar
        Box(modifier = Modifier.width(70.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%.1f%%".format(row.utilizationPct),
                    style = MaterialTheme.typography.labelSmall, color = pctColor)
                LinearProgressIndicator(
                    progress = { (row.utilizationPct / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = pctColor,
                    trackColor = WsBorder
                )
            }
        }
    }
}

@Composable
private fun MoneyCell(value: Money, width: Int, bold: Boolean = false, colored: Boolean = false) {
    val color = if (colored) {
        if (value.isNegative()) WsDanger else if (value.isZero()) WsTextSecondary else WsSuccess
    } else WsTextPrimary
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Text(
        MoneyFormatter.format(value),
        modifier = Modifier.width(width.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = weight),
        color = color,
        textAlign = TextAlign.End
    )
}

// ── Demonstrativo Financeiro ──────────────────────────────────────────────────

@Composable
private fun DemonstrativoTab(state: ReportsUiState, viewModel: ReportsViewModel) {
    val filter = state.demonstrativoFilter
    var fromStr by remember(filter.from) { mutableStateOf(filter.from.format(dateFmt)) }
    var toStr   by remember(filter.to)   { mutableStateOf(filter.to.format(dateFmt)) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = WsSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, WsBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    WsTextField("DE (DD/MM/AAAA)", fromStr, modifier = Modifier.weight(1f)) { fromStr = it }
                    WsTextField("ATÉ (DD/MM/AAAA)", toStr, modifier = Modifier.weight(1f)) { toStr = it }
                    WsButton("Filtrar", icon = Icons.Default.Search, onClick = {
                        val from = parseDate(fromStr) ?: filter.from
                        val to   = parseDate(toStr)   ?: filter.to
                        viewModel.applyDemonstrativoFilter(filter.copy(from = from, to = to))
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WsOutlinedButton(
                        onClick = { viewModel.exportDemonstrativoPdf() },
                        enabled = state.demonstrativoRows.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exportar PDF")
                    }
                    WsOutlinedButton(
                        onClick = { viewModel.exportDemonstrativoExcel() },
                        enabled = state.demonstrativoRows.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.TableChart, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exportar Excel")
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.demonstrativoRows.isNotEmpty()) {
                        val totIncome  = state.demonstrativoRows.fold(Money.ZERO) { a, r -> a + r.income }
                        val totExpense = state.demonstrativoRows.fold(Money.ZERO) { a, r -> a + r.expense }
                        Text(
                            "Receita: ${MoneyFormatter.format(totIncome)}  |  Despesa: ${MoneyFormatter.format(totExpense)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = WsTextSecondary,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }

        if (state.isLoading) {
            WsLoaderFullscreen()
        } else if (state.demonstrativoRows.isEmpty()) {
            EmptyState("Nenhum lançamento liquidado encontrado para o período selecionado.")
        } else {
            DemonstrativoTable(state.demonstrativoRows)
        }
    }
}

@Composable
private fun DemonstrativoTable(rows: List<DemonstrativoRow>) {
    val grouped = rows.groupBy { it.groupCode to it.groupName }
    val totIncome  = rows.fold(Money.ZERO) { a, r -> a + r.income }
    val totExpense = rows.fold(Money.ZERO) { a, r -> a + r.expense }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
            .background(WsSurface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().background(WsElevated)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell("CÓD. AUDESP", Modifier.width(100.dp))
                TableHeaderCell("CATEGORIA",   Modifier.weight(2f))
                TableHeaderCell("GRUPO",       Modifier.weight(1.2f))
                TableHeaderCell("RECEITA",     Modifier.width(120.dp), TextAlign.End)
                TableHeaderCell("DESPESA",     Modifier.width(120.dp), TextAlign.End)
                TableHeaderCell("SALDO",       Modifier.width(120.dp), TextAlign.End)
            }
            HorizontalDivider(color = WsBorder)

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                grouped.forEach { (grpKey, grpRows) ->
                    val (grpCode, grpName) = grpKey
                    val grpLabel = listOfNotNull(grpCode, grpName).joinToString(" — ").ifBlank { "Sem grupo" }
                    val subIncome  = grpRows.fold(Money.ZERO) { a, r -> a + r.income }
                    val subExpense = grpRows.fold(Money.ZERO) { a, r -> a + r.expense }

                    // Group header
                    item(key = "grp_${grpCode}_${grpName}") {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(WsAccent.copy(alpha = 0.08f))
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                grpLabel,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = WsAccent
                            )
                        }
                    }

                    // Category rows
                    items(grpRows, key = { it.categoryId }) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                row.categoryCode,
                                modifier = Modifier.width(100.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = WsTextSecondary
                            )
                            Text(
                                row.categoryName,
                                modifier = Modifier.weight(2f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                grpLabel,
                                modifier = Modifier.weight(1.2f),
                                style = MaterialTheme.typography.bodySmall,
                                color = WsTextDisabled,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            MoneyCell(row.income,   width = 120, colored = false)
                            MoneyCell(row.expense,  width = 120, colored = false)
                            MoneyCell(row.balance,  width = 120, colored = true)
                        }
                        HorizontalDivider(color = WsBorder.copy(alpha = 0.25f))
                    }

                    // Group subtotal
                    item(key = "sub_${grpCode}_${grpName}") {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(WsElevated)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Subtotal $grpLabel",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            MoneyCell(subIncome,            width = 120, bold = true)
                            MoneyCell(subExpense,           width = 120, bold = true)
                            MoneyCell(subIncome - subExpense, width = 120, bold = true, colored = true)
                        }
                        HorizontalDivider(color = WsBorder)
                    }
                }
            }

            // Footer total
            HorizontalDivider(color = WsBorder)
            Row(
                modifier = Modifier.fillMaxWidth().background(WsElevated)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TOTAL GERAL",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                MoneyCell(totIncome,             width = 120, bold = true)
                MoneyCell(totExpense,            width = 120, bold = true)
                MoneyCell(totIncome - totExpense, width = 120, bold = true, colored = true)
            }
        }
    }
}

private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim(), dateFmt) }.getOrNull()
