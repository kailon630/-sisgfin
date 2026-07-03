package br.com.sisgfin.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.*
import br.com.sisgfin.core.ui.notifications.CrudEventEffects
import br.com.sisgfin.WsOutlinedButton
import br.com.sisgfin.core.ui.panel.BaseCrudPanel
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.money.toMoney
import java.time.LocalDate

@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState       by viewModel.uiState.collectAsState()
    val summaries     by viewModel.summaries.collectAsState()
    val costCenters by viewModel.costCenters.collectAsState()
    val selectedYear  by viewModel.selectedYear.collectAsState()
    val isLoading     by viewModel.isLoadingSummaries.collectAsState()
    CrudEventEffects(viewModel)

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Orçamento por Rubrica", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Dotação × realizado por projeto e categoria",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                YearSelector(selectedYear) { viewModel.selectYear(it) }
                WsButton("Nova Dotação", icon = Icons.Default.Add) {
                    viewModel.openNew()
                    onShowRightPanel { BudgetItemPanel(viewModel, onCloseRightPanel) }
                }
                WsIconButton(Icons.Default.Refresh) { viewModel.load() }
            }
        }

        // Totalizadores
        if (summaries.isNotEmpty()) {
            val totalBudget   = summaries.fold(Money.ZERO) { a, s -> a + s.item.annualAmount }
            val totalRealized = summaries.fold(Money.ZERO) { a, s -> a + s.annualRealized }
            val totalBalance  = totalBudget - totalRealized
            val overallPct    = if (totalBudget.isZero()) 0.0
                                else totalRealized.value.toDouble() / totalBudget.value.toDouble() * 100.0

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BudgetTile("Total Dotado",    MoneyFormatter.format(totalBudget),   WsAccent,   Modifier.weight(1f))
                BudgetTile("Total Realizado", MoneyFormatter.format(totalRealized), WsWarning,  Modifier.weight(1f))
                BudgetTile(
                    "Saldo Disponível",
                    MoneyFormatter.format(totalBalance),
                    if (totalBalance.isNegative()) WsDanger else WsSuccess,
                    Modifier.weight(1f)
                )
                BudgetTile(
                    "% Utilizado",
                    "${"%.1f".format(overallPct)}%",
                    utilColor(overallPct),
                    Modifier.weight(1f)
                )
            }
        }

        // Tabela
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WsAccent)
            }
        } else if (summaries.isEmpty()) {
            EmptyState("Nenhuma dotação cadastrada para $selectedYear.")
        } else {
            BudgetTable(
                summaries = summaries,
                costCenters = costCenters,
                onEdit = { item ->
                    viewModel.select(item)
                    onShowRightPanel { BudgetItemPanel(viewModel, onCloseRightPanel) }
                },
                onToggle = { viewModel.toggleActive(it.id) }
            )
        }

        if (uiState.isDialogVisible) {
            // diálogo usa o panel lateral
        }
    }
}

@Composable
private fun YearSelector(year: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val years = (LocalDate.now().year + 1 downTo 2020).toList()
    Box {
        WsOutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(36.dp)
        ) {
            Text("$year", style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = WsElevated) {
            years.forEach { y ->
                DropdownMenuItem(
                    text = { Text("$y", color = WsTextPrimary) },
                    onClick = { onSelect(y); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun BudgetTile(label: String, value: String, color: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
            .background(WsSurface)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
            Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp), color = color)
        }
    }
}

@Composable
private fun BudgetTable(
    summaries: List<BudgetItemSummary>,
    costCenters: List<br.com.sisgfin.CostCenter>,
    onEdit: (BudgetItem) -> Unit,
    onToggle: (BudgetItem) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
            .background(WsSurface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth().background(WsElevated).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell("CENTRO DE CUSTO",        Modifier.weight(1.6f))
                TableHeaderCell("CATEGORIA",      Modifier.weight(1.8f))
                TableHeaderCell("DOT. MENSAL",    Modifier.width(110.dp), TextAlign.End)
                TableHeaderCell("DOT. ANUAL",     Modifier.width(110.dp), TextAlign.End)
                TableHeaderCell("REALIZADO",      Modifier.width(110.dp), TextAlign.End)
                TableHeaderCell("SALDO",          Modifier.width(110.dp), TextAlign.End)
                TableHeaderCell("% UTIL",         Modifier.width(72.dp),  TextAlign.Center)
                TableHeaderCell("",               Modifier.width(60.dp))
            }
            HorizontalDivider(color = WsBorder)

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(summaries) { summary ->
                    val isEncerrado = costCenters.find { it.id == summary.item.costCenterId }?.isEncerrado == true
                    BudgetRow(
                        summary     = summary,
                        isEncerrado = isEncerrado,
                        onEdit      = { onEdit(summary.item) },
                        onToggle    = { onToggle(summary.item) }
                    )
                    HorizontalDivider(color = WsBorder.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(
    summary: BudgetItemSummary,
    isEncerrado: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit
) {
    val pct = summary.utilizationPct
    val pctColor = utilColor(pct)
    val balanceColor = if (summary.isOverBudget) WsDanger
                       else if (summary.annualBalance.isZero()) WsTextSecondary
                       else WsSuccess

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(summary.costCenterName, modifier = Modifier.weight(1.6f), style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)

        Column(modifier = Modifier.weight(1.8f)) {
            Text(summary.categoryName, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary.categoryCode, style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
        }

        Text(MoneyFormatter.format(summary.item.monthlyAmount), modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)

        Text(MoneyFormatter.format(summary.item.annualAmount), modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)

        Text(MoneyFormatter.format(summary.annualRealized), modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = WsWarning, textAlign = TextAlign.End)

        Text(MoneyFormatter.format(summary.annualBalance), modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = balanceColor, textAlign = TextAlign.End)

        // Barra de % utilização
        Column(modifier = Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${"%.0f".format(pct)}%", style = MaterialTheme.typography.labelMedium, color = pctColor)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier.fillMaxWidth(0.8f).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(WsBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(minOf(pct / 100.0, 1.0).toFloat())
                        .fillMaxHeight()
                        .background(pctColor)
                )
            }
        }

        // Ações — RN-28: cadeado para projetos encerrados
        Row(modifier = Modifier.width(60.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isEncerrado) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Projeto encerrado",
                    tint = WsTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                WsIconButton(Icons.Default.Edit) { onEdit() }
            }
        }
    }
}

@Composable
fun BudgetItemPanel(viewModel: BudgetViewModel, onClose: () -> Unit) {
    val uiState    by viewModel.uiState.collectAsState()
    val costCenters by viewModel.costCenters.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val item = uiState.selectedItem ?: return

    var costCenterId     by remember(item.id) { mutableStateOf(item.costCenterId.takeIf { it > 0 }) }
    var categoryId    by remember(item.id) { mutableStateOf(item.categoryId.takeIf { it > 0 }) }
    var year          by remember(item.id) { mutableStateOf(item.year.toString()) }
    var monthlyStr    by remember(item.id) { mutableStateOf(if (item.monthlyAmount.isZero()) "" else item.monthlyAmount.toString()) }
    var annualStr     by remember(item.id) { mutableStateOf(if (item.annualAmount.isZero()) "" else item.annualAmount.toString()) }
    var notes         by remember(item.id) { mutableStateOf(item.notes ?: "") }
    var annualManual  by remember(item.id) { mutableStateOf(false) }

    // RN-28: verifica se o projeto selecionado está encerrado
    val selectedCostCenter = costCenters.find { it.id == costCenterId }
    val isEncerrado = selectedCostCenter?.isEncerrado == true

    val costCenterOptions = costCenters.map { it.id to it.name }
    val categoryOptions = categories.map { it.id to it.displayName }

    BaseCrudPanel(
        title    = if (item.id == 0) "Nova Dotação" else "Editar Dotação",
        subtitle = if (item.id == 0) "Vincular projeto + categoria com valor orçado" else "${item.year}",
        onClose  = onClose,
        isLoading    = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        // RN-28: onSave = null torna o painel somente leitura para projetos encerrados
        onSave = if (isEncerrado) null else {
            {
                val monthly = monthlyStr.toMoney()
                val annual  = if (annualStr.isBlank()) monthly * java.math.BigDecimal(12) else annualStr.toMoney()
                viewModel.save(
                    item.copy(
                        costCenterId  = costCenterId ?: 0,
                        categoryId    = categoryId ?: 0,
                        year          = year.toIntOrNull() ?: LocalDate.now().year,
                        monthlyAmount = monthly,
                        annualAmount  = annual,
                        notes         = notes.ifBlank { null }
                    )
                )
            }
        }
    ) {
        // RN-28: banner de aviso para projeto encerrado
        if (isEncerrado) {
            EncerradoBanner(selectedCostCenter!!.name)
        }

        DetailSection("Vínculo") {
            WsSelectField(
                "CENTRO DE CUSTO", costCenterOptions, costCenterId,
                onSelect = { costCenterId = it },
                nullable = false,
                placeholder = "Selecionar centro de custo...",
                enabled = !isEncerrado
            )
            WsSelectField(
                "CATEGORIA", categoryOptions, categoryId,
                onSelect = { categoryId = it },
                nullable = false,
                placeholder = "Selecionar categoria...",
                enabled = !isEncerrado
            )
            WsTextField("ANO", year, enabled = !isEncerrado) { year = it }
        }

        DetailSection("Dotação") {
            WsTextField("DOTAÇÃO MENSAL (R$)", monthlyStr, enabled = !isEncerrado) { v ->
                monthlyStr = v
                if (!annualManual) {
                    val m = v.toBigDecimalOrNull()
                    if (m != null) annualStr = (m * java.math.BigDecimal(12)).toPlainString()
                }
            }
            WsTextField("DOTAÇÃO ANUAL (R$)", annualStr, enabled = !isEncerrado) { v ->
                annualStr = v
                annualManual = v.isNotBlank()
            }
            if (!isEncerrado) {
                Text(
                    "Se o anual não for preenchido, será calculado como mensal × 12.",
                    style = MaterialTheme.typography.labelMedium,
                    color = WsTextSecondary
                )
            }
        }

        DetailSection("Observações") {
            WsTextField("NOTAS", notes, enabled = !isEncerrado) { notes = it }
        }

        // Mostra realizado se editando
        if (item.id != 0) {
            val summaries by viewModel.summaries.collectAsState()
            val summary = summaries.find { it.item.id == item.id }
            if (summary != null) {
                DetailSection("Realizado (RN-24)") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Lançamentos PAID no ano", style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
                        Text(MoneyFormatter.format(summary.annualRealized), style = MaterialTheme.typography.bodyLarge,
                            color = WsWarning, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Saldo disponível", style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
                        Text(
                            MoneyFormatter.format(summary.annualBalance),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (summary.isOverBudget) WsDanger else WsSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("% Utilizado", style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
                        Text(
                            "${"%.1f".format(summary.utilizationPct)}%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = utilColor(summary.utilizationPct),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// Banner vermelho para projeto encerrado (RN-28)
@Composable
private fun EncerradoBanner(costCenterName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(WsDanger.copy(alpha = 0.08f))
            .border(1.dp, WsDanger.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Lock, null, tint = WsDanger, modifier = Modifier.size(16.dp))
        Column {
            Text(
                "Projeto encerrado — somente leitura",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = WsDanger
            )
            Text(
                "\"$costCenterName\" está encerrado. O orçamento não pode ser alterado.",
                style = MaterialTheme.typography.labelSmall,
                color = WsDanger.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun utilColor(pct: Double) = when {
    pct >= 100.0 -> WsDanger
    pct >= 80.0  -> WsWarning
    else         -> WsSuccess
}
