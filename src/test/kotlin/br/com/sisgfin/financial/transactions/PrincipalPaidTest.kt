package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.money.Money
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Parte A — principalPaid como principal puro.
 *
 * paidAmount armazena apenas principal amortizado.
 * interestAmount e fineAmount são colunas separadas e não afetam principalPaid nem outstandingPrincipal.
 *
 * A1 e A4 devem falhar ANTES da correção (principalPaid = paidAmount - encargos).
 */
class PrincipalPaidTest {

    private fun tx(
        amount: String,
        paidAmount: String? = null,
        interestAmount: String? = null,
        fineAmount: String? = null,
        status: TransactionStatus = TransactionStatus.PENDING
    ) = Transaction(
        type           = TransactionType.EXPENSE,
        status         = status,
        description    = "Teste A",
        amount         = Money.fromString(amount),
        paidAmount     = paidAmount?.let { Money.fromString(it) },
        interestAmount = interestAmount?.let { Money.fromString(it) },
        fineAmount     = fineAmount?.let { Money.fromString(it) },
        issueDate      = LocalDateTime.of(2026, 1, 1, 0, 0),
        dueDate        = LocalDateTime.of(2026, 3, 1, 0, 0),
        accountId      = 1
    )

    // ── A1 ───────────────────────────────────────────────────────────────────

    @Test
    fun `A1 amount 500 paidAmount 500 interest 50 - principalPaid 500 outstandingPrincipal 0`() {
        val t = tx("500.00", paidAmount = "500.00", interestAmount = "50.00")
        assertEquals(0, Money.fromString("500.00").compareTo(t.principalPaid),
            "principalPaid deve ser 500 — encargos não reduzem o principal")
        assertEquals(0, Money.ZERO.compareTo(t.outstandingPrincipal),
            "título totalmente amortizado: outstandingPrincipal == 0")
    }

    // ── A2 ───────────────────────────────────────────────────────────────────

    @Test
    fun `A2 titulo PAID com paidAmount 500 e interest 50 - nao aparece em aPagar`() {
        val t = tx("500.00", paidAmount = "500.00", interestAmount = "50.00",
            status = TransactionStatus.PAID)
        assertFalse(TransactionQuery.aPagar().statuses.contains(t.status),
            "status PAID não deve constar nos statuses de aPagar()")
    }

    // ── A3 ───────────────────────────────────────────────────────────────────

    @Test
    fun `A3 amount 1000 paidAmount 300 interest 20 - outstandingPrincipal 700`() {
        val t = tx("1000.00", paidAmount = "300.00", interestAmount = "20.00",
            status = TransactionStatus.PARTIAL)
        assertEquals(0, Money.fromString("700.00").compareTo(t.outstandingPrincipal),
            "outstandingPrincipal deve ser 700 — encargos não reduzem o saldo devedor")
    }

    // ── A4 ───────────────────────────────────────────────────────────────────

    @Test
    fun `A4 quitacao do saldo de A3 - recordPayment recebe 700`() {
        // outstandingPrincipal de A3 = 700; é esse valor que deve ser passado ao recordPayment.
        val t = tx("1000.00", paidAmount = "300.00", interestAmount = "20.00",
            status = TransactionStatus.PARTIAL)
        assertEquals(0, Money.fromString("700.00").compareTo(t.outstandingPrincipal),
            "saldo devedor correto: markAsPaidFull passaria 700 ao recordPayment")
    }

    // ── A5 ───────────────────────────────────────────────────────────────────

    @Test
    fun `A5 paidAmount null - principalPaid 0 outstandingPrincipal igual ao amount`() {
        val t = tx("1000.00")
        assertEquals(0, Money.ZERO.compareTo(t.principalPaid),
            "paidAmount null → principalPaid == 0")
        assertEquals(0, Money.fromString("1000.00").compareTo(t.outstandingPrincipal),
            "paidAmount null → outstandingPrincipal == amount")
    }
}
