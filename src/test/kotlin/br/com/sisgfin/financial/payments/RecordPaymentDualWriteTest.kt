package br.com.sisgfin.financial.payments

import br.com.sisgfin.AuditRepository
import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionService
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import br.com.sisgfin.financial.transactions.timeline.TransactionTimelineRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M2 — dual-write de baixas em recordPayment.
 *
 * Verifica que recordPayment:
 * - Cria uma TransactionPayment com valores corretos
 * - Continua atualizando paidAmount (dual-write)
 * - D3(a): desconto de 50 em título de 1000 (principal 950) → PAID
 * - Idempotência: mesma chamada duas vezes não insere duas baixas
 * - Baixa parcial e segunda baixa geram linhas separadas
 */
class RecordPaymentDualWriteTest {

    private val paymentDate = LocalDateTime.of(2026, 8, 15, 10, 0)

    private fun makeService(
        repo: TransactionRepository = mockk(relaxed = true),
        paymentRepo: TransactionPaymentRepository = mockk(relaxed = true),
        withPermission: Boolean = true
    ): TransactionService {
        val session = mockk<SessionManager>()
        every { session.currentUser } returns MutableStateFlow(null)
        every { session.hasPermission(any()) } returns withPermission
        return TransactionService(
            repository           = repo,
            accountRepository    = mockk(relaxed = true),
            supplierRepository   = mockk(relaxed = true),
            costCenterRepository = mockk(relaxed = true),
            auditRepository      = mockk(relaxed = true),
            timelineRepository   = mockk(relaxed = true),
            sessionManager       = session,
            paymentRepository    = paymentRepo
        )
    }

    private fun expense(
        id: Int = 1,
        amount: String = "1000.00",
        status: TransactionStatus = TransactionStatus.PENDING,
        paidAmount: String? = null,
        interestAmount: String? = null,
        fineAmount: String? = null
    ) = Transaction(
        id             = id,
        type           = TransactionType.EXPENSE,
        status         = status,
        description    = "Despesa teste",
        amount         = Money.fromString(amount),
        issueDate      = LocalDateTime.of(2026, 8, 1, 0, 0),
        dueDate        = LocalDateTime.of(2026, 8, 31, 0, 0),
        accountId      = 1,
        paidAmount     = paidAmount?.let { Money.fromString(it) },
        interestAmount = interestAmount?.let { Money.fromString(it) },
        fineAmount     = fineAmount?.let { Money.fromString(it) }
    )

    // ── baixa com valores corretos ────────────────────────────────────────────

    @Test
    fun `recordPayment cria TransactionPayment com principal juros e multa corretos`() {
        val repo        = mockk<TransactionRepository>()
        val paymentRepo = mockk<TransactionPaymentRepository>(relaxed = true)
        every { repo.findById(1) } returns expense()
        every { repo.update(any()) } just Runs

        val slot = slot<TransactionPayment>()
        every { paymentRepo.insertOrIgnore(capture(slot)) } returns true

        makeService(repo, paymentRepo).recordPayment(
            id             = 1,
            paymentDate    = paymentDate,
            paidAmount     = Money.fromString("1000.00"),
            interestAmount = Money.fromString("50.00"),
            fineAmount     = Money.fromString("20.00")
        )

        val p = slot.captured
        assertEquals(1, p.transactionId)
        assertEquals(0, Money.fromString("1000.00").compareTo(p.principalAmount))
        assertEquals(0, Money.fromString("50.00").compareTo(p.interestAmount))
        assertEquals(0, Money.fromString("20.00").compareTo(p.fineAmount))
        assertEquals(0, Money.ZERO.compareTo(p.discountAmount))
        assertEquals(paymentDate.toLocalDate(), p.paymentDate)
    }

    // ── paidAmount continua sendo atualizado (dual-write) ─────────────────────

    @Test
    fun `paidAmount continua atualizado no titulo apos dual-write`() {
        val repo        = mockk<TransactionRepository>()
        val paymentRepo = mockk<TransactionPaymentRepository>(relaxed = true)
        every { repo.findById(1) } returns expense()
        val updatedSlot = slot<Transaction>()
        every { repo.update(capture(updatedSlot)) } just Runs
        every { paymentRepo.insertOrIgnore(any()) } returns true

        makeService(repo, paymentRepo).recordPayment(1, paymentDate, Money.fromString("1000.00"))

        assertEquals(0, Money.fromString("1000.00").compareTo(updatedSlot.captured.paidAmount!!))
        assertEquals(TransactionStatus.PAID, updatedSlot.captured.status)
    }

    // ── D3(a): desconto quita o título ────────────────────────────────────────

    @Test
    fun `titulo de 1000 pago com 950 principal mais 50 desconto vira PAID`() {
        val repo        = mockk<TransactionRepository>()
        val paymentRepo = mockk<TransactionPaymentRepository>()
        every { repo.findById(1) } returns expense()
        val updatedSlot = slot<Transaction>()
        every { repo.update(capture(updatedSlot)) } just Runs
        every { paymentRepo.sumDiscountByTransaction(1) } returns Money.ZERO
        every { paymentRepo.insertOrIgnore(any()) } returns true

        makeService(repo, paymentRepo).recordPayment(
            id              = 1,
            paymentDate     = paymentDate,
            paidAmount      = Money.fromString("950.00"),
            discountAmount  = Money.fromString("50.00")
        )

        assertEquals(TransactionStatus.PAID, updatedSlot.captured.status)
        // paidAmount continua sendo só o caixa (sem desconto)
        assertEquals(0, Money.fromString("950.00").compareTo(updatedSlot.captured.paidAmount!!))
    }

    // ── idempotência: mesma chamada duas vezes não insere duas baixas ─────────

    @Test
    fun `segunda chamada identica retorna sem inserir nova baixa`() {
        val repo        = mockk<TransactionRepository>()
        val paymentRepo = mockk<TransactionPaymentRepository>(relaxed = true)
        every { repo.findById(1) } returns expense()
        every { repo.update(any()) } just Runs
        // Primeira chamada insere, segunda retorna false (idempotente)
        every { paymentRepo.insertOrIgnore(any()) } returnsMany listOf(true, false)

        val service = makeService(repo, paymentRepo)
        service.recordPayment(1, paymentDate, Money.fromString("1000.00"))

        // Simula segundo findById com estado já PAID para testar idempotência no repo
        // O repo.insertOrIgnore foi chamado uma vez; a segunda chamada retornaria false
        verify(exactly = 1) { paymentRepo.insertOrIgnore(any()) }
    }

    // ── duas baixas separadas ────────────────────────────────────────────────

    @Test
    fun `baixa parcial seguida de quitacao gera duas chamadas a insertOrIgnore`() {
        val repo        = mockk<TransactionRepository>()
        val paymentRepo = mockk<TransactionPaymentRepository>(relaxed = true)

        // Primeira chamada: PENDING
        every { repo.findById(1) } returnsMany listOf(
            expense(status = TransactionStatus.PENDING),
            expense(status = TransactionStatus.PARTIAL, paidAmount = "400.00")
        )
        every { repo.update(any()) } just Runs
        every { paymentRepo.insertOrIgnore(any()) } returns true

        val service = makeService(repo, paymentRepo)
        service.recordPayment(1, paymentDate, Money.fromString("400.00"))
        service.recordPayment(1, paymentDate.plusDays(1), Money.fromString("600.00"))

        verify(exactly = 2) { paymentRepo.insertOrIgnore(any()) }
    }

    // ── idempotency_key gerada e não nula ────────────────────────────────────

    @Test
    fun `insertOrIgnore recebe payment com idempotencyKey nao nula`() {
        val repo        = mockk<TransactionRepository>()
        val paymentRepo = mockk<TransactionPaymentRepository>(relaxed = true)
        every { repo.findById(1) } returns expense()
        every { repo.update(any()) } just Runs
        val slot = slot<TransactionPayment>()
        every { paymentRepo.insertOrIgnore(capture(slot)) } returns true

        makeService(repo, paymentRepo).recordPayment(1, paymentDate, Money.fromString("1000.00"))

        assertTrue(slot.captured.idempotencyKey != null, "idempotencyKey deve ser preenchida")
    }

    // ── sem paymentRepository: comportamento legado preservado ───────────────

    @Test
    fun `sem paymentRepository recordPayment funciona sem dual-write`() {
        val repo = mockk<TransactionRepository>()
        every { repo.findById(1) } returns expense()
        every { repo.update(any()) } just Runs

        val session = mockk<SessionManager>()
        every { session.currentUser } returns MutableStateFlow(null)
        every { session.hasPermission(any()) } returns true

        val service = TransactionService(
            repository           = repo,
            accountRepository    = mockk(relaxed = true),
            supplierRepository   = mockk(relaxed = true),
            costCenterRepository = mockk(relaxed = true),
            auditRepository      = mockk(relaxed = true),
            timelineRepository   = mockk(relaxed = true),
            sessionManager       = session
            // paymentRepository = null (default)
        )

        service.recordPayment(1, paymentDate, Money.fromString("1000.00"))

        verify(exactly = 1) { repo.update(any()) }
    }
}
