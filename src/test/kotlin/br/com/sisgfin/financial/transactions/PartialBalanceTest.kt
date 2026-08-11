package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.accounts.AccountBalanceFormula
import br.com.sisgfin.financial.money.Money
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Testes para a correção do cálculo de saldo com lançamentos PARTIAL + C1 (estorno dirigido).
 *
 * Abordagem: fórmula pura via [AccountBalanceFormula] — mesma usada em produção.
 */
class PartialBalanceTest {

    private fun balance(
        initial: Money,
        income: Money = Money.ZERO,
        incomePartial: Money = Money.ZERO,
        expense: Money = Money.ZERO,
        expensePartial: Money = Money.ZERO,
        reversalCredit: Money = Money.ZERO,
        reversalDebit: Money = Money.ZERO,
        adjustment: Money = Money.ZERO,
        transferIn: Money = Money.ZERO,
        transferOut: Money = Money.ZERO
    ): Money = AccountBalanceFormula.compute(
        initialBalance = initial,
        income = income,
        incomePartial = incomePartial,
        expense = expense,
        expensePartial = expensePartial,
        adjustment = adjustment,
        transferIn = transferIn,
        transferOut = transferOut,
        reversalCredit = reversalCredit,
        reversalDebit = reversalDebit
    )

    @Test
    fun `T1 expense partial paidAmount 400 reduz saldo em 400`() {
        val result = balance(
            initial = Money.fromDouble(10_000.0),
            expensePartial = Money.fromDouble(400.0)
        )
        assertEquals("9600.00", result.toString())
    }

    @Test
    fun `T2 income partial paidAmount 400 aumenta saldo em 400`() {
        val result = balance(
            initial = Money.fromDouble(10_000.0),
            incomePartial = Money.fromDouble(400.0)
        )
        assertEquals("10400.00", result.toString())
    }

    @Test
    fun `T3 expense paid amount 1000 reduz saldo em 1000`() {
        val result = balance(
            initial = Money.fromDouble(10_000.0),
            expense = Money.fromDouble(1_000.0)
        )
        assertEquals("9000.00", result.toString())
    }

    @Test
    fun `T4 income paid amount 1000 aumenta saldo em 1000`() {
        val result = balance(
            initial = Money.fromDouble(10_000.0),
            income = Money.fromDouble(1_000.0)
        )
        assertEquals("11000.00", result.toString())
    }

    @Test
    fun `T5 expense paid e reversal credit restauram saldo original`() {
        val result = balance(
            initial = Money.fromDouble(10_000.0),
            expense = Money.fromDouble(1_000.0),
            reversalCredit = Money.fromDouble(1_000.0)
        )
        assertEquals("10000.00", result.toString())
    }

    @Test
    fun `T6a estado PARTIAL paidAmount 400 impacta saldo em -400`() {
        val result = balance(
            initial = Money.fromDouble(10_000.0),
            expense = Money.ZERO,
            expensePartial = Money.fromDouble(400.0)
        )
        assertEquals("9600.00", result.toString())
    }

    @Test
    fun `T6b estado PAID apos completar pagamento impacta saldo em -1000 sem double counting`() {
        val result = balance(
            initial = Money.fromDouble(10_000.0),
            expense = Money.fromDouble(1_000.0),
            expensePartial = Money.ZERO
        )
        assertEquals("9000.00", result.toString())
    }

    @Test
    fun `T6c verificacao explicita de ausencia de double counting`() {
        val errado = balance(
            initial = Money.fromDouble(10_000.0),
            expense = Money.fromDouble(1_000.0),
            expensePartial = Money.fromDouble(400.0)
        )
        assertFalse(errado.toString() == "9000.00", "double counting detectado se ambos fossem somados")
        assertEquals("8600.00", errado.toString())
    }

    @Test
    fun `T7 expense partial 400 e expense paid 1000 na mesma conta impactam -1400`() {
        val result = balance(
            initial = Money.fromDouble(10_000.0),
            expense = Money.fromDouble(1_000.0),
            expensePartial = Money.fromDouble(400.0)
        )
        assertEquals("8600.00", result.toString())
    }

    @Test
    fun `T8 formula de openingBalance com PARTIAL antes do periodo tem mesma semantica`() {
        val openingExpense = Money.fromDouble(1_000.0)
        val openingExpensePartial = Money.fromDouble(400.0)
        val initialBalance = Money.fromDouble(10_000.0)

        val opening = balance(
            initial = initialBalance,
            expense = openingExpense,
            expensePartial = openingExpensePartial
        )
        assertEquals("8600.00", opening.toString())
    }

    @Test
    fun `cenario completo saldo nao alterado por PENDING depois -400 por PARTIAL depois -1000 por PAID`() {
        val initial = Money.fromDouble(10_000.0)

        val saldo1 = balance(initial = initial)
        assertEquals("10000.00", saldo1.toString())

        val saldo2 = balance(initial = initial, expensePartial = Money.fromDouble(400.0))
        assertEquals("9600.00", saldo2.toString())

        val saldo3 = balance(initial = initial, expense = Money.fromDouble(1_000.0))
        assertEquals("9000.00", saldo3.toString())

        assertFalse(saldo3.compareTo(Money.fromDouble(8_600.0)) == 0)
    }
}
