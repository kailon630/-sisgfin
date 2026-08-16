package br.com.sisgfin.financial.transactions

import br.com.sisgfin.Employee
import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.Supplier
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.financial.payments.TransactionPaymentRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.suppliers.EntityType
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T-08/T-09: validateEmployee — funcionário inativo rejeitado em create e update;
 * fornecedor inativo continua rejeitado (RN-02 intacta).
 */
class TransactionEmployeeValidationTest {

    private fun makeService(
        repo: TransactionRepository = mockk(relaxed = true),
        accountRepo: FinancialAccountRepository = mockk(relaxed = true),
        supplierRepo: SupplierRepository = mockk(relaxed = true),
        employeeRepo: EmployeeRepository = mockk(relaxed = true)
    ): TransactionService {
        val session = mockk<SessionManager>()
        every { session.currentUser } returns MutableStateFlow(null)
        every { session.hasPermission(any()) } returns true
        return TransactionService(
            repository           = repo,
            accountRepository    = accountRepo,
            supplierRepository   = supplierRepo,
            costCenterRepository = mockk(relaxed = true),
            auditRepository      = mockk(relaxed = true),
            timelineRepository   = mockk(relaxed = true),
            sessionManager       = session,
            ledgerService        = mockk(relaxed = true),
            employeeRepository   = employeeRepo,
            paymentRepository    = mockk(relaxed = true)
        )
    }

    private fun account(id: Int) = FinancialAccount(id = id, name = "Conta #$id")

    private fun employee(id: Int, active: Boolean) = Employee(
        id = id, name = "Fulano $id", document = "00000000000", phone = "", email = "",
        role = "Dev", salary = Money.fromString("3000.00"), paymentDay = 5, active = active
    )

    private fun supplier(id: Int, active: Boolean) = Supplier(
        id = id, document = "00000000000", name = "Fornecedor $id", isActive = active, entityType = EntityType.FORNECEDOR
    )

    private fun baseExpense(accountId: Int = 1) = Transaction(
        type = TransactionType.EXPENSE,
        status = TransactionStatus.PENDING,
        description = "Teste",
        amount = Money.fromString("500.00"),
        issueDate = LocalDateTime.now(),
        dueDate = LocalDateTime.now().plusDays(7),
        accountId = accountId
    )

    // ── create com employeeId ativo grava OK ─────────────────────────────────

    @Test
    fun `create com employeeId ativo grava sem erro`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val empRepo = mockk<EmployeeRepository>()
        every { empRepo.getById(10) } returns employee(10, active = true)
        val repo = mockk<TransactionRepository>(relaxed = true)
        every { repo.insert(any()) } returns 1
        val service = makeService(repo = repo, accountRepo = accountRepo, employeeRepo = empRepo)

        service.create(baseExpense().copy(employeeId = 10))

        verify(exactly = 1) { repo.insert(any()) }
    }

    // ── create com funcionário inativo lança exceção ──────────────────────────

    @Test
    fun `create com funcionario inativo lanca IllegalArgumentException`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val empRepo = mockk<EmployeeRepository>()
        every { empRepo.getById(99) } returns employee(99, active = false)
        val service = makeService(accountRepo = accountRepo, employeeRepo = empRepo)

        val ex = assertThrows<IllegalArgumentException> {
            service.create(baseExpense().copy(employeeId = 99))
        }
        assert(ex.message!!.contains("inativo"))
    }

    // ── update com funcionário inativo lança exceção ──────────────────────────

    @Test
    fun `update com funcionario inativo lanca IllegalArgumentException`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val empRepo = mockk<EmployeeRepository>()
        every { empRepo.getById(99) } returns employee(99, active = false)
        val existing = baseExpense().copy(id = 5)
        val repo = mockk<TransactionRepository>(relaxed = true)
        every { repo.findById(5) } returns existing
        val service = makeService(repo = repo, accountRepo = accountRepo, employeeRepo = empRepo)

        val ex = assertThrows<IllegalArgumentException> {
            service.update(existing.copy(employeeId = 99))
        }
        assert(ex.message!!.contains("inativo"))
    }

    // ── fornecedor inativo continua sendo rejeitado (RN-02) ──────────────────

    @Test
    fun `create com fornecedor inativo continua sendo rejeitado`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val supRepo = mockk<SupplierRepository>()
        every { supRepo.findById(7) } returns supplier(7, active = false)
        val service = makeService(accountRepo = accountRepo, supplierRepo = supRepo)

        val ex = assertThrows<IllegalArgumentException> {
            service.create(baseExpense().copy(supplierId = 7))
        }
        assert(ex.message!!.contains("inativo"))
    }

    // ── engines não quebram: PayrollEngine usa create() com funcionário ativo ─

    @Test
    fun `create com employeeId PAYROLL_ENGINE e funcionario ativo nao lanca excecao`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val empRepo = mockk<EmployeeRepository>()
        every { empRepo.getById(5) } returns employee(5, active = true)
        val repo = mockk<TransactionRepository>(relaxed = true)
        every { repo.insert(any()) } returns 1
        val service = makeService(repo = repo, accountRepo = accountRepo, employeeRepo = empRepo)

        service.create(baseExpense().copy(employeeId = 5, origin = TransactionOrigin.PAYROLL_ENGINE))

        verify(exactly = 1) { repo.insert(any()) }
    }
}
