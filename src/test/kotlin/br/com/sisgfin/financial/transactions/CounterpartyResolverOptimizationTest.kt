package br.com.sisgfin.financial.transactions

import br.com.sisgfin.Employee
import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.Supplier
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.suppliers.EntityType
import io.mockk.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T-11 — CounterpartyResolver.resolve() deve usar findByIds/getByIds
 * em vez de findAll/getAll para evitar carga de tabelas inteiras.
 */
class CounterpartyResolverOptimizationTest {

    private fun tx(id: Int, supplierId: Int? = null, employeeId: Int? = null) = Transaction(
        id          = id,
        type        = TransactionType.EXPENSE,
        status      = TransactionStatus.PENDING,
        description = "Tx $id",
        amount      = Money.fromString("100.00"),
        issueDate   = LocalDateTime.now(),
        dueDate     = LocalDateTime.now().plusDays(7),
        accountId   = 1,
        supplierId  = supplierId,
        employeeId  = employeeId
    )

    private fun supplier(id: Int, name: String) = Supplier(
        id         = id,
        document   = "00000000000$id",
        name       = name,
        entityType = EntityType.FORNECEDOR,
        isActive   = true
    )

    private fun employee(id: Int, name: String) = Employee(
        id         = id,
        name       = name,
        document   = "00000000000$id",
        phone      = "",
        email      = "",
        role       = "",
        salary     = Money.fromString("0.00"),
        paymentDay = 5
    )

    @Test
    fun `resolve com supplierId chama findByIds nao findAll`() {
        val supplierRepo = mockk<SupplierRepository>()
        val employeeRepo = mockk<EmployeeRepository>()
        every { supplierRepo.findByIds(setOf(5)) } returns listOf(supplier(5, "Acme Ltda"))

        val resolver = CounterpartyResolver(supplierRepo, employeeRepo)
        val map = resolver.resolve(listOf(tx(1, supplierId = 5)))

        assertEquals("Acme Ltda", map.nameFor(tx(1, supplierId = 5)))
        verify(exactly = 1) { supplierRepo.findByIds(setOf(5)) }
        verify(exactly = 0) { supplierRepo.findAll() }
    }

    @Test
    fun `resolve com employeeId chama getByIds nao getAll`() {
        val supplierRepo = mockk<SupplierRepository>()
        val employeeRepo = mockk<EmployeeRepository>()
        every { employeeRepo.getByIds(setOf(3)) } returns listOf(employee(3, "João Silva"))

        val resolver = CounterpartyResolver(supplierRepo, employeeRepo)
        val map = resolver.resolve(listOf(tx(1, employeeId = 3)))

        assertEquals("João Silva", map.nameFor(tx(1, employeeId = 3)))
        verify(exactly = 1) { employeeRepo.getByIds(setOf(3)) }
        verify(exactly = 0) { employeeRepo.getAll() }
    }

    @Test
    fun `resolve sem ids nao consulta repositorios`() {
        val supplierRepo = mockk<SupplierRepository>()
        val employeeRepo = mockk<EmployeeRepository>()

        val resolver = CounterpartyResolver(supplierRepo, employeeRepo)
        val map = resolver.resolve(listOf(tx(1)))

        assertNull(map.nameFor(tx(1)))
        verify(exactly = 0) { supplierRepo.findByIds(any()) }
        verify(exactly = 0) { employeeRepo.getByIds(any()) }
    }

    @Test
    fun `resolve carrega apenas os ids presentes nas transacoes`() {
        val supplierRepo = mockk<SupplierRepository>()
        val employeeRepo = mockk<EmployeeRepository>()
        every { supplierRepo.findByIds(setOf(1, 2)) } returns listOf(
            supplier(1, "Alpha"), supplier(2, "Beta")
        )

        val resolver = CounterpartyResolver(supplierRepo, employeeRepo)
        val txs = listOf(tx(10, supplierId = 1), tx(11, supplierId = 2))
        val map = resolver.resolve(txs)

        assertEquals("Alpha", map.nameFor(tx(10, supplierId = 1)))
        assertEquals("Beta",  map.nameFor(tx(11, supplierId = 2)))
        verify(exactly = 1) { supplierRepo.findByIds(setOf(1, 2)) }
    }
}
