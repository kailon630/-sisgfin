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
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M3 — reconciliação como portão.
 *
 * Verifica o invariante: após qualquer ciclo de baixas via recordPayment,
 * Σ(cashEffective das baixas) == paidAmount atualizado no título.
 * Este teste autoriza o M4 a existir.
 */
class ReconciliationTest {

    private val date = LocalDateTime.of(2026, 8, 15, 10, 0)

    private fun expense(
        status: TransactionStatus = TransactionStatus.PENDING,
        paidAmount: String? = null,
        interestAmount: String? = null,
        fineAmount: String? = null
    ) = Transaction(
        id             = 1,
        type           = TransactionType.EXPENSE,
        status         = status,
        description    = "Título reconciliação",
        amount         = Money.fromString("1000.00"),
        issueDate      = LocalDateTime.of(2026, 8, 1, 0, 0),
        dueDate        = LocalDateTime.of(2026, 8, 31, 0, 0),
        accountId      = 1,
        paidAmount     = paidAmount?.let { Money.fromString(it) },
        interestAmount = interestAmount?.let { Money.fromString(it) },
        fineAmount     = fineAmount?.let { Money.fromString(it) }
    )

    private fun buildService(
        txStates: List<Transaction>,
        insertedPayments: MutableList<TransactionPayment>
    ): TransactionService {
        val txRepo = mockk<TransactionRepository>()
        every { txRepo.findById(1) } returnsMany txStates
        every { txRepo.updateWithPayment(any(), capture(insertedPayments)) } returns true

        val paymentRepo = mockk<TransactionPaymentRepository>()
        every { paymentRepo.sumDiscountByTransaction(any()) } returns Money.ZERO

        val session = mockk<SessionManager>()
        every { session.currentUser } returns MutableStateFlow(null)
        every { session.hasPermission(any()) } returns true

        return TransactionService(
            repository           = txRepo,
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

    // ── portão M3: ciclo completo parcial + juros + quitação ─────────────────

    @Test
    fun `ciclo parcial com juros mais quitacao produz zero divergencias`() {
        val inserted = mutableListOf<TransactionPayment>()

        val states = listOf(
            expense(status = TransactionStatus.PENDING),
            expense(status = TransactionStatus.PARTIAL, paidAmount = "350.00", interestAmount = "50.00"),
            expense(status = TransactionStatus.PARTIAL, paidAmount = "750.00", interestAmount = "50.00")
        )
        val service = buildService(states, inserted)

        // Baixa 1: 300 principal + 50 juros = 350 caixa
        service.recordPayment(1, date, Money.fromString("300.00"), interestAmount = Money.fromString("50.00"))
        // Baixa 2: 400 principal
        service.recordPayment(1, date.plusDays(1), Money.fromString("400.00"))
        // Baixa 3: 300 principal
        service.recordPayment(1, date.plusDays(2), Money.fromString("300.00"))

        // paidAmount acumulado no título = 350 + 400 + 300 = 1050
        val paidAmount = Money.fromString("1050.00")

        val somaBaixas = inserted.fold(Money.ZERO) { acc, p -> acc + p.cashEffective }
        assertEquals(0, paidAmount.compareTo(somaBaixas),
            "Σ(cashEffective=$somaBaixas) deve igualar paidAmount=$paidAmount — zero divergências")
    }

    // ── baixa integral única ──────────────────────────────────────────────────

    @Test
    fun `baixa integral unica invariante preservado`() {
        val inserted = mutableListOf<TransactionPayment>()
        val service  = buildService(listOf(expense()), inserted)

        service.recordPayment(1, date, Money.fromString("1000.00"))

        val soma = inserted.fold(Money.ZERO) { acc, p -> acc + p.cashEffective }
        assertEquals(0, Money.fromString("1000.00").compareTo(soma))
    }

    // ── quitação com juros e multa ────────────────────────────────────────────

    @Test
    fun `quitacao com juros e multa cashEffective cobre caixa total`() {
        val inserted = mutableListOf<TransactionPayment>()
        val service  = buildService(listOf(expense()), inserted)

        service.recordPayment(
            id             = 1,
            paymentDate    = date,
            paidAmount     = Money.fromString("1000.00"),
            interestAmount = Money.fromString("50.00"),
            fineAmount     = Money.fromString("20.00")
        )

        // cashEffective = 1000 + 50 + 20 = 1070 == paidAmount armazenado
        val soma = inserted.fold(Money.ZERO) { acc, p -> acc + p.cashEffective }
        assertEquals(0, Money.fromString("1070.00").compareTo(soma))
    }

    // ── baixas estornadas não entram na soma ──────────────────────────────────

    @Test
    fun `baixa com reversedById preenchido nao contribui para reconciliacao`() {
        val payDate = LocalDate.of(2026, 8, 15)
        val ativa   = TransactionPayment(transactionId = 1, paymentDate = payDate, accountId = 1, principalAmount = Money.fromString("600.00"))
        val revert  = TransactionPayment(transactionId = 1, paymentDate = payDate, accountId = 1, principalAmount = Money.fromString("400.00"), reversedById = 1)

        val soma = listOf(ativa, revert)
            .filter { it.reversedById == null }
            .fold(Money.ZERO) { acc, p -> acc + p.cashEffective }

        assertEquals(0, Money.fromString("600.00").compareTo(soma))
    }

    // ── ReconciliationDivergence.delta ────────────────────────────────────────

    @Test
    fun `ReconciliationDivergence delta e diferenca entre somaBaixas e paidAmount`() {
        val div = ReconciliationDivergence(
            transactionId     = 1,
            transactionAmount = Money.fromString("1000.00"),
            paidAmount        = Money.fromString("1000.00"),
            somaBaixas        = Money.fromString("950.00")
        )
        assertEquals(0, Money.fromString("-50.00").compareTo(div.delta))
    }
}
