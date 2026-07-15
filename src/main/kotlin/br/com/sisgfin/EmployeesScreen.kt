package br.com.sisgfin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.financial.banking.BankList
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
                    text = "Novo Funcionário",
                    icon = Icons.Default.Add,
                    onClick = { 
                        viewModel.openEmployeeDialog()
                        onShowRightPanel { EmployeeEditorPanel(viewModel, onCloseRightPanel) }
                    }
                )
                
                WsIconButton(Icons.Default.Refresh, onClick = { viewModel.loadEmployees() })
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
                    TableHeaderCell("BANCO", Modifier.weight(0.7f), TextAlign.Center)
                    TableHeaderCell("STATUS", Modifier.weight(0.7f), TextAlign.Center)
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
        Column(modifier = Modifier.weight(2f)) {
            Text(employee.name, style = MaterialTheme.typography.bodyLarge)
            val empType = EmploymentType.entries.firstOrNull { it.label == employee.employmentType }
            if (empType != null) {
                Text(empType.label, style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
            }
        }
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

        Box(modifier = Modifier.weight(0.7f), contentAlignment = Alignment.Center) {
            if (employee.hasBankingData) {
                Surface(
                    color = WsSuccess.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "✓",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = WsSuccess,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Surface(
                    color = WsWarning.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "⚠",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = WsWarning,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(0.7f), contentAlignment = Alignment.Center) {
            val statusColor = if (employee.active) WsSuccess else WsTextDisabled
            Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun EmployeeEditorPanel(viewModel: EmployeeViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val employee = uiState.selectedItem ?: return

    val initialType = EmploymentType.entries.firstOrNull { it.label == employee.employmentType }
        ?: EmploymentType.CLT

    var selectedType    by remember(employee.id) { mutableStateOf(initialType) }
    var name            by remember(employee.id) { mutableStateOf(employee.name) }
    var document        by remember(employee.id) { mutableStateOf(employee.document) }
    var email           by remember(employee.id) { mutableStateOf(employee.email) }
    var phone           by remember(employee.id) { mutableStateOf(employee.phone) }
    var role            by remember(employee.id) { mutableStateOf(employee.role) }
    var salary          by remember(employee.id) { mutableStateOf(employee.salary.toString()) }
    var selectedPaymentDays by remember(employee.id) {
        mutableStateOf(
            employee.paymentDays
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it in 1..31 }
                ?.toSet() ?: emptySet()
        )
    }
    var bankCode       by remember(employee.id) { mutableStateOf(employee.bankCode) }
    var agencyNumber   by remember(employee.id) { mutableStateOf(employee.agencyNumber ?: "") }
    var agencyDv       by remember(employee.id) { mutableStateOf(employee.agencyDv ?: "") }
    var accountNumber  by remember(employee.id) { mutableStateOf(employee.accountNumber ?: "") }
    var accountDv      by remember(employee.id) { mutableStateOf(employee.accountDv ?: "") }
    var accountType    by remember(employee.id) { mutableStateOf(employee.accountType ?: "CS") }

    val bankOptions = BankList.allAsOptions()
    val selectedBankIdx = bankCode?.let { BankList.indexByCode(it) }
    val accountTypeOptions: List<Pair<Int, String>> = listOf(
        0 to "CS — Conta Salário",
        1 to "CC — Conta Corrente",
        2 to "CP — Conta Poupança"
    )
    val accountTypeCodes = listOf("CS", "CC", "CP")
    val selectedAccountTypeIdx = accountTypeCodes.indexOf(accountType).takeIf { it >= 0 }

    val nameLabel = if (selectedType == EmploymentType.PJ) "RAZÃO SOCIAL" else "NOME COMPLETO"
    val roleLabel = when (selectedType) {
        EmploymentType.PJ -> "SERVIÇO / DESCRIÇÃO"
        EmploymentType.ESTAGIO -> "CURSO / ÁREA"
        else -> "CARGO / FUNÇÃO"
    }
    val salaryLabel = when (selectedType) {
        EmploymentType.PJ -> "VALOR DOS HONORÁRIOS (R$)"
        EmploymentType.ESTAGIO -> "VALOR DA BOLSA (R$)"
        else -> "SALÁRIO BRUTO (R$)"
    }
    val docAllowedTypes = if (selectedType == EmploymentType.PJ)
        setOf(DocumentType.CNPJ)
    else
        setOf(DocumentType.CPF)

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
                    paymentDay     = selectedPaymentDays.minOrNull() ?: employee.paymentDay,
                    paymentDays    = selectedPaymentDays.sorted().joinToString(",").ifEmpty { null },
                    employmentType = selectedType.label,
                    bankCode       = bankCode,
                    agencyNumber   = agencyNumber.ifBlank { null },
                    agencyDv       = agencyDv.ifBlank { null },
                    accountNumber  = accountNumber.ifBlank { null },
                    accountDv      = accountDv.ifBlank { null },
                    accountType    = accountType.ifBlank { null }
                )
            )
        }
    ) {
        Text("TIPO DE VÍNCULO", style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EmploymentType.entries.forEach { type ->
                WsFilterChip(
                    selected = selectedType == type,
                    onClick = {
                        if (selectedType != type) {
                            val wasPj = selectedType == EmploymentType.PJ
                            val isPj = type == EmploymentType.PJ
                            if (wasPj != isPj) document = ""
                            selectedType = type
                        }
                    },
                    label = { Text(type.label) }
                )
            }
        }

        HorizontalDivider(color = WsBorder, modifier = Modifier.padding(vertical = 4.dp))

        WsTextField(nameLabel, name) { name = it }
        WsDocumentField(
            value = document,
            onValueChange = { document = it },
            allowedTypes = docAllowedTypes,
            stateKey = "${employee.id}_${selectedType.name}"
        )
        WsTextField(roleLabel, role) { role = it }
        WsTextField("E-MAIL", email) { email = it }
        WsTextField("TELEFONE", phone) { phone = it }
        WsTextField(salaryLabel, salary) { salary = it }

        Text(
            "DIAS DE PAGAMENTO AUTOMÁTICO",
            style = MaterialTheme.typography.labelSmall,
            color = WsTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
        DayPickerChips(
            selectedDays = selectedPaymentDays,
            onToggle = { day ->
                selectedPaymentDays = if (day in selectedPaymentDays)
                    selectedPaymentDays - day
                else
                    selectedPaymentDays + day
            }
        )
        if (selectedPaymentDays.isNotEmpty()) {
            Text(
                "Lançamentos gerados automaticamente nos dias: ${selectedPaymentDays.sorted().joinToString(", ")} de cada mês",
                style = MaterialTheme.typography.labelSmall,
                color = WsAccent
            )
        }

        HorizontalDivider(color = WsBorder)
        Text(
            "Dados Bancários",
            style = MaterialTheme.typography.titleSmall,
            color = WsTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
        WsSelectField(
            label      = "BANCO",
            options    = bankOptions,
            selectedId = selectedBankIdx,
            onSelect   = { idx -> bankCode = idx?.let { BankList.codeByIndex(it) } },
            placeholder = "Selecionar banco...",
            nullable   = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WsTextField("AGÊNCIA", agencyNumber, Modifier.weight(1f)) { agencyNumber = it }
            WsTextField("DV", agencyDv, Modifier.weight(0.3f)) { agencyDv = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WsTextField("CONTA", accountNumber, Modifier.weight(1f)) { accountNumber = it }
            WsTextField("DV", accountDv, Modifier.weight(0.3f)) { accountDv = it }
        }
        WsSelectField(
            label      = "TIPO DE CONTA",
            options    = accountTypeOptions,
            selectedId = selectedAccountTypeIdx,
            onSelect   = { idx -> accountType = if (idx != null) accountTypeCodes[idx] else "CS" },
            nullable   = false
        )
        if (bankCode == null || agencyNumber.isBlank() || accountNumber.isBlank()) {
            Text(
                "⚠ Preencha os dados bancários para habilitar a geração de remessa.",
                style = MaterialTheme.typography.labelSmall,
                color = WsWarning
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
private fun DayPickerChips(
    selectedDays: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..31).chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { day ->
                    val selected = day in selectedDays
                    Surface(
                        onClick = { onToggle(day) },
                        shape = RoundedCornerShape(4.dp),
                        color = if (selected) WsAccent else Color.Transparent,
                        border = if (!selected) BorderStroke(1.dp, WsBorder) else null,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = day.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White else WsTextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmployeePopup(employee: Employee?, onSave: (Employee) -> Unit, onCancel: () -> Unit) {
    if (employee == null) return

    val initialType = EmploymentType.entries.firstOrNull { it.label == employee.employmentType }
        ?: EmploymentType.CLT

    var selectedType by remember(employee.id) { mutableStateOf(initialType) }
    var name         by remember(employee.id) { mutableStateOf(employee.name) }
    var document     by remember(employee.id) { mutableStateOf(employee.document) }
    var email        by remember(employee.id) { mutableStateOf(employee.email) }
    var phone        by remember(employee.id) { mutableStateOf(employee.phone) }
    var role         by remember(employee.id) { mutableStateOf(employee.role) }
    var salary       by remember(employee.id) { mutableStateOf(employee.salary.toString()) }
    var paymentDay   by remember(employee.id) { mutableStateOf(employee.paymentDay.toString()) }

    val nameLabel   = if (selectedType == EmploymentType.PJ) "RAZÃO SOCIAL" else "NOME COMPLETO"
    val roleLabel   = when (selectedType) {
        EmploymentType.PJ     -> "SERVIÇO / DESCRIÇÃO"
        EmploymentType.ESTAGIO -> "CURSO / ÁREA"
        else                   -> "CARGO / FUNÇÃO"
    }
    val salaryLabel = when (selectedType) {
        EmploymentType.PJ     -> "HONORÁRIOS (R$)"
        EmploymentType.ESTAGIO -> "BOLSA (R$)"
        else                   -> "SALÁRIO (R$)"
    }
    val docAllowedTypes = if (selectedType == EmploymentType.PJ)
        setOf(DocumentType.CNPJ) else setOf(DocumentType.CPF)

    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(8.dp),
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
                Text("TIPO DE VÍNCULO", style = MaterialTheme.typography.labelSmall, color = WsTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmploymentType.entries.forEach { type ->
                        WsFilterChip(
                            selected = selectedType == type,
                            onClick = {
                                if (selectedType != type) {
                                    val wasPj = selectedType == EmploymentType.PJ
                                    val isPj = type == EmploymentType.PJ
                                    if (wasPj != isPj) document = ""
                                    selectedType = type
                                }
                            },
                            label = { Text(type.label) }
                        )
                    }
                }

                WsTextField(nameLabel, name) { name = it }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WsDocumentField(
                        value = document,
                        onValueChange = { document = it },
                        allowedTypes = docAllowedTypes,
                        modifier = Modifier.weight(1f),
                        stateKey = "${employee.id}_${selectedType.name}"
                    )
                    WsTextField(roleLabel, role, Modifier.weight(1f)) { role = it }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WsTextField("E-MAIL", email, Modifier.weight(1f)) { email = it }
                    WsTextField("TELEFONE", phone, Modifier.weight(1f)) { phone = it }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WsTextField(salaryLabel, salary, Modifier.weight(1f)) { salary = it }
                    WsTextField("DIA PGTO", paymentDay, Modifier.weight(0.6f)) { paymentDay = it }
                }
            }
        },
        confirmButton = {
            WsButton(
                text = "Salvar Registro",
                onClick = {
                    onSave(employee.copy(
                        name           = name,
                        document       = document,
                        email          = email,
                        phone          = phone,
                        role           = role,
                        salary         = salary.toMoney(),
                        paymentDay     = paymentDay.toIntOrNull() ?: 1,
                        employmentType = selectedType.label
                    ))
                }
            )
        },
        dismissButton = {
            WsButton("Descartar", variant = WsButtonVariant.TERTIARY, onClick = onCancel)
        }
    )
}
