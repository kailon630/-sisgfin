package br.com.sisgfin.payroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.sisgfin.*
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.MoneyFormatter
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val monthFmt = DateTimeFormatter.ofPattern("MMM/yyyy", Locale.forLanguageTag("pt-BR"))

@Composable
fun PayrollImportScreen(
    viewModel: PayrollImportViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToEmployees: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Cabeçalho fixo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Importar Folha de Pagamento", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Importação via arquivo XLSX — SCI Ambiente Contábil",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WsTextSecondary
                )
            }
            WizardStepIndicator(uiState.step)
        }

        HorizontalDivider(color = WsBorder)

        when (uiState.step) {
            PayrollImportStep.SELECT_FILE ->
                SelectFileStep(uiState = uiState, viewModel = viewModel)

            PayrollImportStep.PARSING, PayrollImportStep.CONFIRMING ->
                LoadingStep(
                    message = if (uiState.step == PayrollImportStep.PARSING)
                        "Lendo arquivo e localizando funcionários…"
                    else
                        "Criando lançamentos…"
                )

            PayrollImportStep.PREVIEW ->
                PreviewStep(
                    uiState   = uiState,
                    onBack    = { viewModel.back() },
                    onToggle  = { viewModel.toggleEntry(it) },
                    onSelectAll   = { viewModel.selectAll() },
                    onDeselectAll = { viewModel.deselectAll() },
                    onConfirm = { viewModel.confirm() }
                )

            PayrollImportStep.DONE ->
                DoneStep(
                    summary                  = uiState.summary!!,
                    registeredCpfs           = uiState.registeredCpfs,
                    missingBankData          = uiState.missingBankData,
                    exportLoading            = uiState.exportLoading,
                    onRegister               = { viewModel.openRegisterDialog(it) },
                    onNewImport              = { viewModel.reset() },
                    onNavigateToTransactions = onNavigateToTransactions,
                    onExportAdiantamentos    = { viewModel.exportRemessa(TipoRemessa.ADIANTAMENTO) },
                    onExportLiquidos         = { viewModel.exportRemessa(TipoRemessa.LIQUIDO) },
                    onNavigateToEmployees    = onNavigateToEmployees
                )
        }

        uiState.registeringEntry?.let { entry ->
            RegisterMissingEmployeeDialog(
                entry     = entry,
                onDismiss = { viewModel.closeRegisterDialog() },
                onSave    = { name, cpf, role, salary, empType ->
                    viewModel.registerEmployee(name, cpf, role, salary, empType)
                }
            )
        }

        uiState.error?.let { err ->
            Surface(
                color = WsDanger.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = WsDanger, modifier = Modifier.size(16.dp))
                    Text(err, style = MaterialTheme.typography.bodySmall, color = WsDanger)
                }
            }
        }
    }
}

// ── Indicador de etapas ───────────────────────────────────────────────────────

@Composable
private fun WizardStepIndicator(step: PayrollImportStep) {
    val steps = listOf("Configurar", "Preview", "Concluído")
    val active = when (step) {
        PayrollImportStep.SELECT_FILE, PayrollImportStep.PARSING -> 0
        PayrollImportStep.PREVIEW, PayrollImportStep.CONFIRMING  -> 1
        PayrollImportStep.DONE                                    -> 2
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, label ->
            val done    = i < active
            val current = i == active
            val color   = when {
                done    -> WsSuccess
                current -> WsAccent
                else    -> WsTextDisabled
            }
            Surface(
                color = color.copy(alpha = if (current) 0.15f else if (done) 0.10f else 0.05f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (done) Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                    Text("${i + 1}. $label", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                }
            }
            if (i < steps.lastIndex) {
                Box(modifier = Modifier.width(16.dp).height(1.dp).background(WsBorder))
            }
        }
    }
}

// ── Etapa 1: Configurar ───────────────────────────────────────────────────────

@Composable
private fun SelectFileStep(
    uiState: PayrollImportUiState,
    viewModel: PayrollImportViewModel
) {
    var monthInput by remember(uiState.referenceMonth) {
        mutableStateOf(uiState.referenceMonth.let {
            "${it.monthValue.toString().padStart(2, '0')}/${it.year}"
        })
    }

    val accountOptions  = remember(uiState.accounts)    { uiState.accounts.map  { it.id to it.name } }
    val categoryOptions = remember(uiState.categories)  { uiState.categories.map { it.id to it.name } }
    val costCenterOptions = remember(uiState.costCenters) { uiState.costCenters.map { it.id to it.name } }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {

        // Coluna esquerda — formulário
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Parâmetros da importação", style = MaterialTheme.typography.titleMedium)

            WsTextField(
                label = "MÊS DE REFERÊNCIA (MM/AAAA)",
                value = monthInput,
                onValueChange = { v ->
                    monthInput = v
                    if (v.length == 7 && v[2] == '/') {
                        val m = v.substring(0, 2).toIntOrNull()
                        val y = v.substring(3).toIntOrNull()
                        if (m != null && y != null && m in 1..12 && y > 2000) {
                            viewModel.setReferenceMonth(YearMonth.of(y, m))
                        }
                    }
                }
            )

            WsSelectField(
                label       = "CONTA A DEBITAR",
                options     = accountOptions,
                selectedId  = uiState.selectedAccountId,
                onSelect    = { viewModel.setAccount(it) },
                placeholder = "Selecionar conta…",
                nullable    = false
            )

            WsSelectField(
                label       = "CATEGORIA (RUBRICA)",
                options     = categoryOptions,
                selectedId  = uiState.selectedCategoryId,
                onSelect    = { viewModel.setCategory(it) },
                placeholder = "Ex: Vencimentos e Salários…",
                nullable    = false
            )

            WsSelectField(
                label       = "CENTRO DE CUSTO",
                options     = costCenterOptions,
                selectedId  = uiState.selectedCostCenterId,
                onSelect    = { viewModel.setCostCenter(it) },
                placeholder = "Nenhum (opcional)",
                nullable    = true
            )

            HorizontalDivider(color = WsBorder)

            // Seleção de arquivo
            Text("Arquivo XLSX", style = MaterialTheme.typography.titleSmall)

            Surface(
                color  = WsElevated,
                shape  = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (uiState.selectedFile != null) WsSuccess.copy(alpha = 0.4f) else WsBorder, RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (uiState.selectedFile != null) Icons.Default.CheckCircle else Icons.Outlined.UploadFile,
                        contentDescription = null,
                        tint   = if (uiState.selectedFile != null) WsSuccess else WsTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        if (uiState.selectedFile != null) {
                            Text(uiState.selectedFile.name, style = MaterialTheme.typography.bodyMedium, color = WsTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(uiState.selectedFile.parent ?: "", style = MaterialTheme.typography.labelSmall, color = WsTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text("Nenhum arquivo selecionado", style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
                            Text("Formato: *.xlsx — SCI Ambiente Contábil ÚNICO", style = MaterialTheme.typography.labelSmall, color = WsTextDisabled)
                        }
                    }
                    WsButton(
                        text    = if (uiState.selectedFile != null) "Trocar" else "Selecionar",
                        icon    = Icons.Outlined.FolderOpen,
                        variant = WsButtonVariant.SECONDARY,
                        onClick = { viewModel.selectFile() }
                    )
                }
            }

            if (!uiState.canAdvance) {
                Text(
                    "Preencha o mês de referência, conta, categoria e selecione um arquivo para avançar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = WsTextDisabled
                )
            }

            WsButton(
                text    = "Avançar — Visualizar Preview",
                icon    = Icons.Default.ArrowForward,
                onClick = { viewModel.advance() },
                enabled = uiState.canAdvance,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Coluna direita — histórico placeholder
        Column(
            modifier = Modifier.weight(0.9f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Histórico de importações", style = MaterialTheme.typography.titleMedium)
            Surface(
                color  = WsElevated,
                shape  = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, WsBorder, RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.History, contentDescription = null, tint = WsTextDisabled, modifier = Modifier.size(32.dp))
                        Text("Nenhuma importação anterior", style = MaterialTheme.typography.bodyMedium, color = WsTextDisabled)
                    }
                }
            }
        }
    }
}

// ── Etapa de carregamento (parsing / confirming) ──────────────────────────────

@Composable
private fun LoadingStep(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(320.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = WsAccent, modifier = Modifier.size(48.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = WsTextSecondary)
        }
    }
}

// ── Etapa 2: Preview ─────────────────────────────────────────────────────────

@Composable
private fun PreviewStep(
    uiState: PayrollImportUiState,
    onBack: () -> Unit,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onConfirm: () -> Unit
) {
    val result = uiState.result ?: return
    val entries = result.entries

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Chips de resumo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryChip("${entries.size}", "funcionários lidos", WsAccent)
            SummaryChip("${entries.count { it.employeeFound }}", "localizados", WsSuccess)
            if (result.notFoundCount > 0) SummaryChip("${result.notFoundCount}", "não encontrados", WsDanger)
            if (result.warnings.isNotEmpty()) SummaryChip("${result.warnings.size}", "avisos", WsWarning)
            Spacer(Modifier.weight(1f))
            Text(
                "Total selecionado: ${MoneyFormatter.format(uiState.totalAmountSelected)}",
                style = MaterialTheme.typography.titleSmall,
                color = WsTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Seleção em massa
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${uiState.totalSelected} de ${entries.size} selecionados",
                style = MaterialTheme.typography.bodySmall,
                color = WsTextSecondary
            )
            TextButton(onClick = onSelectAll, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Selecionar todos", style = MaterialTheme.typography.labelSmall, color = WsAccent)
            }
            TextButton(onClick = onDeselectAll, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Desmarcar todos", style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
            }
        }

        // Tabela
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
                .background(WsSurface)
                .heightIn(max = 420.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WsElevated)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(32.dp)) // espaço checkbox
                    TableHeaderCell("FUNCIONÁRIO",   Modifier.weight(2f))
                    TableHeaderCell("CPF",           Modifier.weight(1.2f))
                    TableHeaderCell("ADIANTAMENTO",  Modifier.weight(1f), TextAlign.End)
                    TableHeaderCell("VENC.ADIANT.",  Modifier.weight(0.9f), TextAlign.Center)
                    TableHeaderCell("LÍQUIDO",       Modifier.weight(1f), TextAlign.End)
                    TableHeaderCell("VENC.LÍQ.",     Modifier.weight(0.9f), TextAlign.Center)
                    TableHeaderCell("STATUS",        Modifier.weight(0.9f), TextAlign.Center)
                }
                HorizontalDivider(color = WsBorder)

                LazyColumn {
                    itemsIndexed(entries) { i, entry ->
                        PayrollEntryRow(
                            index    = i,
                            entry    = entry,
                            selected = i in uiState.selectedIndices,
                            onToggle = { onToggle(i) }
                        )
                        HorizontalDivider(color = WsBorder.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // Avisos globais (do parser)
        if (result.warnings.isNotEmpty()) {
            Surface(
                color  = WsWarning.copy(alpha = 0.08f),
                shape  = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, WsWarning.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Avisos do parser:", style = MaterialTheme.typography.labelMedium, color = WsWarning, fontWeight = FontWeight.SemiBold)
                    result.warnings.forEach { w ->
                        Text("• $w", style = MaterialTheme.typography.labelSmall, color = WsWarning)
                    }
                }
            }
        }

        // Rodapé com botões
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WsButton("Voltar", onClick = onBack, variant = WsButtonVariant.SECONDARY, icon = Icons.Default.ArrowBack)
            Spacer(Modifier.weight(1f))
            WsButton(
                text    = "Confirmar importação (${uiState.totalSelected})",
                icon    = Icons.Default.CloudUpload,
                onClick = onConfirm,
                enabled = uiState.totalSelected > 0
            )
        }
    }
}

@Composable
private fun PayrollEntryRow(
    index: Int,
    entry: PayrollEntry,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val rowColor = when {
        !entry.employeeFound       -> WsDanger.copy(alpha = 0.06f)
        entry.warningMessage != null -> WsWarning.copy(alpha = 0.06f)
        else                        -> WsSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked         = selected,
            onCheckedChange = { onToggle() },
            modifier        = Modifier.size(20.dp),
            colors          = CheckboxDefaults.colors(checkedColor = WsAccent, uncheckedColor = WsTextDisabled)
        )
        Spacer(Modifier.width(12.dp))

        Text(
            entry.nome,
            modifier  = Modifier.weight(2f),
            style     = MaterialTheme.typography.bodySmall,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            color     = if (!entry.employeeFound) WsDanger else WsTextPrimary
        )
        Text(
            entry.cpf.formatCpfDisplay(),
            modifier  = Modifier.weight(1.2f),
            style     = MaterialTheme.typography.bodySmall,
            color     = WsTextSecondary
        )
        Text(
            MoneyFormatter.format(entry.adiantamento),
            modifier  = Modifier.weight(1f),
            style     = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            color     = if (entry.adiantamento.isZero()) WsTextDisabled else WsTextPrimary
        )
        Text(
            entry.adiantamentoDueDate.format(dateFmt),
            modifier  = Modifier.weight(0.9f),
            style     = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color     = WsTextSecondary
        )
        Text(
            MoneyFormatter.format(entry.liquido),
            modifier  = Modifier.weight(1f),
            style     = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End
        )
        Text(
            entry.liquidoDueDate.format(dateFmt),
            modifier  = Modifier.weight(0.9f),
            style     = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color     = WsTextSecondary
        )

        // Status badge
        Box(modifier = Modifier.weight(0.9f), contentAlignment = Alignment.Center) {
            when {
                !entry.employeeFound -> StatusBadge("✗ Não encontrado", WsDanger)
                entry.warningMessage != null -> StatusBadge("⚠ Aviso", WsWarning)
                else -> StatusBadge("OK", WsSuccess)
            }
        }
    }
}

@Composable
private fun SummaryChip(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color  = color.copy(alpha = 0.1f),
        shape  = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color  = color.copy(alpha = 0.12f),
        shape  = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            modifier  = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style     = MaterialTheme.typography.labelSmall,
            color     = color,
            fontWeight = FontWeight.SemiBold,
            maxLines  = 1
        )
    }
}

// ── Etapa 4: Resultado ────────────────────────────────────────────────────────

@Composable
private fun DoneStep(
    summary: PayrollImportSummary,
    registeredCpfs: Set<String>,
    missingBankData: List<String>,
    exportLoading: Boolean,
    onRegister: (PayrollEntry) -> Unit,
    onNewImport: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onExportAdiantamentos: () -> Unit,
    onExportLiquidos: () -> Unit,
    onNavigateToEmployees: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

        // Cards de resumo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ResultCard("Transações criadas", summary.transactionsCreated.toString(), WsSuccess, Icons.Default.CheckCircle, Modifier.weight(1f))
            ResultCard("Funcionários processados", summary.employeesProcessed.toString(), WsAccent, Icons.Outlined.Badge, Modifier.weight(1f))
            if (summary.notFoundCount > 0)
                ResultCard("Não localizados", summary.notFoundCount.toString(), WsDanger, Icons.Default.Warning, Modifier.weight(1f))
            if (summary.warningCount > 0)
                ResultCard("Avisos", summary.warningCount.toString(), WsWarning, Icons.Default.Info, Modifier.weight(1f))
        }

        // Lista de não-localizados com botão de cadastro
        if (summary.notFoundEntries.isNotEmpty()) {
            Surface(
                color  = WsDanger.copy(alpha = 0.06f),
                shape  = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, WsDanger.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Funcionários não localizados — cadastre para incluir em importações futuras:",
                        style = MaterialTheme.typography.titleSmall,
                        color = WsDanger,
                        fontWeight = FontWeight.SemiBold
                    )
                    summary.notFoundEntries.forEach { entry ->
                        val alreadyRegistered = entry.cpf in registeredCpfs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (alreadyRegistered) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WsSuccess, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.PersonOff, contentDescription = null, tint = WsDanger, modifier = Modifier.size(16.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.nome, style = MaterialTheme.typography.bodySmall, color = if (alreadyRegistered) WsTextSecondary else WsTextPrimary)
                                Text(
                                    "${entry.cpf.formatCpfDisplay()}  ·  Adiant. ${MoneyFormatter.format(entry.adiantamento)}  |  Líq. ${MoneyFormatter.format(entry.liquido)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WsTextSecondary
                                )
                            }
                            if (alreadyRegistered) {
                                Text("Cadastrado", style = MaterialTheme.typography.labelSmall, color = WsSuccess, fontWeight = FontWeight.SemiBold)
                            } else {
                                WsButton(
                                    text    = "Cadastrar",
                                    icon    = Icons.Default.PersonAdd,
                                    variant = WsButtonVariant.SECONDARY,
                                    onClick = { onRegister(entry) }
                                )
                            }
                        }
                        if (entry != summary.notFoundEntries.last()) {
                            HorizontalDivider(color = WsDanger.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }

        // Botões de navegação
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WsButton(
                text    = "Ver no Contas a Pagar",
                icon    = Icons.Default.List,
                variant = WsButtonVariant.SECONDARY,
                onClick = onNavigateToTransactions
            )
            WsButton(
                text    = "Nova importação",
                icon    = Icons.Default.Refresh,
                onClick = onNewImport
            )
        }

        // Seção de remessa bancária
        HorizontalDivider(color = WsBorder)
        Text(
            "Remessa Bancária",
            style = MaterialTheme.typography.titleSmall,
            color = WsTextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WsButton(
                text    = if (exportLoading) "Gerando..." else "Exportar Adiantamentos",
                icon    = Icons.Default.FileDownload,
                variant = WsButtonVariant.SECONDARY,
                enabled = !exportLoading && summary.employeesProcessed > 0,
                onClick = onExportAdiantamentos
            )
            WsButton(
                text    = if (exportLoading) "Gerando..." else "Exportar Líquidos",
                icon    = Icons.Default.FileDownload,
                enabled = !exportLoading,
                onClick = onExportLiquidos
            )
        }

        // Aviso de funcionários sem dados bancários
        if (missingBankData.isNotEmpty()) {
            Surface(
                color    = WsWarning.copy(alpha = 0.10f),
                shape    = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, WsWarning.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "⚠ ${missingBankData.size} funcionário(s) sem dados bancários — não entraram na remessa:",
                        style = MaterialTheme.typography.titleSmall,
                        color = WsWarning,
                        fontWeight = FontWeight.SemiBold
                    )
                    missingBankData.forEach { nome ->
                        Text(
                            "• $nome",
                            style = MaterialTheme.typography.bodySmall,
                            color = WsTextPrimary
                        )
                    }
                    WsButton(
                        text    = "Ir para Cadastro de Funcionários",
                        icon    = Icons.Default.People,
                        variant = WsButtonVariant.SECONDARY,
                        onClick = onNavigateToEmployees
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color  = color.copy(alpha = 0.08f),
        shape  = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Column {
                Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
            }
        }
    }
}

// ── Dialog: cadastrar funcionário ausente ────────────────────────────────────

@Composable
private fun RegisterMissingEmployeeDialog(
    entry: PayrollEntry,
    onDismiss: () -> Unit,
    onSave: (name: String, cpf: String, role: String, salary: Money, empType: EmploymentType?) -> Unit
) {
    var name           by remember { mutableStateOf(entry.nome) }
    var role           by remember { mutableStateOf("") }
    var salaryInput    by remember { mutableStateOf("") }
    var selectedEmpIdx by remember { mutableStateOf<Int?>(null) }

    val empTypeOptions = EmploymentType.entries.mapIndexed { i, e -> i to e.label }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = WsSurface,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = WsAccent, modifier = Modifier.size(20.dp))
                Text("Cadastrar Funcionário", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Preencha os dados para cadastrar o funcionário. Ele será incluído nas próximas importações.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WsTextSecondary
                )
                WsTextField("NOME COMPLETO", name) { name = it }
                WsTextField(
                    label = "CPF",
                    value = entry.cpf.formatCpfDisplay(),
                    onValueChange = {},
                    enabled = false
                )
                WsTextField("CARGO / FUNÇÃO", role) { role = it }
                WsTextField("SALÁRIO BASE (R$)", salaryInput) { salaryInput = it }
                WsSelectField(
                    label       = "TIPO DE VÍNCULO",
                    options     = empTypeOptions,
                    selectedId  = selectedEmpIdx,
                    onSelect    = { selectedEmpIdx = it },
                    placeholder = "Selecionar (opcional)",
                    nullable    = true
                )
                Text(
                    "Lembre de vincular um fornecedor ao funcionário para que os lançamentos da folha sejam gerados corretamente.",
                    style = MaterialTheme.typography.labelSmall,
                    color = WsWarning
                )
            }
        },
        confirmButton = {
            WsButton(
                text    = "Cadastrar",
                icon    = Icons.Default.Check,
                enabled = name.isNotBlank() && role.isNotBlank(),
                onClick = {
                    val salary  = Money.fromString(salaryInput.replace(",", "."))
                    val empType = selectedEmpIdx?.let { EmploymentType.entries[it] }
                    onSave(name, entry.cpf, role, salary, empType)
                }
            )
        },
        dismissButton = {
            WsButton(
                text    = "Cancelar",
                variant = WsButtonVariant.SECONDARY,
                onClick = onDismiss
            )
        }
    )
}

private fun String.formatCpfDisplay(): String =
    if (length == 11) "${substring(0,3)}.${substring(3,6)}.${substring(6,9)}-${substring(9)}" else this
