package br.com.sisgfin.financial.transactions

import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.SupplierRepository

/**
 * F5 — Batch resolution of supplier/employee names for a transaction list.
 * Single DB round-trip per entity type; callers never make N+1 lookups.
 */
class CounterpartyMap(
    private val suppliers: Map<Int, String>,
    private val employees: Map<Int, String>
) {
    fun nameFor(tx: Transaction): String? =
        tx.supplierId?.let { suppliers[it] }
            ?: tx.employeeId?.let { employees[it] }

    companion object {
        val EMPTY = CounterpartyMap(emptyMap(), emptyMap())
    }
}

class CounterpartyResolver(
    private val supplierRepository: SupplierRepository,
    private val employeeRepository: EmployeeRepository
) {
    fun resolve(transactions: List<Transaction>): CounterpartyMap {
        val supplierIds = transactions.mapNotNull { it.supplierId }.toSet()
        val employeeIds = transactions.mapNotNull { it.employeeId }.toSet()
        val suppliers = if (supplierIds.isNotEmpty())
            supplierRepository.findByIds(supplierIds).associate { it.id to it.name }
        else emptyMap()
        val employees = if (employeeIds.isNotEmpty())
            employeeRepository.getByIds(employeeIds).associate { it.id to it.name }
        else emptyMap()
        return CounterpartyMap(suppliers, employees)
    }
}
