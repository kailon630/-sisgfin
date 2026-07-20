package br.com.sisgfin.ofx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.VerticalDivider
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
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import java.time.format.DateTimeFormatter

private val dateFmt     = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
fun OfxImportScreen(
    viewModel: OfxImportViewModel,
    onNavigateToStatement: () -> Unit,
    onNavigateToManualTx: (Transaction) -> Unit
) {
    val step            by viewModel.step.collectAsState()
    val accounts        by viewModel.accounts.collectAsState()
    val selectedId      by viewModel.selectedAccountId.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val importHistory   by viewModel.importHistory.collectAsState()
    val errorMessage    by viewModel.errorMessage.collectAsState()
    val suppliers       by viewModel.suppliers.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAccounts()
        viewModel.loadHistory()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        when (val s = step) {
            is OfxImportViewModel.Step.SelectFile ->
                SelectFileStep(
                    accounts      = accounts,
                    selectedId    = selectedId,
                    isLoading     = isLoading,
                    errorMessage  = errorMessage,
                    importHistory = importHistory,
                    onSelectFile  = { viewModel.selectFile() },
                    onSelectAccount = { viewModel.selectAccount(it) }
                )

            is OfxImportViewModel.Step.Preview ->
                PreviewStep(
                    preview     = s,
                    accounts    = accounts,
                    selectedId  = selectedId,
                    isLoading   = isLoading,
                    onBack      = { viewModel.reset() },
                    onSelectAccount = { viewModel.selectAccount(it) },
                    onImport    = { viewModel.executeImport() }
                )

            is OfxImportViewModel.Step.Reconcile ->
                ReconcileStep(
                    reconcile   = s,
                    isLoading   = isLoading,
                    onLink      = { idx, cand -> viewModel.linkCandidate(idx, cand) },
                    onIgnore    = { idx -> viewModel.ignoreCandidate(idx) },
                    onFinish    = { viewModel.finishReconciliation() }
                )

            is OfxImportViewModel.Step.Done ->
                DoneStep(
                    result                = s.result,
                    suppliers             = suppliers,
                    onNewImport           = { viewModel.reset() },
                    onNavigateToStatement = onNavigateToStatement,
                    onNavigateToManualTx  = onNavigateToManualTx,
                    onQuickEdit           = { txId, desc, supId ->
                        viewModel.quickEditOfxEntry(txId, desc, supId)
                    },
                    onQuickPay            = { txId, date -> viewModel.quickPayManual(txId, date) },
                    onQuickCancel         = { txId -> viewModel.quickCancelManual(txId) }
                )
        }
    }
}

// ── Etapa 1: Seleção de arquivo ───────────────────────────────────────────────

@Composable
private fun SelectFileStep(
    accounts: List<br.com.sisgfin.FinancialAccount>,
    selectedId: Int?,
    isLoading: Boolean,
    errorMessage: String?,
    importHistory: List<OfxImport>,
    onSelectFile: () -> Unit,
    onSelectAccount: (Int?) -> Unit
) {
    val accountOptions = remember(accounts) { accounts.map { it.id to it.name } }

    // Cabeçalho
    Text("Importar Extrato OFX", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Importe o arquivo .ofx do seu banco para registrar lançamentos automaticamente",
        style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary
    )

    // Card de seleção
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
            .background(WsSurface)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Outlined.CloudUpload, null,
                modifier = Modifier.size(48.dp),
                tint = WsTextDisabled
            )
            Text(
                "Selecione um arquivo .ofx gerado pelo seu banco",
                style = MaterialTheme.typography.bodyMedium,
                color = WsTextSecondary,
                textAlign = TextAlign.Center
            )

            // Seletor de conta
            Box(modifier = Modifier.widthIn(min = 320.dp, max = 480.dp)) {
                WsSelectField(
                    label       = "CONTA DE DESTINO",
                    options     = accountOptions,
                    selectedId  = selectedId,
                    onSelect    = onSelectAccount,
                    placeholder = "Selecionar conta...",
                    nullable    = true
                )
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                WsButton(
                    text = "Selecionar arquivo...",
                    icon  = Icons.Default.FolderOpen,
                    onClick = onSelectFile
                )
            }

            errorMessage?.let {
                Surface(
                    color = WsDanger.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = WsDanger, modifier = Modifier.size(16.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = WsDanger)
                    }
                }
            }
        }
    }

    // Histórico de importações
    if (importHistory.isNotEmpty()) {
        ImportHistoryTable(importHistory, accounts)
    }
}

// ── Etapa 2: Preview ──────────────────────────────────────────────────────────

@Composable
private fun PreviewStep(
    preview: OfxImportViewModel.Step.Preview,
    accounts: List<br.com.sisgfin.FinancialAccount>,
    selectedId: Int?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onSelectAccount: (Int?) -> Unit,
    onImport: () -> Unit
) {
    val statement      = preview.statement
    val accountOptions = remember(accounts) { accounts.map { it.id to it.name } }
    val newCount       = statement.transactions.size - preview.duplicateCount

    // Toolbar do wizard
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ArrowBack, "Voltar", tint = WsTextSecondary, modifier = Modifier.size(18.dp))
            }
            Column {
                Text("Preview do arquivo", style = MaterialTheme.typography.headlineMedium)
                Text(preview.file.name, style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
            }
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            WsButton(
                text    = "Importar $newCount lançamentos",
                icon    = Icons.Default.CloudDownload,
                enabled = selectedId != null,
                onClick = onImport
            )
        }
    }

    // Cards de resumo
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryChip("Banco", statement.bankId,         Modifier.weight(1f))
        SummaryChip("Conta OFX", statement.acctId,    Modifier.weight(1f))
        SummaryChip("Período",
            "${statement.dtStart.format(dateFmt)} – ${statement.dtEnd.format(dateFmt)}",
            Modifier.weight(2f)
        )
        SummaryChip("Total", "${statement.transactions.size} lançamentos", Modifier.weight(1f))
        if (preview.duplicateCount > 0) {
            SummaryChip(
                "Já importados",
                "${preview.duplicateCount} duplicatas",
                Modifier.weight(1f),
                valueColor = WsWarning
            )
        }
    }

    // Seletor de conta + aviso de divergência
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.widthIn(max = 400.dp)) {
            WsSelectField(
                label       = "CONTA DE DESTINO",
                options     = accountOptions,
                selectedId  = selectedId,
                onSelect    = onSelectAccount,
                placeholder = "Selecionar conta...",
                nullable    = false
            )
        }
        if (selectedId == null) {
            Surface(
                color = WsWarning.copy(alpha = 0.08f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(top = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = WsWarning, modifier = Modifier.size(14.dp))
                    Text(
                        "Selecione a conta para continuar",
                        style = MaterialTheme.typography.labelMedium,
                        color = WsWarning
                    )
                }
            }
        }
    }

    // Tabela dos primeiros 20 lançamentos
    Text(
        "Primeiros ${minOf(20, statement.transactions.size)} lançamentos",
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.5.sp),
        color = WsTextDisabled
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
            .background(WsSurface)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().background(WsElevated).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell("DATA",      Modifier.weight(0.8f))
                TableHeaderCell("TIPO",      Modifier.weight(0.7f))
                TableHeaderCell("FITID",     Modifier.weight(1.2f))
                TableHeaderCell("DESCRIÇÃO", Modifier.weight(2.5f))
                TableHeaderCell("VALOR",     Modifier.weight(1f), TextAlign.End)
            }
            HorizontalDivider(color = WsBorder)
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(statement.transactions.take(20)) { tx ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tx.date.format(dateFmt),
                            modifier = Modifier.weight(0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = WsTextSecondary)
                        Text(tx.type.name,
                            modifier = Modifier.weight(0.7f),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (tx.isInflow) WsSuccess else WsDanger)
                        Text(tx.fitId,
                            modifier = Modifier.weight(1.2f),
                            style = MaterialTheme.typography.labelSmall,
                            color = WsTextDisabled,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(tx.memo,
                            modifier = Modifier.weight(2.5f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(
                            MoneyFormatter.format(tx.amount.abs()),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            textAlign = TextAlign.End,
                            color = if (tx.isInflow) WsSuccess else WsTextPrimary
                        )
                    }
                    HorizontalDivider(color = WsBorder.copy(alpha = 0.4f))
                }
            }
        }
    }
}

// ── Etapa 2b: Conciliação Manual ─────────────────────────────────────────────

@Composable
private fun ReconcileStep(
    reconcile: OfxImportViewModel.Step.Reconcile,
    isLoading: Boolean,
    onLink: (Int, ConciliationCandidate) -> Unit,
    onIgnore: (Int) -> Unit,
    onFinish: () -> Unit
) {
    val candidates = reconcile.candidates
    val handled    = reconcile.handledIndices
    val allHandled = candidates.indices.all { it in handled }

    // Cabeçalho
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Possíveis conciliações", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${candidates.size} lançamento${if (candidates.size != 1) "s" else ""} do extrato " +
                "corresponde${if (candidates.size == 1) "" else "m"} a lançamentos manuais pendentes",
                style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            WsButton(
                text    = if (allHandled) "Concluir" else "Pular restantes",
                icon    = Icons.Default.ArrowForward,
                onClick = onFinish
            )
        }
    }

    Surface(
        color  = WsInfo.copy(alpha = 0.08f),
        shape  = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WsInfo.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CompareArrows, null, tint = WsInfo, modifier = Modifier.size(16.dp))
            Text(
                "Vincular une os dois lançamentos em um só (o lançamento OFX é removido). " +
                "Ignorar mantém ambos independentes.",
                style = MaterialTheme.typography.bodyMedium, color = WsInfo
            )
        }
    }

    LazyColumn(
        modifier            = Modifier.fillMaxWidth().heightIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(candidates) { idx, cand ->
            val action = handled[idx]
            ConciliationCandidateCard(
                index    = idx + 1,
                total    = candidates.size,
                cand     = cand,
                action   = action,
                onLink   = { onLink(idx, cand) },
                onIgnore = { onIgnore(idx) }
            )
        }
    }
}

@Composable
private fun ConciliationCandidateCard(
    index: Int,
    total: Int,
    cand: ConciliationCandidate,
    action: Boolean?,     // null = pendente, true = vinculado, false = ignorado
    onLink: () -> Unit,
    onIgnore: () -> Unit
) {
    val borderColor = when (action) {
        true  -> WsSuccess.copy(alpha = 0.5f)
        false -> WsBorder
        null  -> WsAccent.copy(alpha = 0.4f)
    }
    val bgColor = when (action) {
        true  -> WsSuccess.copy(alpha = 0.04f)
        false -> WsBackground
        null  -> WsSurface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor)
    ) {
        Column {
            // Badge do número
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Candidato $index de $total",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                    color = WsTextDisabled
                )
                when (action) {
                    true  -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = WsSuccess, modifier = Modifier.size(14.dp))
                        Text("Vinculado", style = MaterialTheme.typography.labelSmall, color = WsSuccess)
                    }
                    false -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cancel, null, tint = WsTextDisabled, modifier = Modifier.size(14.dp))
                        Text("Ignorado", style = MaterialTheme.typography.labelSmall, color = WsTextDisabled)
                    }
                    null  -> {}
                }
            }

            HorizontalDivider(color = WsBorder.copy(alpha = 0.4f))

            // Lado a lado: OFX | Manual
            Row(
                modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Coluna OFX
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "EXTRATO OFX",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                        color = WsAccent
                    )
                    Text(cand.ofxTx.memo.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(cand.ofxTx.date.format(dateFmt),
                        style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
                    Text(
                        MoneyFormatter.format(cand.ofxTx.amount.abs()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (cand.ofxTx.isInflow) WsSuccess else WsDanger
                    )
                    Text(
                        "FITID: ${cand.ofxTx.fitId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WsTextDisabled,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }

                // Divisor
                VerticalDivider(color = WsBorder, modifier = Modifier.height(90.dp).align(Alignment.CenterVertically))

                // Coluna Manual
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "LANÇAMENTO MANUAL",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                        color = WsWarning
                    )
                    Text(cand.manualTx.description,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(cand.manualTx.dueDate.toLocalDate().format(dateFmt),
                        style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
                    Text(
                        MoneyFormatter.format(cand.manualTx.amount),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (cand.manualTx.type == TransactionType.INCOME) WsSuccess else WsDanger
                    )
                    val statusColor = when (cand.manualTx.status) {
                        TransactionStatus.OVERDUE -> WsDanger
                        TransactionStatus.PENDING -> WsWarning
                        else -> WsTextSecondary
                    }
                    Text(
                        cand.manualTx.status.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            // Botões de ação (apenas quando ainda pendente)
            if (action == null) {
                HorizontalDivider(color = WsBorder.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WsButton(
                        text = "Ignorar",
                        icon = Icons.Default.Close,
                        variant = WsButtonVariant.SECONDARY,
                        onClick = onIgnore
                    )
                    Spacer(Modifier.width(8.dp))
                    WsButton(
                        text    = "Vincular",
                        icon    = Icons.Default.Link,
                        onClick = onLink
                    )
                }
            }
        }
    }
}

// ── Etapa 3: Resultado ────────────────────────────────────────────────────────

@Composable
private fun DoneStep(
    result: OfxImportResult,
    suppliers: List<Supplier>,
    onNewImport: () -> Unit,
    onNavigateToStatement: () -> Unit,
    onNavigateToManualTx: (Transaction) -> Unit,
    onQuickEdit: (txId: Int, description: String, supplierId: Int?) -> Unit,
    onQuickPay: (txId: Int, paymentDate: java.time.LocalDateTime) -> Unit,
    onQuickCancel: (txId: Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Icon(
            Icons.Default.CheckCircle, null,
            modifier = Modifier.size(56.dp),
            tint = if (result.hasErrors) WsWarning else WsSuccess
        )

        Text(
            if (result.hasErrors) "Importação concluída com erros" else "Importação concluída",
            style = MaterialTheme.typography.headlineMedium
        )

        // Contadores
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            ResultStatCard("Importados",  result.newCount.toString(),       WsSuccess,     Modifier.weight(1f))
            ResultStatCard("Já existiam", result.duplicateCount.toString(), WsWarning,     Modifier.weight(1f))
            ResultStatCard("Erros",       result.errorCount.toString(),
                if (result.hasErrors) WsDanger else WsTextDisabled, Modifier.weight(1f))
        }

        // Avisos de ACCTID
        if (result.hasWarnings) {
            Surface(
                color = WsWarning.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.warnings.forEach { w ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, null, tint = WsWarning, modifier = Modifier.size(14.dp))
                            Text(w, style = MaterialTheme.typography.bodyMedium, color = WsWarning)
                        }
                    }
                }
            }
        }

        // Erros individuais
        if (result.hasErrors) {
            Surface(
                color = WsDanger.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Lançamentos com erro (${result.errorCount})",
                        style = MaterialTheme.typography.labelMedium, color = WsDanger)
                    result.errors.take(10).forEach { e ->
                        Text(e, style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
                    }
                    if (result.errors.size > 10) {
                        Text("… e mais ${result.errors.size - 10} erros",
                            style = MaterialTheme.typography.labelSmall, color = WsTextDisabled)
                    }
                }
            }
        }

        // Ações principais
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WsButton(text = "Nova importação", icon = Icons.Default.Refresh,
                variant = WsButtonVariant.SECONDARY, onClick = onNewImport)
            WsButton(text = "Ver no extrato", icon = Icons.Default.ArrowForward, onClick = onNavigateToStatement)
        }

        // ── Entradas sem previsão ─────────────────────────────────────────────
        if (result.unmatchedOfx.isNotEmpty()) {
            UnmatchedOfxSection(
                entries   = result.unmatchedOfx,
                suppliers = suppliers,
                onSave    = onQuickEdit
            )
        }

        // ── Previsões não liquidadas ──────────────────────────────────────────
        if (result.unmatchedManual.isNotEmpty()) {
            UnmatchedManualSection(
                transactions = result.unmatchedManual,
                onNavigate   = onNavigateToManualTx,
                onQuickPay   = onQuickPay,
                onQuickCancel = onQuickCancel
            )
        }
    }
}

// ── Seção: Entradas OFX sem previsão ─────────────────────────────────────────

@Composable
private fun UnmatchedOfxSection(
    entries: List<UnmatchedOfxEntry>,
    suppliers: List<Supplier>,
    onSave: (txId: Int, description: String, supplierId: Int?) -> Unit
) {
    var editingEntry by remember { mutableStateOf<UnmatchedOfxEntry?>(null) }
    var savedIds     by remember { mutableStateOf(setOf<Int>()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(color = WsWarning.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                Text(
                    "${entries.size}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = WsWarning,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "Entradas sem previsão — não havia lançamento manual correspondente",
                style = MaterialTheme.typography.titleSmall, color = WsTextPrimary
            )
        }
        Text(
            "Estes lançamentos foram importados como PAGO. Clique em Detalhar para adicionar descrição e fornecedor.",
            style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary
        )

        entries.forEach { entry ->
            val done = entry.txId in savedIds
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, if (done) WsSuccess.copy(alpha = 0.4f) else WsBorder, RoundedCornerShape(8.dp))
                    .background(if (done) WsSuccess.copy(alpha = 0.04f) else WsSurface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            entry.ofxTx.memo.ifBlank { "Sem descrição" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            entry.ofxTx.date.format(dateFmt),
                            style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary
                        )
                    }
                    Text(
                        MoneyFormatter.format(entry.ofxTx.amount.abs()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (entry.ofxTx.isInflow) WsSuccess else WsDanger
                    )
                    if (done) {
                        Icon(Icons.Default.CheckCircle, null, tint = WsSuccess, modifier = Modifier.size(18.dp))
                    } else {
                        WsButton(
                            text    = "Detalhar",
                            icon    = Icons.Default.Edit,
                            variant = WsButtonVariant.SECONDARY,
                            onClick = { editingEntry = entry }
                        )
                    }
                }
            }
        }
    }

    editingEntry?.let { entry ->
        OFXQuickEditModal(
            entry     = entry,
            suppliers = suppliers,
            onSave    = { desc, supId ->
                onSave(entry.txId, desc, supId)
                savedIds = savedIds + entry.txId
                editingEntry = null
            },
            onCancel  = { editingEntry = null }
        )
    }
}

@Composable
private fun OFXQuickEditModal(
    entry: UnmatchedOfxEntry,
    suppliers: List<Supplier>,
    onSave: (description: String, supplierId: Int?) -> Unit,
    onCancel: () -> Unit
) {
    var description by remember(entry.txId) { mutableStateOf(entry.ofxTx.memo) }
    var supplierId  by remember(entry.txId) { mutableStateOf<Int?>(null) }

    val supplierOptions = remember(suppliers) { suppliers.map { it.id to it.name } }
    val selectedSupplierIdx = supplierId?.let { id -> suppliers.indexOfFirst { it.id == id }.takeIf { it >= 0 } }

    AlertDialog(
        onDismissRequest = onCancel,
        shape            = RoundedCornerShape(8.dp),
        containerColor   = WsSurface,
        properties       = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier         = Modifier.width(520.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Detalhar lançamento", style = MaterialTheme.typography.titleLarge)
                Surface(
                    color = WsElevated, shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            entry.ofxTx.date.format(dateFmt),
                            style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary
                        )
                        Text(
                            MoneyFormatter.format(entry.ofxTx.amount.abs()),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (entry.ofxTx.isInflow) WsSuccess else WsDanger
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                WsTextField("DESCRIÇÃO", description) { description = it }
                WsSelectField(
                    label       = "FORNECEDOR",
                    options     = supplierOptions,
                    selectedId  = selectedSupplierIdx,
                    onSelect    = { idx -> supplierId = idx?.let { suppliers[it].id } },
                    placeholder = "Selecionar fornecedor...",
                    nullable    = true
                )
            }
        },
        confirmButton = {
            WsButton("Salvar", onClick = { onSave(description.trim().ifBlank { entry.ofxTx.memo }, supplierId) })
        },
        dismissButton = {
            WsButton("Cancelar", variant = WsButtonVariant.TERTIARY, onClick = onCancel)
        }
    )
}

// ── Seção: Previsões não liquidadas ──────────────────────────────────────────

private enum class ManualTxResolution { PAID, CANCELLED }

@Composable
private fun UnmatchedManualSection(
    transactions: List<Transaction>,
    onNavigate: (Transaction) -> Unit,
    onQuickPay: (txId: Int, paymentDate: java.time.LocalDateTime) -> Unit,
    onQuickCancel: (txId: Int) -> Unit
) {
    var payingTx      by remember { mutableStateOf<Transaction?>(null) }
    var confirmingTx  by remember { mutableStateOf<Transaction?>(null) }
    var resolved      by remember { mutableStateOf(mapOf<Int, ManualTxResolution>()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(color = WsDanger.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                Text(
                    "${transactions.size}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = WsDanger,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "Previsões não liquidadas — lançamentos manuais sem entrada no extrato",
                style = MaterialTheme.typography.titleSmall, color = WsTextPrimary
            )
        }
        Text(
            "Estes lançamentos estavam previstos no período mas não aparecem no extrato bancário. " +
            "Quite, cancele ou abra o lançamento para tratar individualmente.",
            style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary
        )

        transactions.forEach { tx ->
            val resolution  = resolved[tx.id]
            val statusColor = when (tx.status) {
                TransactionStatus.OVERDUE -> WsDanger
                else                      -> WsWarning
            }
            val (borderColor, bgColor) = when (resolution) {
                ManualTxResolution.PAID      -> WsSuccess.copy(alpha = 0.5f) to WsSuccess.copy(alpha = 0.04f)
                ManualTxResolution.CANCELLED -> WsBorder to WsBackground
                null                         -> WsBorder to WsSurface
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .background(bgColor)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                tx.description,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Venc. ${tx.dueDate.toLocalDate().format(dateFmt)}",
                                    style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary
                                )
                                if (resolution == null) {
                                    Surface(color = statusColor.copy(alpha = 0.12f), shape = RoundedCornerShape(3.dp)) {
                                        Text(
                                            tx.status.displayName,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            MoneyFormatter.format(tx.amount),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (tx.type == TransactionType.INCOME) WsSuccess else WsDanger
                        )
                        when (resolution) {
                            ManualTxResolution.PAID -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = WsSuccess, modifier = Modifier.size(16.dp))
                                Text("Quitado", style = MaterialTheme.typography.labelMedium, color = WsSuccess)
                            }
                            ManualTxResolution.CANCELLED -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Cancel, null, tint = WsTextDisabled, modifier = Modifier.size(16.dp))
                                Text("Cancelado", style = MaterialTheme.typography.labelMedium, color = WsTextDisabled)
                            }
                            null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WsButton(
                                    text    = "Quitar",
                                    icon    = Icons.Default.Check,
                                    onClick = { payingTx = tx }
                                )
                                WsButton(
                                    text    = "Cancelar",
                                    icon    = Icons.Default.Close,
                                    variant = WsButtonVariant.SECONDARY,
                                    onClick = { confirmingTx = tx }
                                )
                                WsButton(
                                    text    = "Ver",
                                    icon    = Icons.Default.OpenInNew,
                                    variant = WsButtonVariant.TERTIARY,
                                    onClick = { onNavigate(tx) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog de quitação rápida
    payingTx?.let { tx ->
        QuickPayDialog(
            tx        = tx,
            onConfirm = { date ->
                onQuickPay(tx.id, date)
                resolved = resolved + (tx.id to ManualTxResolution.PAID)
                payingTx = null
            },
            onDismiss = { payingTx = null }
        )
    }

    // Dialog de confirmação de cancelamento
    confirmingTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { confirmingTx = null },
            shape            = RoundedCornerShape(8.dp),
            containerColor   = WsSurface,
            title            = { Text("Cancelar lançamento?") },
            text             = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tx.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "O lançamento será cancelado. Esta ação não pode ser desfeita.",
                        style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary
                    )
                }
            },
            confirmButton = {
                WsButton("Confirmar cancelamento", onClick = {
                    onQuickCancel(tx.id)
                    resolved = resolved + (tx.id to ManualTxResolution.CANCELLED)
                    confirmingTx = null
                })
            },
            dismissButton = {
                WsButton("Voltar", variant = WsButtonVariant.TERTIARY, onClick = { confirmingTx = null })
            }
        )
    }
}

@Composable
private fun QuickPayDialog(
    tx: Transaction,
    onConfirm: (java.time.LocalDateTime) -> Unit,
    onDismiss: () -> Unit
) {
    var payDate by remember { mutableStateOf(java.time.LocalDate.now().format(dateFmt)) }
    val parsed  = runCatching {
        java.time.LocalDate.parse(payDate, dateFmt)
    }.getOrNull()
    val dateError = when {
        parsed == null -> "Data inválida"
        parsed < tx.issueDate.toLocalDate() -> "Data não pode ser anterior à emissão"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(8.dp),
        containerColor   = WsSurface,
        title            = { Text("Registrar pagamento") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tx.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Valor: ${MoneyFormatter.format(tx.amount)}",
                    style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary
                )
                WsTextField("DATA DE PAGAMENTO (DD/MM/AAAA)", payDate) { payDate = it }
                if (dateError != null) {
                    Text(dateError, style = MaterialTheme.typography.labelMedium, color = WsDanger)
                }
            }
        },
        confirmButton = {
            WsButton("Confirmar", onClick = {
                if (dateError == null && parsed != null) onConfirm(parsed.atStartOfDay())
            })
        },
        dismissButton = {
            WsButton("Cancelar", variant = WsButtonVariant.TERTIARY, onClick = onDismiss)
        }
    )
}

// ── Histórico de importações ──────────────────────────────────────────────────

@Composable
private fun ImportHistoryTable(
    history: List<OfxImport>,
    accounts: List<br.com.sisgfin.FinancialAccount>
) {
    val accountMap = remember(accounts) { accounts.associate { it.id to it.name } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.History, null, tint = WsTextDisabled, modifier = Modifier.size(14.dp))
            Text(
                "HISTÓRICO DE IMPORTAÇÕES",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, letterSpacing = 0.8.sp),
                color = WsTextDisabled
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
                .background(WsSurface)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().background(WsElevated).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableHeaderCell("ARQUIVO",    Modifier.weight(2f))
                    TableHeaderCell("DATA",       Modifier.weight(1.2f))
                    TableHeaderCell("CONTA",      Modifier.weight(1.5f))
                    TableHeaderCell("PERÍODO",    Modifier.weight(1.8f))
                    TableHeaderCell("NOVOS",      Modifier.weight(0.7f), TextAlign.Center)
                    TableHeaderCell("DUPLICATAS", Modifier.weight(0.8f), TextAlign.Center)
                }
                HorizontalDivider(color = WsBorder)
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(history) { imp ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(imp.filename,
                                modifier = Modifier.weight(2f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(imp.importedAt.format(dateTimeFmt),
                                modifier = Modifier.weight(1.2f),
                                style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
                            Text(accountMap[imp.accountId] ?: "#${imp.accountId}",
                                modifier = Modifier.weight(1.5f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${imp.dtStart.format(dateFmt)} – ${imp.dtEnd.format(dateFmt)}",
                                modifier = Modifier.weight(1.8f),
                                style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
                            Text(imp.newRecords.toString(),
                                modifier = Modifier.weight(0.7f),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                textAlign = TextAlign.Center, color = WsSuccess)
                            Text(imp.duplicateRecords.toString(),
                                modifier = Modifier.weight(0.8f),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center, color = WsTextSecondary)
                        }
                        HorizontalDivider(color = WsBorder.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

// ── Componentes auxiliares ────────────────────────────────────────────────────

@Composable
private fun SummaryChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified
) {
    val effectiveValueColor = if (valueColor == Color.Unspecified) WsTextPrimary else valueColor
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(6.dp),
        color    = WsElevated,
        border   = androidx.compose.foundation.BorderStroke(1.dp, WsBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                color = WsTextDisabled
            )
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = effectiveValueColor)
        }
    }
}

@Composable
private fun ResultStatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(8.dp),
        color    = color.copy(alpha = 0.08f),
        border   = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = WsTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
