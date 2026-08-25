package br.com.sisgfin.financial.payments

import br.com.sisgfin.SessionManager
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionService
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M2 — dual-write atômico de baixas em recordPayment.
 *
 * Verifica que recordPayment:
 * - Delega ao repository.updateWithPayment() quando paymentRepository está presente
 * - Cria TransactionPayment com valores corretos
 * - Continua refletindo paidAmount no título (dual-write)
 * - D-PRINCIPAL (B): desconto de 50 em título de 1000 (operador paga 950) → PAID,
 *   paidAmount acumulado = 1000 (face), principalAmount na baixa = 1000 (face).
 * - Idempotência: mesma chamada duas vezes envia mesmo idempotencyKey ao repositório
 * - Duas baixas separadas geram dois chamadas a updateWithPayment
 * - Se updateWithPayment lança, exceção não é engolida (atomicidade)
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
            ledgerService        = mockk(relaxed = true),
            employeeRepository   = mockk(relaxed = true),
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
        val slot = slot<TransactionPayment>()
        every { repo.updateWithPayment(any(), capture(slot)) } returns true

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
        every { repo.updateWithPayment(capture(updatedSlot), any()) } returns true

        makeService(repo, paymentRepo).recordPayment(1, paymentDate, Money.fromString("1000.00"))

        assertEquals(0, Money.fromString("1000.00").compareTo(updatedSlot.captured.paidAmount!!))
        assertEquals(TransactionStatus.PAID, updatedSlot.captured.status)
    }

    // ── D-PRINCIPAL (B): desconto quita o título; paidAmount = face ──────────

    @Test
    fun `titulo de 1000 operador paga 950 com desconto 50 vira PAID paidAmount e 1000 face`() {
        // D-PRINCIPAL (B): principal_amount = face = cash + desconto = 950 + 50 = 1000.
        // paidAmount acumulado no título = face = 1000 (outstandingPrincipal = 0).
        // cashEffective = 950 (o que saiu da conta bancária).
        val repo        = mockk<TransactionRepository>()
        val paymentRepo = mockk<TransactionPaymentRepository>(relaxed = true)
        every { repo.findById(1) } returns expense()
        val updatedSlot = slot<Transaction>()
        val paymentSlot = slot<TransactionPayment>()
        every { repo.updateWithPayment(capture(updatedSlot), capture(paymentSlot)) } returns true

        makeService(repo, paymentRepo).recordPayment(
            id              = 1,
            paymentDate     = paymentDate,
            paidAmount      = Money.fromString("950.00"),
            discountAmount  = Money.fromString("50.00")
        )

        assertEquals(TransactionStatus.PAID, updatedSlot.captured.status)
        // paidAmount = face acumulado = 1000 (não 950)
        assertEquals(0, Money.fromString("1000.00").compareTo(updatedSlot.captured.paidAmount!!))
        // principal_amount na baixa = face = 1000
        assertEquals(0, Money.fromString("1000.00").compareTo(paymentSlot.captured.principalAmount))
        // discount_amount na baixa = 50
        assertEquals(0, Money.fromString("50.00").compareTo(paymentSlot.captured.discountAmount))
    }

    // ── idempotência: mesma chave enviada ao repositório nas duas chamadas ────

    @Test
    fun `segunda chamada identica envia mesmo idempotencyKey para repositorio gerenciar deduplicacao`() {
        val repo        = mockk<TransactionRepository>()
        val paymentRepo = mockk<TransactionPaymentRepository>(relaxed = true)
        every { repo.findById(1) } returns expense()
        val capturedPayments = mutableListOf<TransactionPayment>()
        every { repo.updateWithPayment(any(), capture(capturedPayments)) } returns true

        val service = makeService(repo, paymentRepo)
        service.recordPayment(1, paymentDate, Money.fromString("1000.00"))
        service.recordPayment(1, paymentDate, Money.fromString("1000.00"))

        assertEquals(2, capturedPayments.size)
        assertEquals(
            capturedPayments[0].idempotencyKey,
            capturedPayments[1].idempotencyKey,
            "Mesmo idempotencyKey nas duas chamadas — repo decide deduplicação"
        )
    }

    // ── duas baixas separadas ────────────────────────────────────────────────

    @Test
    fun `baixa parcial seguida de quitacao gera duas chamadas a updateWithPayment`() {
        val repo        = mockk<TransactionRepository>()
        val paymentRepo = mockk<TransactionPaymentRepository>(relaxed = true)

        every { repo.findById(1) } returnsMany listOf(
            expense(status = TransactionStatus.PENDING),
            expense(status = TransactionStatus.PARTIAL, paidAmount = "400.00")
        )
        every { repo.updateWithPayment(any(), any()) } returns true

        val service = makeService(repo, paymentRepo)
        service.recordPayment(1, paymentDate, Money.fromString("400.00"))
        service.recordPayment(1, paymentDate.plusDays(1), Money.fromString("600.00"))

        verify(exactly = 2) { repo.updateWithPayment(any(), any()) }
    }

    // ── idempotency_key gerada e não nula ────────────────────────────────────

    @Test
    fun `updateWithPayment recebe payment com idempotencyKey nao nula`() {
        val repo        = mockk<TransactionRepository>()
        val paymentRepo = mockk<TransactionPaymentRepository>(relaxed = true)
        every { repo.findById(1) } returns expense()
        val slot = slot<TransactionPayment>()
        every { repo.updateWithPayment(any(), capture(slot)) } returns true

        makeService(repo, paymentRepo).recordPayment(1, paymentDate, Money.fromString("1000.00"))

        assertTrue(slot.captured.idempotencyKey != null, "idempotencyKey deve ser preenchida")
    }

    // ── atomicidade: falha em updateWithPayment propaga sem efeito colateral ──

    @Test
    fun `se updateWithPayment lanca excecao recordPayment nao engole o erro`() {
        val repo        = mockk<TransactionRepository>()
        val paymentRepo = mockk<TransactionPaymentRepository>(relaxed = true)
        every { repo.findById(1) } returns expense()
        every { repo.updateWithPayment(any(), any()) } throws RuntimeException("falha simulada no insert")

        assertThrows<RuntimeException> {
            makeService(repo, paymentRepo).recordPayment(1, paymentDate, Money.fromString("1000.00"))
        }
        // Caminho legado não foi acionado — o update atômico é o único caminho
        verify(exactly = 0) { repo.update(any()) }
    }
}
