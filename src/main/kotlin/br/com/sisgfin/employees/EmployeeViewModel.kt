package br.com.sisgfin.employees

import br.com.sisgfin.Employee
import br.com.sisgfin.EmployeeService
import br.com.sisgfin.core.crud.BaseCrudViewModel
import br.com.sisgfin.core.crud.CrudEvent
import br.com.sisgfin.engine.EngineOrchestrator
import br.com.sisgfin.engine.EngineRun
import br.com.sisgfin.engine.EngineRunStatus
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

class EmployeeViewModel(
    private val employeeService: EmployeeService,
    private val transactionRepository: TransactionRepository,
    private val engineOrchestrator: EngineOrchestrator
) : BaseCrudViewModel<Employee>(
    operations = employeeService,
    emptyFactory = {
        Employee(
            name = "",
            document = "",
            phone = "",
            email = "",
            role = "",
            salary = Money.ZERO,
            paymentDay = 1
        )
    },
    itemFilter = { item, query ->
        val q = query.lowercase()
        item.name.lowercase().contains(q) ||
            item.role.lowercase().contains(q) ||
            item.email.lowercase().contains(q)
    }
) {
    private val _nextPaymentDates = MutableStateFlow<Map<Int, LocalDate?>>(emptyMap())
    val nextPaymentDates: StateFlow<Map<Int, LocalDate?>> = _nextPaymentDates.asStateFlow()

    private val _payrollRun = MutableStateFlow<EngineRun?>(null)
    val payrollRun: StateFlow<EngineRun?> = _payrollRun.asStateFlow()

    private val _payrollRunning = MutableStateFlow(false)
    val payrollRunning: StateFlow<Boolean> = _payrollRunning.asStateFlow()

    override fun load() {
        super.load()
        loadNextPaymentDates()
        loadPayrollStatus()
    }

    private fun loadPayrollStatus() {
        viewModelScope.launch {
            _payrollRun.value = withContext(Dispatchers.IO) {
                engineOrchestrator.findLastPayrollRun(YearMonth.now())
            }
        }
    }

    fun runPayrollNow() {
        if (_payrollRunning.value) return
        _payrollRunning.value = true
        viewModelScope.launch {
            val run = withContext(Dispatchers.IO) {
                engineOrchestrator.runPayrollForMonth(YearMonth.now())
            }
            _payrollRun.value = run
            _payrollRunning.value = false
            val created = run.created
            if (created > 0) {
                emitEvent(CrudEvent.ShowSnackbar("$created lançamento(s) criado(s) na folha do mês"))
            }
            if (run.status == EngineRunStatus.FAILED) {
                emitEvent(CrudEvent.ShowSnackbar("Falha ao gerar folha: ${run.error}"))
            }
        }
    }

    override suspend fun onSaveSuccess() {
        val result = employeeService.lastPayrollResult
        val generated = result.sumOf { it.generated }
        if (generated > 0) {
            val names = result.filter { it.generated > 0 }.joinToString(", ") { it.employeeName }
            emitEvent(CrudEvent.ShowSnackbar(
                "$generated lançamento(s) criado(s) no contas a pagar para $names"
            ))
        }
    }

    private fun loadNextPaymentDates() {
        viewModelScope.launch {
            val dates = withContext(Dispatchers.IO) {
                uiState.value.items
                    .filter { it.effectivePaymentDays().isNotEmpty() }
                    .associate { emp ->
                        emp.id to transactionRepository.findNextPendingForEmployee(emp.id)
                    }
            }
            _nextPaymentDates.value = dates
        }
    }

    fun loadEmployees() = load()
    fun selectEmployee(employee: Employee) = select(employee)
    fun saveEmployee(employee: Employee) = save(employee)
    fun toggleEmployeeActive(id: Int) = toggleActive(id)
    fun openNewEmployee() = openNew()
    fun openEmployeeDialog(employee: Employee? = null) {
        onAction(br.com.sisgfin.core.crud.CrudAction.ClosePanel)
        openDialog(employee)
    }
}
