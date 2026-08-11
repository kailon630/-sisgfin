package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.accounts.AccountBalanceFormula
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P0-4 — acumulação de pagamentos parciais.
 *
 * Espelha a lógica corrigida de TransactionService.recordPayment em memória
 * (mesmo padrão dos demais testes desta suite — sem banco).
 *
 * Todos os testes devem falhar ANTES da correção e passar DEPOIS.
 * Falha antes: `tx.outstandingPrincipal` e `tx.principalPaid` não existem → erro de compilação.
 */
class PartialPaymentAccumulationTest {

    private val issueDate = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val payDate1  = LocalDateTime.of(2026, 2, 1, 0, 0)
    private val payDate2  = LocalDateTime.of(2026, 3, 1, 0, 0)
    private val payDate3  = LocalDateTime.of(2026, 4, 1, 0, 0)

    private fun pendingTx(amount: String = "1000.00") = Transaction(
        id          = 1,
        type        = TransactionType.EXPENSE,
        status      = TransactionStatus.PENDING,
        description = "Título teste",
        amount      = Money.fromString(amount),
        issueDate   = issueDate,
        dueDate     = LocalDateTime.of(2026, 3, 1, 0, 0),
        accountId   = 1
    )

    /**
     * Espelha TransactionService.recordPayment (versão corrigida) em memória.
     * `principal` = valor do principal nesta baixa.
     * `interest`/`fine` = encargos desta baixa, acumulados em interestAmount/fineAmount.
     */
    private fun applyPayment(
        tx: Transaction,
        principal: Money,
        interest: Money? = null,
        fine: Money? = null,
        paymentDate: LocalDateTime = payDate1
    ): Transaction {
        val juros = interest ?: Money.ZERO
        val multa = fine ?: Money.ZERO

        // Validações (espelham TransactionValidator.validatePayment corrigido)
        val outstanding = tx.outstandingPrincipal          // P0-4: propriedade nova
        if (principal.isZero() || principal.isNegative())
            throw IllegalArgumentException("Valor pago deve ser maior que zero.")
        if (principal.compareTo(outstanding) > 0)
            throw IllegalArgumentException(
                "Valor excede o saldo devedor do título (R$ $outstanding restantes)."
            )
        if (juros.isNegative())
            throw IllegalArgumentException("Juros não pode ser negativo.")
        if (multa.isNegative())
            throw IllegalArgumentException("Multa não pode ser negativa.")
        if (paymentDate.isBefore(tx.issueDate))
            throw IllegalArgumentException("Data de pagamento não pode ser anterior à data de emissão.")

        // Acumulação (espelha lógica corrigida)
        val newPaidAmount  = (tx.paidAmount      ?: Money.ZERO) + principal + juros + multa
        val newInterest    = (tx.interestAmount   ?: Money.ZERO) + juros
        val newFine        = (tx.fineAmount       ?: Money.ZERO) + multa
        val newPrincipal   = newPaidAmount - newInterest - newFine  // = principalPaid acumulado

        // Resolução de status (espelha TransactionStateMachine corrigido)
        val newStatus = TransactionStateMachine.resolveStatusAfterPayment(tx.amount.value, newPrincipal.value)
        TransactionStateMachine.assertTransition(tx.status, newStatus)  // exige PARTIAL→PARTIAL permitido

        return tx.copy(
            status         = newStatus,
            paymentDate    = paymentDate,
            paidAmount     = newPaidAmount,
            interestAmount = if (newInterest.isZero()) null else newInterest,
            fineAmount     = if (newFine.isZero()) null else newFine
        )
    }

    // ── T1 ───────────────────────────────────────────────────────────────────

    @Test
    fun `T1 duas baixas 300 mais 700 produzem PAID com paidAmount acumulado de 1000`() {
        val tx0 = pendingTx()

        val tx1 = applyPayment(tx0, Money.fromString("300.00"), paymentDate = payDate1)
        assertEquals(TransactionStatus.PARTIAL, tx1.status)
        assertEquals(0, Money.fromString("300.00").compareTo(tx1.paidAmount!!))
        assertEquals(0, Money.fromString("700.00").compareTo(tx1.outstandingPrincipal))

        val tx2 = applyPayment(tx1, Money.fromString("700.00"), paymentDate = payDate2)
        assertEquals(TransactionStatus.PAID, tx2.status)
        assertEquals(0, Money.fromString("1000.00").compareTo(tx2.paidAmount!!))
        assertEquals(0, Money.fromString("1000.00").compareTo(tx2.principalPaid))
    }

    // ── T2 ───────────────────────────────────────────────────────────────────

    @Test
    fun `T2 duas baixas 300 mais 300 produzem PARTIAL com paidAmount acumulado de 600`() {
        val tx0 = pendingTx()
        val tx1 = applyPayment(tx0, Money.fromString("300.00"), paymentDate = payDate1)
        val tx2 = applyPayment(tx1, Money.fromString("300.00"), paymentDate = payDate2)
        assertEquals(TransactionStatus.PARTIAL, tx2.status)
        assertEquals(0, Money.fromString("600.00").compareTo(tx2.paidAmount!!))
        assertEquals(0, Money.fromString("400.00").compareTo(tx2.outstandingPrincipal))
    }

    // ── T3 ───────────────────────────────────────────────────────────────────

    @Test
    fun `T3 segunda baixa de 800 com saldo devedor de 700 e rejeitada com mensagem de saldo`() {
        val tx0 = pendingTx()
        val tx1 = applyPayment(tx0, Money.fromString("300.00"), paymentDate = payDate1)

        val ex = assertThrows<IllegalArgumentException> {
            applyPayment(tx1, Money.fromString("800.00"), paymentDate = payDate2)
        }
        assertTrue(ex.message!!.contains("saldo devedor"), "Mensagem deve citar saldo devedor: ${ex.message}")
        assertTrue(ex.message!!.contains("700"),           "Mensagem deve informar o valor restante: ${ex.message}")
    }

    // ── T4 ───────────────────────────────────────────────────────────────────

    @Test
    fun `T4 quitacao com encargos — PAID principalPaid 1000 paidAmount 1050 saldo 1050`() {
        val tx0 = pendingTx()
        val tx1 = applyPayment(tx0, Money.fromString("300.00"), paymentDate = payDate1)

        val tx2 = applyPayment(
            tx1,
            principal = Money.fromString("700.00"),
            interest  = Money.fromString("50.00"),
            paymentDate = payDate2
        )

        assertEquals(TransactionStatus.PAID, tx2.status)
        assertEquals(0, Money.fromString("1050.00").compareTo(tx2.paidAmount!!),
            "paidAmount deve ser 1050 (principal acumulado + juros)")
        assertEquals(0, Money.fromString("1000.00").compareTo(tx2.principalPaid),
            "principalPaid deve ser 1000 (exclui encargos)")
        assertEquals(0, Money.fromString("50.00").compareTo(tx2.interestAmount!!),
            "interestAmount acumulado deve ser 50")

        // Saldo de conta deve refletir saída de 1050 (não 1000)
        val balance = AccountBalanceFormula.compute(
            initialBalance = Money.fromString("10000.00"),
            expense        = tx2.paidAmount!!
        )
        assertEquals(0, Money.fromString("8950.00").compareTo(balance))
    }

    // ── T5 ───────────────────────────────────────────────────────────────────

    @Test
    fun `T5 baixa integral 1000 produz PAID sem regressao`() {
        val tx0 = pendingTx()
        val tx1 = applyPayment(tx0, Money.fromString("1000.00"), paymentDate = payDate1)
        assertEquals(TransactionStatus.PAID, tx1.status)
        assertEquals(0, Money.fromString("1000.00").compareTo(tx1.paidAmount!!))
        assertEquals(0, Money.fromString("1000.00").compareTo(tx1.principalPaid))
        assertEquals(0, Money.ZERO.compareTo(tx1.outstandingPrincipal))
    }

    // ── T6 ───────────────────────────────────────────────────────────────────

    @Test
    fun `T6 tres baixas 400 mais 400 mais 200 produzem PAID com paidAmount de 1000`() {
        val tx0 = pendingTx()

        val tx1 = applyPayment(tx0, Money.fromString("400.00"), paymentDate = payDate1)
        assertEquals(TransactionStatus.PARTIAL, tx1.status)
        assertEquals(0, Money.fromString("400.00").compareTo(tx1.paidAmount!!))

        val tx2 = applyPayment(tx1, Money.fromString("400.00"), paymentDate = payDate2)
        assertEquals(TransactionStatus.PARTIAL, tx2.status)
        assertEquals(0, Money.fromString("800.00").compareTo(tx2.paidAmount!!))

        val tx3 = applyPayment(tx2, Money.fromString("200.00"), paymentDate = payDate3)
        assertEquals(TransactionStatus.PAID, tx3.status)
        assertEquals(0, Money.fromString("1000.00").compareTo(tx3.paidAmount!!))
    }

    // ── T7 ───────────────────────────────────────────────────────────────────

    @Test
    fun `T7 saldo com duas baixas parciais acumuladas reflete total correto`() {
        val tx0 = pendingTx()
        val tx1 = applyPayment(tx0, Money.fromString("300.00"), paymentDate = payDate1)
        val tx2 = applyPayment(tx1, Money.fromString("200.00"), paymentDate = payDate2)

        // paidAmount deve ser 500 acumulados
        assertEquals(0, Money.fromString("500.00").compareTo(tx2.paidAmount!!))

        // AccountBalanceFormula com paidAmount (total caixa saído) como expensePartial
        val balance = AccountBalanceFormula.compute(
            initialBalance = Money.fromString("10000.00"),
            expensePartial = tx2.paidAmount!!
        )
        assertEquals(0, Money.fromString("9500.00").compareTo(balance))
    }

    // ── T8 ───────────────────────────────────────────────────────────────────

    @Test
    fun `T8a baixa de valor zero e rejeitada`() {
        val tx0 = pendingTx()
        val ex = assertThrows<IllegalArgumentException> {
            applyPayment(tx0, Money.ZERO)
        }
        assertTrue(ex.message!!.contains("maior que zero"))
    }

    @Test
    fun `T8b baixa de valor negativo e rejeitada`() {
        val tx0 = pendingTx()
        val ex = assertThrows<IllegalArgumentException> {
            applyPayment(tx0, Money.fromString("-100.00"))
        }
        assertTrue(ex.message!!.contains("maior que zero"))
    }
}
