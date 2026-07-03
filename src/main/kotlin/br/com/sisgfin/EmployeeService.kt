package br.com.sisgfin

import br.com.sisgfin.core.crud.CrudOperations
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
        if (employee.id == 0) {
            val newId = repository.insert(employee)
            lastPayrollResult = if (employee.effectivePaymentDays().isNotEmpty())
                payrollEngine.generateForEmployee(newId)
            else emptyList()
        } else {
            repository.update(employee)
            lastPayrollResult = if (employee.effectivePaymentDays().isNotEmpty())
                payrollEngine.generateForEmployee(employee.id)
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
