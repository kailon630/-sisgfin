package br.com.sisgfin.financial.transactions

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.financial.money.Money
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * M5-A, D5 — transferência como evento consumado.
 *
 * Pernas nascem PAID com paymentDate preenchida.
 * insertTransferPairWithBaixas é chamado (não insertTransferPair).
 * recordPayment em TRANSFER lança (guard D5).
 */
class TransferAsEventTest {

    private val transferDate: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

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
            employeeRepository   = mockk(relaxed = true),
            paymentRepository    = mockk(relaxed = true)
        )
    }

    private fun account(id: Int) = FinancialAccount(id = id, name = "Conta #$id")

    // ── 1. perna de origem nasce PAID ─────────────────────────────────────────

    @Test
    fun `createTransfer source nasce com status PAID (D5)`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val sourceSlot = slot<Transaction>()
        every { repo.insertTransferPairWithBaixas(capture(sourceSlot), any(), any(), any(), any()) } returns (1 to 2)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED")

        assertEquals(TransactionStatus.PAID, sourceSlot.captured.status)
        assertNotNull(sourceSlot.captured.paymentDate)
    }

    // ── 2. perna de destino nasce PAID ────────────────────────────────────────

    @Test
    fun `createTransfer destination nasce com status PAID (D5)`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>()
        val destSlot = slot<Transaction>()
        every { repo.insertTransferPairWithBaixas(any(), capture(destSlot), any(), any(), any()) } returns (1 to 2)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED")

        assertEquals(TransactionStatus.PAID, destSlot.captured.status)
        assertNotNull(destSlot.captured.paymentDate)
    }

    // ── 3. insertTransferPairWithBaixas chamado (não insertTransferPair) ───────

    @Test
    fun `createTransfer chama insertTransferPairWithBaixas e nao insertTransferPair`() {
        val accountRepo = mockk<FinancialAccountRepository>()
        every { accountRepo.findById(any()) } returns account(1)
        val repo = mockk<TransactionRepository>(relaxed = true)
        every { repo.insertTransferPairWithBaixas(any(), any(), any(), any(), any()) } returns (1 to 2)
        val service = makeService(repo = repo, accountRepo = accountRepo)

        service.createTransfer(1, 2, Money.fromString("500.00"), transferDate, "TED")

        verify(exactly = 1) { repo.insertTransferPairWithBaixas(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { repo.insertTransferPair(any(), any()) }
    }

    // ── 4. recordPayment em TRANSFER rejeita (guard D5) ───────────────────────

    @Test
    fun `recordPayment em lancamento TRANSFER lanca IllegalStateException (D5)`() {
        val repo = mockk<TransactionRepository>()
        val transferTx = Transaction(
            id          = 1,
            type        = TransactionType.TRANSFER,
            status      = TransactionStatus.PENDING,
            description = "TED",
            amount      = Money.fromString("500.00"),
            issueDate   = transferDate,
            dueDate     = transferDate,
            accountId   = 1
        )
        every { repo.findById(1) } returns transferTx
        val service = makeService(repo = repo)

        val ex = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            service.recordPayment(1, transferDate, Money.fromString("500.00"))
        }
        assert(ex.message!!.contains("Transferência") || ex.message!!.contains("TRANSFER"))
        verify(exactly = 0) { repo.updateWithPayment(any(), any()) }
    }
}
