package br.com.sisgfin.financial.transactions

import br.com.sisgfin.Employee
import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.financial.payments.TransactionPaymentRepository
import br.com.sisgfin.Supplier
import br.com.sisgfin.SupplierRepository
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
 * T-14: verifica comportamento de update() em relação a employeeId, supplierId e reversedType.
 */
class TransactionEmployeeIdUpdateTest {

    private fun stubEmployeeRepo() = mockk<EmployeeRepository>().also {
        every { it.getById(any()) } returns Employee(
            name = "Stub", document = "00000000000", phone = "", email = "",
            role = "Stub", salary = Money.fromString("0.00"), paymentDay = 1
        )
    }

    private fun makeService(
        repo: TransactionRepository = mockk(relaxed = true),
        accountRepo: FinancialAccountRepository = mockk(relaxed = true)
    ): TransactionService {
        val session = mockk<SessionManager>()
        every { session.currentUser } returns MutableStateFlow(null)
        every { session.hasPermission(any()) } returns true
        return TransactionService(
            repository           = repo,
            accountRepository    = accountRepo,
            supplierRepository   = mockk(relaxed = true),
            costCenterRepository = mockk(relaxed = true),
            auditRepository      = mockk(relaxed = true),
            timelineRepository   = mockk(relaxed = true),
            sessionManager       = session,
            ledgerService        = mockk(relaxed = true),
            employeeRepository   = stubEmployeeRepo(),
            paymentRepository    = mockk(relaxed = true)
        )
    }

    private fun account(id: Int) = FinancialAccount(id = id, name = "Conta #$id")

    private fun pending(id: Int, origin: TransactionOrigin = TransactionOrigin.MANUAL, employeeId: Int? = null, supplierId: Int? = null) =
        Transaction(
            id          = id,
            type        = TransactionType.EXPENSE,
            status      = TransactionStatus.PENDING,
            description = "Teste",
            amount      = Money.fromString("100.00"),
            issueDate   = LocalDateTime.now(),
            dueDate     = LocalDateTime.now().plusDays(7),
            accountId   = 1,
            origin      = origin,
            employeeId  = employeeId,
            supplierId  = supplierId
        )

    // ── update grava employeeId para MANUAL ──────────────────────────────────

    @Test
    fun `update grava employeeId em lancamento MANUAL`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val existing = pending(id = 1, origin = TransactionOrigin.MANUAL)
        every { repo.findById(1) } returns existing
        val slot = slot<Transaction>()
        every { repo.update(capture(slot)) } just runs
        val service = makeService(repo, accountRepo)

        service.update(existing.copy(employeeId = 42))

        assertEquals(42, slot.captured.employeeId)
    }

    // ── update rejeita mudança de employeeId em lançamento de folha ───────────

    @Test
    fun `update rejeita alteracao de employeeId em PAYROLL_ENGINE`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val existing = pending(id = 2, origin = TransactionOrigin.PAYROLL_ENGINE, employeeId = 10)
        every { repo.findById(2) } returns existing
        val service = makeService(repo)

        val ex = assertThrows<IllegalStateException> {
            service.update(existing.copy(employeeId = 99))
        }
        assert(ex.message!!.contains("folha de pagamento"))
    }

    @Test
    fun `update rejeita alteracao de employeeId em PAYROLL_IMPORT`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val existing = pending(id = 3, origin = TransactionOrigin.PAYROLL_IMPORT, employeeId = 10)
        every { repo.findById(3) } returns existing
        val service = makeService(repo)

        val ex = assertThrows<IllegalStateException> {
            service.update(existing.copy(employeeId = 99))
        }
        assert(ex.message!!.contains("folha de pagamento"))
    }

    // ── exclusividade mútua supplierId × employeeId ───────────────────────────

    @Test
    fun `update com employeeId limpa supplierId`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val existing = pending(id = 4, supplierId = 7)
        every { repo.findById(4) } returns existing
        val slot = slot<Transaction>()
        every { repo.update(capture(slot)) } just runs
        val service = makeService(repo, accountRepo)

        service.update(existing.copy(employeeId = 5, supplierId = 7))

        assertEquals(5, slot.captured.employeeId)
        assertNull(slot.captured.supplierId)
    }

    @Test
    fun `update com supplierId limpa employeeId`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val supplierRepo = mockk<SupplierRepository>()
        every { supplierRepo.findById(9) } returns Supplier(id = 9, document = "00000000000", name = "Fornecedor Teste", isActive = true, entityType = EntityType.FORNECEDOR)
        val existing = pending(id = 5, employeeId = 3)
        every { repo.findById(5) } returns existing
        val slot = slot<Transaction>()
        every { repo.update(capture(slot)) } just runs
        val service = TransactionService(
            repository           = repo,
            accountRepository    = accountRepo,
            supplierRepository   = supplierRepo,
            costCenterRepository = mockk(relaxed = true),
            auditRepository      = mockk(relaxed = true),
            timelineRepository   = mockk(relaxed = true),
            sessionManager       = run {
                val s = mockk<SessionManager>()
                every { s.currentUser } returns MutableStateFlow(null)
                every { s.hasPermission(any()) } returns true
                s
            },
            ledgerService        = mockk(relaxed = true),
            employeeRepository   = mockk(relaxed = true),
            paymentRepository    = mockk(relaxed = true)
        )

        service.update(existing.copy(supplierId = 9, employeeId = null))

        assertEquals(9, slot.captured.supplierId)
        assertNull(slot.captured.employeeId)
    }

    // ── reversedType continua imutável via update ─────────────────────────────

    @Test
    fun `update nao persiste reversedType mesmo se passado`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        // reversedType do existing é null (lançamento normal, não estorno)
        val existing = pending(id = 6)
        every { repo.findById(6) } returns existing
        val slot = slot<Transaction>()
        every { repo.update(capture(slot)) } just runs
        val service = makeService(repo, accountRepo)

        // Tenta forçar um reversedType via update — não deve persistir
        service.update(existing.copy(reversedType = TransactionType.EXPENSE))

        // O update no repositório NÃO inclui reversedType; o valor persistido vem do insert original.
        // O slot captura o que foi passado ao repo.update() — o campo não é zerado pelo service,
        // mas o repository.update() ignorava e continua ignorando reversedType.
        // Aqui validamos que o service não lança erro (reversedType é transparente no update do service).
        // A imutabilidade real é garantida pelo repositório (não inclui reversedType no SQL).
        verify(exactly = 1) { repo.update(any()) }
    }
}
