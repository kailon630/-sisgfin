package br.com.sisgfin.financial.payments

import br.com.sisgfin.AuditLog
import br.com.sisgfin.AuditRepository
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.PaymentReversalResult
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionService
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.timeline.TimelineEventType
import br.com.sisgfin.financial.transactions.timeline.TransactionTimelineEvent
import br.com.sisgfin.financial.transactions.timeline.TransactionTimelineRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

/**
 * M5-A, D1: TransactionService.reversePayment — estorno de baixa individual.
 *
 * Testa: permissão, validação de justificativa, delegação ao repositório,
 * timeline PAYMENT_REVERSED e propagação de transição de status.
 */
class ReversePaymentTest {

    private fun makeService(
        repo: TransactionRepository = mockk(relaxed = true),
        withPermission: Boolean = true,
        timeline: TransactionTimelineRepository = mockk(relaxed = true),
        audit: AuditRepository = mockk(relaxed = true)
    ): Triple<TransactionService, TransactionTimelineRepository, AuditRepository> {
        val session = mockk<SessionManager>()
        every { session.currentUser } returns MutableStateFlow(null)
        every { session.hasPermission(any()) } returns withPermission
        val service = TransactionService(
            repository           = repo,
            accountRepository    = mockk(relaxed = true),
            supplierRepository   = mockk(relaxed = true),
            costCenterRepository = mockk(relaxed = true),
            auditRepository      = audit,
            timelineRepository   = timeline,
            sessionManager       = session,
            ledgerService        = mockk(relaxed = true),
            employeeRepository   = mockk(relaxed = true),
            paymentRepository    = mockk(relaxed = true)
        )
        return Triple(service, timeline, audit)
    }

    private fun stubResult(
        repo: TransactionRepository,
        paymentId: Int = 1,
        previousStatus: TransactionStatus = TransactionStatus.PAID,
        newStatus: TransactionStatus = TransactionStatus.PENDING
    ): PaymentReversalResult {
        val result = PaymentReversalResult(
            transactionId         = 42,
            originalCashEffective = Money.fromString("500.00"),
            correctionId          = 99,
            previousStatus        = previousStatus,
            newStatus             = newStatus
        )
        every { repo.reversePaymentAndUpdateTitle(paymentId, any(), any(), any()) } returns result
        return result
    }

    // ── 1. permissão ─────────────────────────────────────────────────────────

    @Test
    fun `reversePayment sem permissao lanca SecurityException`() {
        val (service) = makeService(withPermission = false)
        assertThrows<SecurityException> {
            service.reversePayment(1, "motivo válido")
        }
    }

    // ── 2 e 3. validação de justificativa ────────────────────────────────────

    @Test
    fun `reversePayment com justificativa em branco lanca IllegalArgumentException`() {
        val (service) = makeService()
        assertThrows<IllegalArgumentException> {
            service.reversePayment(1, "")
        }
    }

    @Test
    fun `reversePayment com justificativa so espacos lanca IllegalArgumentException`() {
        val (service) = makeService()
        assertThrows<IllegalArgumentException> {
            service.reversePayment(1, "   ")
        }
    }

    // ── 4. delegação ao repositório ──────────────────────────────────────────

    @Test
    fun `reversePayment delega ao repositorio com paymentId e justificativa corretos`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val (service) = makeService(repo = repo)
        stubResult(repo, paymentId = 7)

        service.reversePayment(7, "erro de digitação")

        verify(exactly = 1) { repo.reversePaymentAndUpdateTitle(7, "erro de digitação", null, any()) }
    }

    // ── 5. timeline PAYMENT_REVERSED ─────────────────────────────────────────

    @Test
    fun `reversePayment adiciona timeline PAYMENT_REVERSED`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val timeline = mockk<TransactionTimelineRepository>(relaxed = true)
        val (service) = makeService(repo = repo, timeline = timeline)
        stubResult(repo)

        service.reversePayment(1, "correção de valor")

        verify(exactly = 1) {
            timeline.insert(match { event: TransactionTimelineEvent ->
                event.eventType == TimelineEventType.PAYMENT_REVERSED
            })
        }
    }

    // ── 6. audit PAYMENT_REVERSED ─────────────────────────────────────────────

    @Test
    fun `reversePayment registra audit com acao PAYMENT_REVERSED`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val auditRepo = mockk<AuditRepository>(relaxed = true)
        val (service) = makeService(repo = repo, audit = auditRepo)
        stubResult(repo)

        service.reversePayment(1, "duplicata")

        verify(exactly = 1) {
            auditRepo.insert(match { log: AuditLog ->
                log.action == "PAYMENT_REVERSED"
            })
        }
    }

    // ── 7. propagação PAID → PENDING no timeline ──────────────────────────────

    @Test
    fun `reversePayment propaga transicao PAID para PENDING no evento de timeline`() {
        val repo = mockk<TransactionRepository>(relaxed = true)
        val timeline = mockk<TransactionTimelineRepository>(relaxed = true)
        val (service) = makeService(repo = repo, timeline = timeline)
        stubResult(repo, previousStatus = TransactionStatus.PAID, newStatus = TransactionStatus.PENDING)

        service.reversePayment(1, "única baixa estornada")

        verify(exactly = 1) {
            timeline.insert(match { event: TransactionTimelineEvent ->
                event.eventType == TimelineEventType.PAYMENT_REVERSED &&
                event.statusFrom == TransactionStatus.PAID &&
                event.statusTo   == TransactionStatus.PENDING
            })
        }
    }
}
