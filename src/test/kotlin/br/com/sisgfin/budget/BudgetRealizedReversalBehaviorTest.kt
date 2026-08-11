package br.com.sisgfin.budget

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * R7.1 — documenta o comportamento ATUAL do realizado orçamentário após estorno.
 * Não é correção: `sumRealized` soma amount de todo PAID sem filtrar tipo,
 * e o estorno copia CC/categoria com amount positivo → realizado dobra.
 */
class BudgetRealizedReversalBehaviorTest {

    /**
     * Espelha [BudgetItemRepository.sumRealized]: soma `amount` de linhas
     * PAID + ativas com mesmo CC×categoria (sem filtro de type).
     */
    private fun sumRealized(lines: List<Pair<TransactionType, Money>>): Money =
        lines.fold(Money.ZERO) { acc, (_, amount) -> acc + amount }

    @Test
    fun `R7 EXPENSE 1000 PAID e REVERSAL 1000 na mesma rubrica resulta em realizado 2000`() {
        // Após pagamento: só a despesa
        val afterPay = sumRealized(
            listOf(TransactionType.EXPENSE to Money.fromDouble(1_000.0))
        )
        assertEquals("1000.00", afterPay.toString())

        // Após estorno: original permanece PAID + REVERSAL PAID (mesmo CC/categoria)
        val afterRev = sumRealized(
            listOf(
                TransactionType.EXPENSE to Money.fromDouble(1_000.0),
                TransactionType.REVERSAL to Money.fromDouble(1_000.0)
            )
        )
        assertEquals("2000.00", afterRev.toString())
        // Comportamento desejável seria 0; hoje é 2000 (bug documentado, não corrigido)
    }

    @Test
    fun `R7 Demonstrativo trata REVERSAL como receita nao como reducao de despesa`() {
        // Espelha ReportsViewModel.applyDemonstrativoFilter
        var income = Money.ZERO
        var expense = Money.ZERO
        fun classify(type: TransactionType, value: Money) {
            when (type) {
                TransactionType.INCOME, TransactionType.REVERSAL, TransactionType.ADJUSTMENT ->
                    income += value
                TransactionType.EXPENSE -> expense += value
                else -> {}
            }
        }
        classify(TransactionType.EXPENSE, Money.fromDouble(1_000.0))
        classify(TransactionType.REVERSAL, Money.fromDouble(1_000.0))
        assertEquals("1000.00", expense.toString())
        assertEquals("1000.00", income.toString())
        assertEquals("0.00", (income - expense).toString())
    }
}
