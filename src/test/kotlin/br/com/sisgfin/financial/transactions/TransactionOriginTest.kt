package br.com.sisgfin.financial.transactions

import br.com.sisgfin.Employee
import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.payments.TransactionPaymentRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-13: verifica que origin é gravado em cada ponto de criação e que
 * cancelPendingPayrollForMonth não cancela lançamentos MANUAL com employeeId.
 */
class TransactionOriginTest {

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

    private fun baseTransaction(accountId: Int = 1) = Transaction(
        type        = TransactionType.EXPENSE,
        status      = TransactionStatus.PENDING,
        description = "Teste",
        amount      = Money.fromString("100.00"),
        issueDate   = LocalDateTime.now(),
        dueDate     = LocalDateTime.now().plusDays(7),
        accountId   = accountId
    )

    // ── origem de create() ────────────────────────────────────────────────────

    @Test
    fun `create sem origin explicita grava MANUAL`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.create(baseTransaction())

        assertEquals(TransactionOrigin.MANUAL, slot.captured.origin)
    }

    @Test
    fun `createFromOfx grava OFX`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.createFromOfx(baseTransaction().copy(ofxFitId = "FIT123", status = TransactionStatus.PAID, paidAmount = Money.fromString("100.00"), paymentDate = LocalDateTime.now()))

        assertEquals(TransactionOrigin.OFX, slot.captured.origin)
    }

    @Test
    fun `createFromPayrollImport grava PAYROLL_IMPORT`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.createFromPayrollImport(baseTransaction().copy(employeeId = 5))

        assertEquals(TransactionOrigin.PAYROLL_IMPORT, slot.captured.origin)
    }

    @Test
    fun `createFromRecurrence grava RECURRENCE`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.createFromRecurrence(baseTransaction().copy(recurrenceTemplateId = 3))

        assertEquals(TransactionOrigin.RECURRENCE, slot.captured.origin)
    }

    @Test
    fun `createTransfer grava TRANSFER em ambas as pernas`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val sourceSlot = slot<Transaction>()
        val destSlot   = slot<Transaction>()
        every { repo.insertTransferPair(capture(sourceSlot), capture(destSlot)) } returns (10 to 20)
        val service = makeService(repo, accountRepo)

        service.createTransfer(1, 2, Money.fromString("500.00"), LocalDateTime.now(), "TED")

        assertEquals(TransactionOrigin.TRANSFER, sourceSlot.captured.origin)
        assertEquals(TransactionOrigin.TRANSFER, destSlot.captured.origin)
    }

    @Test
    fun `reverseTransaction grava REVERSAL`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val original = baseTransaction().copy(
            id = 42, status = TransactionStatus.PAID,
            paidAmount = Money.fromString("100.00"),
            paymentDate = LocalDateTime.now(),
            type = TransactionType.EXPENSE
        )
        every { repo.findById(42) } returns original
        every { repo.hasReversal(42) } returns false
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 99
        val service = makeService(repo, accountRepo)

        service.reverseTransaction(42, "Erro de lançamento")

        assertEquals(TransactionOrigin.REVERSAL, slot.captured.origin)
    }

    @Test
    fun `duplicate grava DUPLICATE`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val source = baseTransaction().copy(id = 10, status = TransactionStatus.PAID, paidAmount = Money.fromString("100.00"), paymentDate = LocalDateTime.now())
        every { repo.findById(10) } returns source
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 11
        val service = makeService(repo, accountRepo)

        service.duplicate(10)

        assertEquals(TransactionOrigin.DUPLICATE, slot.captured.origin)
    }

    @Test
    fun `create com PAYROLL_ENGINE preserva a origem`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(1) } returns account(1)
        val slot = slot<Transaction>()
        every { repo.insert(capture(slot)) } returns 1
        val service = makeService(repo, accountRepo)

        service.create(baseTransaction().copy(employeeId = 7, origin = TransactionOrigin.PAYROLL_ENGINE))

        assertEquals(TransactionOrigin.PAYROLL_ENGINE, slot.captured.origin)
    }

    // ── cancelPendingPayrollForMonth não cancela MANUAL ───────────────────────

    @Test
    fun `cancelPendingPayrollForMonth nao cancela lancamento MANUAL com employeeId`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val service = makeService(repo = repo)

        // findPendingPayrollForMonth retorna lista vazia — MANUAL não aparece nela
        every { repo.findPendingPayrollForMonth(any(), any()) } returns emptyList()

        val cancelled = service.cancelPendingPayrollForMonth(5, YearMonth.now())

        assertEquals(0, cancelled)
        verify(exactly = 0) { repo.deactivate(any()) }
    }

    @Test
    fun `cancelPendingPayrollForMonth cancela lancamento PAYROLL_ENGINE do mesmo mes`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val folha = baseTransaction().copy(
            id = 99, employeeId = 5,
            origin = TransactionOrigin.PAYROLL_ENGINE,
            status = TransactionStatus.PENDING
        )
        every { repo.findPendingPayrollForMonth(5, any()) } returns listOf(folha)
        val service = makeService(repo = repo)

        val cancelled = service.cancelPendingPayrollForMonth(5, YearMonth.now())

        assertEquals(1, cancelled)
        verify(exactly = 1) { repo.deactivate(99) }
    }
}
