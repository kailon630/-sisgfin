package br.com.sisgfin.financial.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.sisgfin.*
import br.com.sisgfin.core.ui.notifications.CrudEventEffects
import br.com.sisgfin.core.ui.panel.BaseCrudPanel
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.money.toCentsStr
import br.com.sisgfin.financial.money.centsToMoney
import br.com.sisgfin.financial.money.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun ProjectsScreen(
    viewModel: ProjectViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val realizedMap by viewModel.realizedMap.collectAsState()
    CrudEventEffects(viewModel)

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        CrudToolbar(
            title = "Projetos",
            subtitle = "Iniciativas, obras e projetos com acompanhamento financeiro",
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = { viewModel.search(it) },
            newItemLabel = "Novo Projeto",
            onNewItemClick = {
                viewModel.openNew()
                onShowRightPanel { ProjectDetailsPanel(viewModel, onCloseRightPanel) }
            },
            onRefreshClick = { viewModel.load() }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
                .background(WsSurface)
        ) {
            if (uiState.items.isEmpty() && !uiState.isLoading) {
                EmptyState("Nenhum projeto cadastrado. Crie um novo projeto para começar.")
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().background(WsElevated)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableHeaderCell("CÓDIGO",      Modifier.width(90.dp))
                        TableHeaderCell("NOME",        Modifier.weight(2f))
                        TableHeaderCell("STATUS",      Modifier.width(110.dp))
                        TableHeaderCell("PERÍODO",     Modifier.weight(1f))
                        TableHeaderCell("ORÇADO",      Modifier.width(120.dp), TextAlign.End)
                        TableHeaderCell("REALIZADO",   Modifier.width(120.dp), TextAlign.End)
                        TableHeaderCell("% EXEC.",     Modifier.width(80.dp),  TextAlign.Center)
                        TableHeaderCell("SITUAÇÃO",    Modifier.width(80.dp),  TextAlign.Center)
                    }
                    HorizontalDivider(color = WsBorder)

                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(uiState.items, key = { it.id }) { project ->
                            ProjectRow(
                                project   = project,
                                realized  = realizedMap[project.id] ?: Money.ZERO,
                                selected  = uiState.selectedItem?.id == project.id,
                                onClick   = {
                                    viewModel.select(project)
                                    onShowRightPanel {
                                        ProjectDetailsPanel(viewModel, onCloseRightPanel)
                                    }
                                }
                            )
                            HorizontalDivider(color = WsBorder.copy(alpha = 0.35f))
                        }
                    }
                }
            }

            if (uiState.isLoading) WsLoaderFullscreen()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(
    project: Project,
    realized: Money,
    selected: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when (project.status) {
        ProjectStatus.EM_ANDAMENTO -> WsAccent
        ProjectStatus.CONCLUIDO    -> WsSuccess
        ProjectStatus.CANCELADO    -> WsDanger
        ProjectStatus.PLANEJAMENTO -> WsTextSecondary
    }
    val budget = project.budget
    val pct = if (budget != null && !budget.isZero()) {
        realized.value.toDouble() / budget.value.toDouble() * 100.0
    } else null
    val pctColor = when {
        pct == null -> WsTextSecondary
        pct > 100   -> WsDanger
        pct > 85    -> WsWarning
        else        -> WsSuccess
    }
    val periodStr = when {
        project.startDate != null && project.expectedEnd != null ->
            "${project.startDate.format(dateFmt)} – ${project.expectedEnd.format(dateFmt)}"
        project.startDate != null -> "Início ${project.startDate.format(dateFmt)}"
        project.expectedEnd != null -> "Até ${project.expectedEnd.format(dateFmt)}"
        else -> "—"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) WsAccent.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            project.code,
            modifier = Modifier.width(90.dp),
            style = MaterialTheme.typography.labelSmall,
            color = WsTextSecondary
        )
        Text(
            project.name,
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(modifier = Modifier.width(110.dp)) {
            Surface(shape = RoundedCornerShape(4.dp), color = statusColor.copy(alpha = 0.1f)) {
                Text(
                    project.status.label,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }
        Text(
            periodStr,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = WsTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            budget?.let { MoneyFormatter.format(it) } ?: "—",
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodySmall,
            color = WsTextSecondary,
            textAlign = TextAlign.End
        )
        Text(
            MoneyFormatter.format(realized),
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (budget != null && realized > budget) WsDanger else WsTextPrimary,
            textAlign = TextAlign.End
        )
        Box(modifier = Modifier.width(80.dp), contentAlignment = Alignment.Center) {
            if (pct != null) {
                Text(
                    "%.1f%%".format(pct),
                    style = MaterialTheme.typography.labelSmall,
                    color = pctColor
                )
            } else {
                Text("—", style = MaterialTheme.typography.labelSmall, color = WsTextDisabled)
            }
        }
        Box(modifier = Modifier.width(80.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (project.isActive) WsSuccess.copy(alpha = 0.1f) else WsTextDisabled.copy(alpha = 0.1f)
            ) {
                Text(
                    if (project.isActive) "Ativo" else "Inativo",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (project.isActive) WsSuccess else WsTextDisabled
                )
            }
        }
    }
}

@Composable
fun ProjectDetailsPanel(
    viewModel: ProjectViewModel,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val realizedMap by viewModel.realizedMap.collectAsState()
    val item = uiState.selectedItem ?: return

    var code        by remember(item.id) { mutableStateOf(item.code) }
    var name        by remember(item.id) { mutableStateOf(item.name) }
    var description by remember(item.id) { mutableStateOf(item.description ?: "") }
    var status      by remember(item.id) { mutableStateOf(item.status) }
    var budgetStr   by remember(item.id) { mutableStateOf(item.budget?.toCentsStr() ?: "") }
    var startStr    by remember(item.id) { mutableStateOf(item.startDate?.format(dateFmt) ?: "") }
    var endStr      by remember(item.id) { mutableStateOf(item.expectedEnd?.format(dateFmt) ?: "") }
    var actualEndStr by remember(item.id) { mutableStateOf(item.actualEnd?.format(dateFmt) ?: "") }

    val realized = realizedMap[item.id] ?: Money.ZERO
    val budget   = if (budgetStr.isNotBlank()) budgetStr.centsToMoney() else null
    val pct = if (budget != null && !budget.isZero()) {
        realized.value.toDouble() / budget.value.toDouble() * 100.0
    } else null

    val isNew = item.id == 0
    val isDirty = code != item.code || name != item.name ||
        description != (item.description ?: "") ||
        status != item.status ||
        budgetStr != (item.budget?.toCentsStr() ?: "") ||
        startStr != (item.startDate?.format(dateFmt) ?: "") ||
        endStr != (item.expectedEnd?.format(dateFmt) ?: "") ||
        actualEndStr != (item.actualEnd?.format(dateFmt) ?: "")

    val statusOptions = ProjectStatus.entries.map { it.ordinal to it.label }

    BaseCrudPanel(
        title      = if (isNew) "Novo Projeto" else item.name,
        subtitle   = if (!isNew) item.code else null,
        onClose    = onClose,
        isLoading  = uiState.isLoading,
        isDirty    = isDirty,
        errorMessage = uiState.errorMessage,
        saveLabel  = if (isNew) "Criar Projeto" else "Salvar Alterações",
        onSave = {
            viewModel.save(
                item.copy(
                    code        = code.trim(),
                    name        = name.trim(),
                    description = description.trim().ifBlank { null },
                    status      = status,
                    budget      = budget,
                    startDate   = parseDate(startStr),
                    expectedEnd = parseDate(endStr),
                    actualEnd   = parseDate(actualEndStr)
                )
            )
        },
        onCancel   = onClose,
        toolbar = if (!isNew) {
            {
                IconButton(onClick = { viewModel.toggleActive(item.id) }) {
                    Icon(
                        if (item.isActive) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                        contentDescription = if (item.isActive) "Inativar" else "Reativar",
                        tint = if (item.isActive) WsSuccess else WsTextDisabled
                    )
                }
            }
        } else null
    ) {
        // Financial summary card (only for existing projects)
        if (!isNew) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WsElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, WsBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ProjectFigure(
                        label = "ORÇAMENTO",
                        value = budget?.let { MoneyFormatter.format(it) } ?: "Não definido",
                        color = WsTextSecondary
                    )
                    VerticalDivider(modifier = Modifier.height(40.dp), color = WsBorder)
                    ProjectFigure(
                        label = "REALIZADO",
                        value = MoneyFormatter.format(realized),
                        color = if (budget != null && realized > budget) WsDanger else WsSuccess
                    )
                    VerticalDivider(modifier = Modifier.height(40.dp), color = WsBorder)
                    ProjectFigure(
                        label = "EXECUÇÃO",
                        value = pct?.let { "%.1f%%".format(it) } ?: "—",
                        color = when {
                            pct == null  -> WsTextSecondary
                            pct > 100    -> WsDanger
                            pct > 85     -> WsWarning
                            else         -> WsSuccess
                        }
                    )
                }
            }
        }

        // Identificação
        DetailSection("Identificação") {
            WsTextField("CÓDIGO", code, modifier = Modifier.fillMaxWidth()) { code = it }
            WsTextField("NOME DO PROJETO", name, modifier = Modifier.fillMaxWidth()) { name = it }
            WsSelectField(
                label      = "STATUS",
                options    = statusOptions,
                selectedId = status.ordinal,
                onSelect   = { id -> id?.let { status = ProjectStatus.entries[it] } },
                nullable   = false
            )
            WsTextField(
                "DESCRIÇÃO",
                description,
                modifier = Modifier.fillMaxWidth()
            ) { description = it }
        }

        // Financeiro
        DetailSection("Financeiro") {
            WsMoneyField(
                label          = "ORÇAMENTO PREVISTO",
                value          = budgetStr,
                modifier       = Modifier.fillMaxWidth(),
                onValueChange  = { budgetStr = it }
            )
        }

        // Período
        DetailSection("Período") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WsDateField("DATA INÍCIO", startStr, modifier = Modifier.weight(1f)) { startStr = it }
                WsDateField("PREVISÃO FIM", endStr, modifier = Modifier.weight(1f)) { endStr = it }
            }
            if (status == ProjectStatus.CONCLUIDO || status == ProjectStatus.CANCELADO) {
                WsDateField(
                    "DATA ${if (status == ProjectStatus.CONCLUIDO) "CONCLUSÃO" else "CANCELAMENTO"}",
                    actualEndStr,
                    modifier = Modifier.fillMaxWidth()
                ) { actualEndStr = it }
            }
        }
    }
}

@Composable
private fun ProjectFigure(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

private fun parseDate(s: String): LocalDate? {
    val digits = s.trim().filter { it.isDigit() }
    if (digits.length == 8) {
        runCatching { return LocalDate.parse(digits, DateTimeFormatter.ofPattern("ddMMyyyy")) }.getOrNull()?.let { return it }
    }
    return runCatching { LocalDate.parse(s.trim(), dateFmt) }.getOrNull()
}
