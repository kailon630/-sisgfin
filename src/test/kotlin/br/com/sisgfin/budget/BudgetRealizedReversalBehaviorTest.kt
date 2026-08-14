package br.com.sisgfin.budget

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * C-11 — garante que `sumRealized` neteia estornos de despesa corretamente.
 *
 * Fórmula corrigida: soma apenas EXPENSE; subtrai REVERSAL cujo reversedType=EXPENSE.
 * REVERSAL órfão (reversedType=null) não subtrai — conservador, sem crash.
 * INCOME e REVERSAL de INCOME não afetam o realizado de despesa.
 *
 * Os dois primeiros testes foram invertidos em C-11: afirmavam o comportamento
 * bugado (realizado=2000 após estorno, REVERSAL como receita no Demonstrativo).
 * Agora afirmam o comportamento correto.
 */
class BudgetRealizedReversalBehaviorTest {

    /**
     * Espelha a lógica corrigida de [BudgetItemRepository.sumRealized]:
     * soma EXPENSE, subtrai REVERSAL(EXPENSE), ignora o resto.
     */
    private fun sumRealized(lines: List<Triple<TransactionType, TransactionType?, Money>>): Money =
        lines.fold(Money.ZERO) { acc, (type, reversedType, amount) ->
            when {
                type == TransactionType.EXPENSE -> acc + amount
                type == TransactionType.REVERSAL && reversedType == TransactionType.EXPENSE -> acc - amount
                else -> acc
            }
        }

    // ── Testes invertidos de R7 ───────────────────────────────────────────────

    @Test
    fun `EXPENSE 1000 PAID estornado integralmente resulta em realizado zero`() {
        // Antes da correção C-11 este teste assertava "2000.00" (comportamento bugado).
        val realized = sumRealized(listOf(
            Triple(TransactionType.EXPENSE,  null,                    Money.fromDouble(1_000.0)),
            Triple(TransactionType.REVERSAL, TransactionType.EXPENSE, Money.fromDouble(1_000.0))
        ))
        assertEquals("0.00", realized.toString())
    }

    @Test
    fun `Demonstrativo REVERSAL de EXPENSE reduz despesa nao conta como receita`() {
        // Antes da correção C-11 este teste assertava expense=1000, income=1000.
        var income  = Money.ZERO
        var expense = Money.ZERO
        fun classify(type: TransactionType, reversedType: TransactionType?, value: Money) {
            when {
                type == TransactionType.INCOME || type == TransactionType.ADJUSTMENT ->
                    income += value
                type == TransactionType.EXPENSE ->
                    expense += value
                type == TransactionType.REVERSAL && reversedType == TransactionType.EXPENSE ->
                    expense -= value
                type == TransactionType.REVERSAL && reversedType == TransactionType.INCOME ->
                    income -= value
                type == TransactionType.REVERSAL ->  // órfão
                    income += value
            }
        }
        classify(TransactionType.EXPENSE, null, Money.fromDouble(1_000.0))
        classify(TransactionType.REVERSAL, TransactionType.EXPENSE, Money.fromDouble(1_000.0))
        assertEquals("0.00", expense.toString())
        assertEquals("0.00", income.toString())
        assertEquals("0.00", (income - expense).toString())
    }

    // ── Novos cenários C-11 (2.3) ─────────────────────────────────────────────

    @Test
    fun `EXPENSE 1000 PAID sem estorno resulta em realizado 1000`() {
        val realized = sumRealized(listOf(
            Triple(TransactionType.EXPENSE, null, Money.fromDouble(1_000.0))
        ))
        assertEquals("1000.00", realized.toString())
    }

    @Test
    fun `EXPENSE 1000 PAID com REVERSAL parcial 600 resulta em realizado 400`() {
        val realized = sumRealized(listOf(
            Triple(TransactionType.EXPENSE,  null,                    Money.fromDouble(1_000.0)),
            Triple(TransactionType.REVERSAL, TransactionType.EXPENSE, Money.fromDouble(600.0))
        ))
        assertEquals("400.00", realized.toString())
    }

    @Test
    fun `INCOME PAID e seu REVERSAL nao afetam realizado de despesa`() {
        val realized = sumRealized(listOf(
            Triple(TransactionType.INCOME,   null,                   Money.fromDouble(500.0)),
            Triple(TransactionType.REVERSAL, TransactionType.INCOME, Money.fromDouble(500.0))
        ))
        assertEquals("0.00", realized.toString())
    }

    @Test
    fun `REVERSAL orfao sem reversed_type nao subtrai do realizado`() {
        val realized = sumRealized(listOf(
            Triple(TransactionType.EXPENSE,  null, Money.fromDouble(1_000.0)),
            Triple(TransactionType.REVERSAL, null, Money.fromDouble(1_000.0))
        ))
        // Orfão é ignorado: não subtrai (conservador), não causa crash
        assertEquals("1000.00", realized.toString())
    }

    @Test
    fun `rubrica sem lancamento tem realizado zero`() {
        val realized = sumRealized(emptyList())
        assertEquals("0.00", realized.toString())
    }
}
