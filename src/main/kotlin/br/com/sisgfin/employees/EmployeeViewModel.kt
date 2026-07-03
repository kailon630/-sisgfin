package br.com.sisgfin.employees

import br.com.sisgfin.Employee
import br.com.sisgfin.EmployeeService
import br.com.sisgfin.core.crud.BaseCrudViewModel
import br.com.sisgfin.core.crud.CrudEvent
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class EmployeeViewModel(
    private val employeeService: EmployeeService,
    private val transactionRepository: TransactionRepository
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

    override fun load() {
        super.load()
        loadNextPaymentDates()
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
    fun openEmployeeDialog(employee: Employee? = null) = openDialog(employee)
}
