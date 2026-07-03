package br.com.sisgfin.cashflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.*
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dateFmt  = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val inputFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val ptBR     = Locale("pt", "BR")

// Âmbar para entradas simuladas
private val WsSimulation = Color(0xFFF59E0B)

@Composable
fun CashFlowScreen(viewModel: CashFlowViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // ── Conteúdo principal ────────────────────────────────────────────────
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Fluxo de Caixa Projetado", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Compromissos pendentes e projeção de saldo — próximos ${uiState.windowDays} dias",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WsTextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Botão Simular
                    val simActive = uiState.isSimulationPanelOpen || uiState.isSimulating
                    Surface(
                        onClick = {
                            if (uiState.isSimulationPanelOpen) viewModel.closeSimulationPanel()
                            else viewModel.openSimulationPanel()
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = if (simActive) WsSimulation.copy(alpha = 0.15f) else WsSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            if (simActive) 1.5.dp else 1.dp,
                            if (simActive) WsSimulation else WsBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Science, null,
                                tint = if (simActive) WsSimulation else WsTextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                if (uiState.isSimulating) "Simular (${uiState.simulationEntries.size})" else "Simular",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (simActive) WsSimulation else WsTextSecondary
                            )
                        }
                    }
                    WsIconButton(Icons.Default.Refresh) { viewModel.load() }
                }
            }

            // Filtros
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7, 14, 30).forEach { days ->
                        val sel = uiState.windowDays == days
                        Surface(
                            onClick = { viewModel.setWindowDays(days) },
                            shape = RoundedCornerShape(6.dp),
                            color = if (sel) WsAccent.copy(alpha = 0.15f) else WsSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) WsAccent else WsBorder)
                        ) {
                            Text(
                                "$days dias",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sel) WsAccent else WsTextSecondary
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                AccountSelector(uiState.accounts, uiState.selectedAccountId) { viewModel.setAccount(it) }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WsAccent, strokeWidth = 2.dp)
                }
                return@Column
            }

            uiState.errorMessage?.let { err ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = WsDanger, modifier = Modifier.size(32.dp))
                        Text(err, color = WsDanger)
                        WsButton("Tentar novamente", icon = Icons.Default.Refresh) { viewModel.load() }
                    }
                }
                return@Column
            }

            // Tiles de resumo
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CashFlowTile(
                    label = "SALDO ATUAL",
                    value = MoneyFormatter.format(uiState.currentBalance),
                    icon = Icons.Outlined.AccountBalance,
                    color = if (uiState.currentBalance.isNegative()) WsDanger else WsAccent,
                    modifier = Modifier.weight(1f)
                )
                CashFlowTile(
                    label = "TOTAL COMPROMETIDO",
                    value = MoneyFormatter.format(uiState.totalCommitted),
                    sub = buildString {
                        append("${uiState.entries.count { !it.isSimulated }} dia(s) com saídas")
                        if (uiState.overdueTransactions.isNotEmpty())
                            append(" + ${uiState.overdueTransactions.size} atrasado(s)")
                        if (uiState.isSimulating)
                            append(" + ${uiState.simulationEntries.size} simulado(s)")
                    },
                    icon = Icons.Outlined.PendingActions,
                    color = WsWarning,
                    modifier = Modifier.weight(1f)
                )
                CashFlowTile(
                    label = "SALDO PROJETADO",
                    value = MoneyFormatter.format(uiState.projectedFinalBalance),
                    sub = "ao final de ${uiState.windowDays} dias",
                    icon = Icons.AutoMirrored.Outlined.TrendingDown,
                    color = if (uiState.projectedFinalBalance.isNegative()) WsDanger else WsSuccess,
                    alert = uiState.projectedFinalBalance.isNegative(),
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(
                    currentBalance = uiState.currentBalance,
                    totalCommitted = uiState.totalCommitted,
                    modifier = Modifier.weight(1f)
                )
            }

            // Badge de impacto da simulação
            if (uiState.isSimulating) {
                SimulationImpactBadge(
                    baseBalance = uiState.baseProjectedBalance,
                    simBalance  = uiState.projectedFinalBalance,
                    delta       = uiState.simulationDelta,
                    windowDays  = uiState.windowDays
                )
            }

            // Atrasados
            if (uiState.overdueTransactions.isNotEmpty()) {
                OverdueAlert(uiState.overdueTransactions, uiState.overdueTotal)
            }

            // Tabela
            if (uiState.entries.isEmpty() && uiState.overdueTransactions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.CheckCircleOutline, null, tint = WsSuccess, modifier = Modifier.size(40.dp))
                        Text("Nenhum compromisso nos próximos ${uiState.windowDays} dias", color = WsTextSecondary)
                    }
                }
            } else {
                CashFlowTable(uiState.entries)
            }
        }

        // ── Painel lateral de simulação ───────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.isSimulationPanelOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit  = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            Row(modifier = Modifier.width(321.dp).fillMaxHeight()) {
                VerticalDivider(color = WsBorder, thickness = 1.dp)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WsSurface)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SimulationPanel(
                        uiState   = uiState,
                        onAdd     = { viewModel.addSimulation(it) },
                        onRemove  = { viewModel.removeSimulation(it) },
                        onClear   = { viewModel.clearSimulations() },
                        onCommit  = { idx, ok, err -> viewModel.commitSimulation(idx, ok, err) },
                        onClose   = { viewModel.closeSimulationPanel() }
                    )
                }
            }
        }
    }
}

// ── Seletor de conta ──────────────────────────────────────────────────────────

@Composable
private fun AccountSelector(
    accounts: List<br.com.sisgfin.FinancialAccount>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = accounts.find { it.id == selectedId }?.name ?: "Todas as contas"
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(6.dp),
            color = WsSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, WsBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.AccountBalance, null, tint = WsTextSecondary, modifier = Modifier.size(14.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
                Icon(Icons.Outlined.ArrowDropDown, null, tint = WsTextSecondary, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = WsElevated) {
            DropdownMenuItem(
                text = { Text("Todas as contas", style = MaterialTheme.typography.bodyMedium, color = WsTextPrimary) },
                onClick = { onSelect(null); expanded = false }
            )
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = { Text(acc.name, style = MaterialTheme.typography.bodyMedium, color = WsTextPrimary) },
                    onClick = { onSelect(acc.id); expanded = false }
                )
            }
        }
    }
}

// ── Tiles ─────────────────────────────────────────────────────────────────────

@Composable
private fun CashFlowTile(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    sub: String = "",
    alert: Boolean = false
) {
    Surface(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (alert) WsDanger.copy(alpha = 0.05f) else WsSurface,
        border = androidx.compose.foundation.BorderStroke(if (alert) 1.5.dp else 1.dp, if (alert) WsDanger else WsBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
                Icon(icon, null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            }
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp))
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
        }
    }
}

@Composable
private fun StatusBadge(currentBalance: Money, totalCommitted: Money, modifier: Modifier = Modifier) {
    val isOk  = currentBalance.value >= totalCommitted.value
    val color = if (isOk) WsSuccess else WsDanger
    val icon  = if (isOk) Icons.Outlined.CheckCircleOutline else Icons.Outlined.Warning
    Surface(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.07f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, color)
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                if (isOk) "Saldo suficiente" else "Saldo insuficiente",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = color,
                textAlign = TextAlign.Center
            )
            Text("para cobrir os compromissos", style = MaterialTheme.typography.labelSmall, color = WsTextSecondary, textAlign = TextAlign.Center)
        }
    }
}

// ── Badge de impacto da simulação ─────────────────────────────────────────────

@Composable
private fun SimulationImpactBadge(
    baseBalance: Money,
    simBalance:  Money,
    delta:       Money,
    windowDays:  Int
) {
    val isWorse = delta.isNegative()
    val color   = if (isWorse) WsDanger else WsSuccess
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.5.dp, WsSimulation.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .background(WsSimulation.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.Science, null, tint = WsSimulation, modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Simulação ativa",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = WsSimulation
            )
            Text(
                "Com esses compromissos, seu saldo projetado a $windowDays dias passa de " +
                "${MoneyFormatter.format(baseBalance)} para ${MoneyFormatter.format(simBalance)}",
                style = MaterialTheme.typography.labelSmall,
                color = WsTextSecondary
            )
        }
        Text(
            (if (isWorse) "−" else "+") + MoneyFormatter.format(Money(delta.value.abs())),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

// ── Painel de simulação ───────────────────────────────────────────────────────

@Composable
private fun SimulationPanel(
    uiState:  CashFlowUiState,
    onAdd:    (SimulationEntry) -> Unit,
    onRemove: (Int) -> Unit,
    onClear:  () -> Unit,
    onCommit: (Int, () -> Unit, (String) -> Unit) -> Unit,
    onClose:  () -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amountStr   by remember { mutableStateOf("") }
    var dateStr     by remember { mutableStateOf("") }
    var accountId   by remember { mutableStateOf<Int?>(null) }
    var commitMsg   by remember { mutableStateOf<String?>(null) }
    var commitError by remember { mutableStateOf<String?>(null) }

    val parsedDate: LocalDate? = runCatching { LocalDate.parse(dateStr.trim(), inputFmt) }.getOrNull()
    val parsedAmount: Money? = runCatching { Money(BigDecimal(amountStr.trim().replace(",", "."))) }
        .getOrNull()?.takeIf { !it.isNegative() && !it.isZero() }
    val canAdd = description.isNotBlank() && parsedAmount != null && parsedDate != null

    // ── Cabeçalho do painel
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Science, null, tint = WsSimulation, modifier = Modifier.size(16.dp))
            Text(
                "Simular Compromisso",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = WsSimulation
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Close, null, tint = WsTextSecondary, modifier = Modifier.size(16.dp))
        }
    }

    Text(
        "Adicione compromissos hipotéticos para ver o impacto no fluxo — sem criar lançamentos reais.",
        style = MaterialTheme.typography.bodySmall,
        color = WsTextSecondary
    )

    HorizontalDivider(color = WsBorder)

    // ── Formulário
    WsTextField(
        label = "Descrição",
        value = description,
        onValueChange = { description = it }
    )

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WsTextField(
            label = "Valor (R$)",
            value = amountStr,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            onValueChange = { amountStr = it }
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vencimento", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
            OutlinedTextField(
                value = dateStr,
                onValueChange = { dateStr = it },
                placeholder = { Text("dd/MM/yyyy", style = MaterialTheme.typography.bodySmall, color = WsTextDisabled) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = if (dateStr.isNotEmpty() && parsedDate == null) WsDanger else WsAccent,
                    unfocusedBorderColor = if (dateStr.isNotEmpty() && parsedDate == null) WsDanger else WsBorder,
                    focusedContainerColor   = WsBackground,
                    unfocusedContainerColor = WsBackground,
                    focusedTextColor     = WsTextPrimary,
                    unfocusedTextColor   = WsTextPrimary,
                    cursorColor          = WsAccent
                )
            )
            if (dateStr.isNotEmpty() && parsedDate == null) {
                Text("Use dd/MM/yyyy", style = MaterialTheme.typography.labelSmall, color = WsDanger)
            }
        }
    }

    if (uiState.accounts.isNotEmpty()) {
        WsSelectField(
            label    = "Conta (opcional)",
            options  = uiState.accounts.map { it.id to it.name },
            selectedId = accountId,
            onSelect   = { accountId = it },
            placeholder = "Todas as contas",
            nullable    = true
        )
    }

    WsButton(
        label    = "Adicionar à simulação",
        modifier = Modifier.fillMaxWidth(),
        icon     = Icons.Outlined.Add
    ) {
        if (canAdd) {
            onAdd(SimulationEntry(description.trim(), parsedAmount!!, parsedDate!!, accountId))
            description = ""; amountStr = ""; dateStr = ""
        }
    }

    // ── Lista de simulações ativas
    if (uiState.simulationEntries.isNotEmpty()) {
        HorizontalDivider(color = WsBorder)
        Text(
            "Compromissos na simulação",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = WsSimulation
        )

        commitMsg?.let { msg ->
            Text(msg, style = MaterialTheme.typography.labelSmall, color = WsSuccess)
        }
        commitError?.let { err ->
            Text(err, style = MaterialTheme.typography.labelSmall, color = WsDanger)
        }

        LazyColumn(
            modifier = Modifier.heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(uiState.simulationEntries) { idx, entry ->
                SimulationEntryCard(
                    entry    = entry,
                    accounts = uiState.accounts,
                    onRemove = { onRemove(idx) },
                    onCommit = {
                        commitMsg = null; commitError = null
                        onCommit(idx,
                            { commitMsg = "Lançamento criado com sucesso!" },
                            { e -> commitError = e }
                        )
                    }
                )
            }
        }

        if (uiState.simulationEntries.size > 1) {
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar todas as simulações", color = WsTextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SimulationEntryCard(
    entry:    SimulationEntry,
    accounts: List<br.com.sisgfin.FinancialAccount>,
    onRemove: () -> Unit,
    onCommit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, WsSimulation.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .background(WsSimulation.copy(alpha = 0.06f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.description, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${entry.dueDate.format(inputFmt)} · ${accounts.find { it.id == entry.accountId }?.name ?: "Todas"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WsTextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(MoneyFormatter.format(entry.amount), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = WsSimulation)
                IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Outlined.Close, null, tint = WsTextDisabled, modifier = Modifier.size(13.dp))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = onCommit,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Outlined.AddTask, null, modifier = Modifier.size(13.dp), tint = WsAccent)
                Spacer(Modifier.width(4.dp))
                Text("Criar lançamento real", style = MaterialTheme.typography.labelSmall, color = WsAccent)
            }
        }
    }
}

// ── Atrasados ─────────────────────────────────────────────────────────────────

@Composable
private fun OverdueAlert(transactions: List<Transaction>, total: Money) {
    var expanded by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.5.dp, WsDanger.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .background(WsDanger.copy(alpha = 0.05f))
    ) {
        Surface(onClick = { expanded = !expanded }, color = Color.Transparent) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.Warning, null, tint = WsDanger, modifier = Modifier.size(16.dp))
                Text(
                    "ATRASADOS — ${transactions.size} lançamento${if (transactions.size > 1) "s" else ""} · ${MoneyFormatter.format(total)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = WsDanger,
                    modifier = Modifier.weight(1f)
                )
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = WsDanger, modifier = Modifier.size(16.dp))
            }
        }
        if (expanded) {
            HorizontalDivider(color = WsDanger.copy(alpha = 0.2f))
            transactions.forEach { tx ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(6.dp).background(WsDanger, RoundedCornerShape(3.dp)))
                    Text(tx.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("venceu ${tx.dueDate.format(dateFmt)}", style = MaterialTheme.typography.labelSmall, color = WsDanger)
                    Text(MoneyFormatter.format(tx.paidAmount ?: tx.amount), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = WsDanger)
                }
                HorizontalDivider(color = WsDanger.copy(alpha = 0.1f))
            }
        }
    }
}

// ── Tabela de fluxo ───────────────────────────────────────────────────────────

@Composable
private fun CashFlowTable(entries: List<DailyCashFlowEntry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
            .background(WsSurface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(WsElevated).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableHeaderCell("DATA / DIA",      Modifier.width(120.dp))
            TableHeaderCell("COMPROMISSOS",    Modifier.weight(1f))
            TableHeaderCell("SAÍDAS",          Modifier.width(120.dp), TextAlign.End)
            TableHeaderCell("ENTRADAS",        Modifier.width(100.dp), TextAlign.End)
            TableHeaderCell("SALDO PROJETADO", Modifier.width(130.dp), TextAlign.End)
        }
        HorizontalDivider(color = WsBorder)
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(entries, key = { "${it.date}_${it.isSimulated}_${it.simulationLabel}" }) { entry ->
                if (entry.isSimulated) SimulatedRow(entry) else CashFlowRow(entry)
                HorizontalDivider(color = WsBorder.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun SimulatedRow(entry: DailyCashFlowEntry) {
    Row(modifier = Modifier.fillMaxWidth().background(WsSimulation.copy(alpha = 0.06f)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(48.dp).background(WsSimulation))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(120.dp)) {
                val dayName = entry.date.dayOfWeek.getDisplayName(TextStyle.SHORT, ptBR).replaceFirstChar { it.uppercase() }
                Text(dayName, style = MaterialTheme.typography.labelSmall, color = WsSimulation)
                Text(entry.date.format(dateFmt), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = WsSimulation)
            }
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Science, null, tint = WsSimulation, modifier = Modifier.size(12.dp))
                Text(
                    entry.simulationLabel ?: "Simulação",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = WsSimulation,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(MoneyFormatter.format(entry.totalOutflow), modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = WsSimulation, textAlign = TextAlign.End)
            Text("—", modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodyMedium, color = WsTextDisabled, textAlign = TextAlign.End)
            Text(
                MoneyFormatter.format(entry.projectedBalance),
                modifier = Modifier.width(130.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (entry.projectedBalance.isNegative()) WsDanger else WsSuccess,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun CashFlowRow(entry: DailyCashFlowEntry) {
    val isNegative = entry.projectedBalance.isNegative()
    val isToday    = entry.date == LocalDate.now()
    val rowBg      = when { isNegative -> WsDanger.copy(alpha = 0.06f); isToday -> WsAccent.copy(alpha = 0.04f); else -> Color.Transparent }
    val sideColor  = when { isNegative -> WsDanger; entry.projectedBalance.value < BigDecimal("500") -> WsWarning; else -> WsSuccess }
    val balColor   = when { isNegative -> WsDanger; entry.projectedBalance.value < BigDecimal("500") -> WsWarning; else -> WsSuccess }

    Row(modifier = Modifier.fillMaxWidth().background(rowBg), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(56.dp).background(sideColor))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(120.dp)) {
                val dayName = entry.date.dayOfWeek.getDisplayName(TextStyle.SHORT, ptBR).replaceFirstChar { it.uppercase() }
                Text(dayName, style = MaterialTheme.typography.labelSmall, color = if (isToday) WsAccent else WsTextSecondary)
                Text(entry.date.format(dateFmt), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (isToday) WsAccent else WsTextPrimary)
                if (isToday) Text("hoje", style = MaterialTheme.typography.labelSmall, color = WsAccent)
            }
            Column(modifier = Modifier.weight(1f)) {
                val shown = (entry.transactions.filter { it.type == TransactionType.EXPENSE } + entry.transactions.filter { it.type == TransactionType.INCOME }).take(3)
                shown.forEach { tx ->
                    Text(tx.description, style = MaterialTheme.typography.bodySmall, color = if (tx.type == TransactionType.EXPENSE) WsTextPrimary else WsSuccess, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val hidden = entry.transactions.size - shown.size
                if (hidden > 0) Text("… e mais $hidden", style = MaterialTheme.typography.labelSmall, color = WsTextDisabled)
            }
            Text(if (entry.totalOutflow.isZero()) "—" else MoneyFormatter.format(entry.totalOutflow), modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (entry.totalOutflow.isZero()) WsTextDisabled else WsDanger, textAlign = TextAlign.End)
            Text(if (entry.totalInflow.isZero()) "—" else MoneyFormatter.format(entry.totalInflow), modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodyMedium, color = if (entry.totalInflow.isZero()) WsTextDisabled else WsSuccess, textAlign = TextAlign.End)
            Text(MoneyFormatter.format(entry.projectedBalance), modifier = Modifier.width(130.dp), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = balColor, textAlign = TextAlign.End)
        }
    }
}
