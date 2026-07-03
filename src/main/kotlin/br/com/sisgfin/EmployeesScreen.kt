package br.com.sisgfin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.financial.money.MoneyFormatter
import br.com.sisgfin.financial.money.toMoney
import br.com.sisgfin.employees.EmployeeViewModel
import br.com.sisgfin.core.ui.notifications.CrudEventEffects
import br.com.sisgfin.core.ui.panel.BaseCrudPanel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun EmployeesScreen(
    viewModel: EmployeeViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val nextPaymentDates by viewModel.nextPaymentDates.collectAsState()
    CrudEventEffects(viewModel)

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Toolbar da Tela
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Funcionários",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    "Gestão de colaboradores e folha de pagamento",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WsButton(
                    label = "Novo Funcionário",
                    icon = Icons.Default.Add,
                    onClick = { 
                        viewModel.openEmployeeDialog()
                        onShowRightPanel { EmployeeEditorPanel(viewModel, onCloseRightPanel) }
                    }
                )
                
                WsIconButton(Icons.Default.Refresh) { viewModel.loadEmployees() }
            }
        }

        // Tabela Moderna
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WsElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableHeaderCell("NOME", Modifier.weight(2f))
                    TableHeaderCell("CARGO", Modifier.weight(1.5f))
                    TableHeaderCell("E-MAIL", Modifier.weight(2f))
                    TableHeaderCell("SALÁRIO", Modifier.weight(1f), TextAlign.End)
                    TableHeaderCell("PRÓX. PGTO", Modifier.weight(1f), TextAlign.Center)
                    TableHeaderCell("STATUS", Modifier.weight(0.8f), TextAlign.Center)
                }
                HorizontalDivider(color = WsBorder)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.items) { emp ->
                        EmployeeRow(
                            employee = emp,
                            nextPaymentDate = nextPaymentDates[emp.id],
                            isSelected = uiState.selectedItem?.id == emp.id,
                            onSingleClick = {
                                viewModel.selectEmployee(emp)
                                onShowRightPanel { EmployeeEditorPanel(viewModel, onCloseRightPanel) }
                            },
                            onDoubleClick = {
                                viewModel.openDialog(emp)
                            }
                        )
                        HorizontalDivider(color = WsBorder.copy(alpha = 0.5f))
                    }
                }
            }
        }

        if (uiState.isDialogVisible) {
             // Reuse the logic for the dialog popup from original implementation
             // We can use a simple wrapper for now or reuse EmployeeDialog if it existed
             EmployeePopup(
                 employee = uiState.selectedItem,
                 onSave = { viewModel.saveEmployee(it) },
                 onCancel = { viewModel.closeDialog() }
             )
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// Item 3: substituído por WsTableRow com hover real via hoverable + MutableInteractionSource
@Composable
fun EmployeeRow(
    employee: Employee,
    nextPaymentDate: LocalDate?,
    isSelected: Boolean,
    onSingleClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    WsTableRow(
        selected = isSelected,
        onClick = onSingleClick
    ) {
        Text(employee.name, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyLarge)
        Text(employee.role, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium)
        Text(employee.email, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = MoneyFormatter.format(employee.salary),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Medium
        )

        val hasPayroll = employee.effectivePaymentDays().isNotEmpty()
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (hasPayroll) {
                Text(
                    text = nextPaymentDate?.format(dateFormatter) ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = if (nextPaymentDate != null) WsTextPrimary else WsTextSecondary
                )
            } else {
                Text("—", style = MaterialTheme.typography.bodyMedium, color = WsTextDisabled, textAlign = TextAlign.Center)
            }
        }

        Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
            val statusColor = if (employee.active) WsSuccess else WsTextDisabled
            Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun EmployeeEditorPanel(viewModel: EmployeeViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val employee = uiState.selectedItem ?: return

    var name           by remember(employee.id) { mutableStateOf(employee.name) }
    var document       by remember(employee.id) { mutableStateOf(employee.document) }
    var email          by remember(employee.id) { mutableStateOf(employee.email) }
    var phone          by remember(employee.id) { mutableStateOf(employee.phone) }
    var role           by remember(employee.id) { mutableStateOf(employee.role) }
    var salary         by remember(employee.id) { mutableStateOf(employee.salary.toString()) }
    var paymentDay     by remember(employee.id) { mutableStateOf(employee.paymentDay.toString()) }
    var paymentDays    by remember(employee.id) { mutableStateOf(employee.paymentDays ?: "") }
    var employmentType by remember(employee.id) { mutableStateOf(employee.employmentType ?: "") }

    // Opções indexadas por ordinal para o WsSelectField
    val employmentTypeOptions: List<Pair<Int, String>> =
        EmploymentType.entries.mapIndexed { i, e -> i to e.label }
    val selectedEmploymentIdx: Int? =
        EmploymentType.entries.indexOfFirst { it.label == employmentType }.takeIf { it >= 0 }

    BaseCrudPanel(
        title = if (employee.id == 0) "Novo Funcionário" else "Detalhes",
        subtitle = "Informações cadastrais",
        onClose = onClose,
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        onSave = {
            viewModel.saveEmployee(
                employee.copy(
                    name           = name,
                    document       = document,
                    email          = email,
                    phone          = phone,
                    role           = role,
                    salary         = salary.toMoney(),
                    paymentDay     = paymentDay.toIntOrNull() ?: 1,
                    paymentDays    = paymentDays.ifBlank { null },
                    employmentType = employmentType.ifBlank { null }
                )
            )
        }
    ) {
        WsTextField("NOME COMPLETO", name) { name = it }
        WsTextField("DOCUMENTO", document) { document = it }
        WsTextField("CARGO / FUNÇÃO", role) { role = it }
        WsTextField("E-MAIL", email) { email = it }
        WsTextField("TELEFONE", phone) { phone = it }
        WsSelectField(
            label       = "TIPO DE VÍNCULO",
            options     = employmentTypeOptions,
            selectedId  = selectedEmploymentIdx,
            onSelect    = { idx -> employmentType = if (idx != null) EmploymentType.entries[idx].label else "" },
            placeholder = "Selecionar...",
            nullable    = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WsTextField("SALÁRIO (R$)", salary, Modifier.weight(1f)) { salary = it }
            WsTextField("DIA PGTO", paymentDay, Modifier.weight(0.6f)) { paymentDay = it }
        }
        WsTextField(
            label = "DIAS DE PGTO AUTOMÁTICO",
            value = paymentDays,
            onValueChange = { paymentDays = it }
        )
        if (paymentDays.isNotBlank()) {
            val days = paymentDays.split(",").mapNotNull { it.trim().toIntOrNull() }
            androidx.compose.material3.Text(
                "Lançamentos gerados automaticamente nos dias: ${days.joinToString(", ")} de cada mês",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = WsAccent
            )
        }
        if (employee.id != 0) {
            TextButton(
                onClick = { viewModel.toggleEmployeeActive(employee.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (employee.active) "Desativar Funcionário" else "Ativar Funcionário",
                    color = if (employee.active) WsDanger else WsSuccess,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun EmployeePopup(employee: Employee?, onSave: (Employee) -> Unit, onCancel: () -> Unit) {
    if (employee == null) return

    var name by remember { mutableStateOf(employee.name) }
    var document by remember { mutableStateOf(employee.document) }
    var email by remember { mutableStateOf(employee.email) }
    var phone by remember { mutableStateOf(employee.phone) }
    var role by remember { mutableStateOf(employee.role) }
    var salary by remember { mutableStateOf(employee.salary.toString()) }
    var paymentDay by remember { mutableStateOf(employee.paymentDay.toString()) }

    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(8.dp), // Cantos menos arredondados para visual Workstation
        containerColor = WsSurface,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(600.dp).padding(16.dp),
        title = {
            Column {
                Text(
                    if (employee.id == 0) "Novo Cadastro" else "Edição de Funcionário",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "Foco total na edição do registro",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) {
                WsTextField("NOME COMPLETO", name) { name = it }
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WsTextField("DOCUMENTO", document, Modifier.weight(1f)) { document = it }
                    WsTextField("CARGO / FUNÇÃO", role, Modifier.weight(1f)) { role = it }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WsTextField("E-MAIL", email, Modifier.weight(1f)) { email = it }
                    WsTextField("TELEFONE", phone, Modifier.weight(1f)) { phone = it }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WsTextField("SALÁRIO (R$)", salary, Modifier.weight(1f)) { salary = it }
                    WsTextField("DIA PGTO", paymentDay, Modifier.weight(0.6f)) { paymentDay = it }
                }
            }
        },
        confirmButton = {
            WsButton(
                label = "Salvar Registro",
                onClick = {
                    onSave(employee.copy(
                        name = name,
                        document = document,
                        email = email,
                        phone = phone,
                        role = role,
                        salary = salary.toMoney(),
                        paymentDay = paymentDay.toIntOrNull() ?: 1
                    ))
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Descartar", color = WsTextSecondary)
            }
        }
    )
}
