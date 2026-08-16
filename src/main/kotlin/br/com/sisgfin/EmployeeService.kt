package br.com.sisgfin

import br.com.sisgfin.core.crud.CrudOperations
import br.com.sisgfin.core.validation.DocumentValidator
import br.com.sisgfin.employees.PayrollEngine
import br.com.sisgfin.employees.PayrollGenerationResult

class EmployeeService(
    private val repository: EmployeeRepository,
    private val payrollEngine: PayrollEngine
) : CrudOperations<Employee> {

    var lastPayrollResult: List<PayrollGenerationResult> = emptyList()
        private set

    override fun listAll(): List<Employee> = repository.getAll()

    override fun save(employee: Employee) {
        val emp = employee.copy(document = DocumentValidator.normalize(employee.document))
        if (emp.id == 0) {
            val newId = repository.insert(emp)
            lastPayrollResult = if (emp.effectivePaymentDays().isNotEmpty())
                payrollEngine.generateForEmployee(newId)
            else emptyList()
        } else {
            val existing = repository.getById(emp.id)
            repository.update(emp)
            val paymentDaysChanged = existing?.effectivePaymentDays() != emp.effectivePaymentDays()
            lastPayrollResult = if (paymentDaysChanged && emp.effectivePaymentDays().isNotEmpty())
                payrollEngine.generateForEmployee(emp.id)
            else emptyList()
        }
    }

    override fun toggleActive(id: Int) {
        val employee = repository.getById(id) ?: return
        val updated = employee.copy(active = !employee.active)
        repository.update(updated)
        lastPayrollResult = if (updated.active && updated.effectivePaymentDays().isNotEmpty())
            payrollEngine.generateForEmployee(id)
        else emptyList()
    }
}
