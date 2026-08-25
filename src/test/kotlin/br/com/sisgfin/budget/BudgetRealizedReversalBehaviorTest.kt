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

    // ── 3b: realizado orçamentário = principal líquido de desconto ────────────
    // Juros e multa NÃO consomem dotação — são despesas financeiras distintas.
    // cashEffective é para saldo de caixa; realizado usa principalAmount - discountAmount.

    /**
     * Espelha a lógica de BudgetItemRepository.sumRealized pós D-PRINCIPAL (B):
     * Σ(principalAmount) das baixas EXPENSE ativas.
     * principalAmount = face amortizado (já inclui desconto). Juros e multa ignorados.
     */
    private data class Payment(
        val principal: Money,
        val interest: Money = Money.ZERO,
        val fine: Money = Money.ZERO,
        val discount: Money = Money.ZERO
    )

    private fun sumRealizedFromPayments(payments: List<Payment>): Money =
        payments.fold(Money.ZERO) { acc, p -> acc + p.principal }

    @Test
    fun `dotacao 10000 titulo 1000 pago com 50 juros realizado e 1000 saldo e 9000`() {
        val budgetAmount = Money.fromString("10000.00")
        val payments = listOf(
            Payment(principal = Money.fromString("1000.00"), interest = Money.fromString("50.00"))
        )
        val realized = sumRealizedFromPayments(payments)
        val budgetBalance = budgetAmount - realized

        assertEquals(0, Money.fromString("1000.00").compareTo(realized),
            "Realizado = 1000 (principal puro; juros 50 não consomem dotação)")
        assertEquals(0, Money.fromString("9000.00").compareTo(budgetBalance),
            "Saldo da dotação = 9000 (10000 - 1000)")
    }

    @Test
    fun `titulo 1000 com desconto 50 operador paga 950 face 1000 realizado e 1000`() {
        // D-PRINCIPAL (B): principal_amount = face = 1000 (cash 950 + desconto 50).
        // Realizado = Σ(principal_amount) = 1000. Desconto já está no face — sem dupla subtração.
        val payments = listOf(
            Payment(principal = Money.fromString("1000.00"), discount = Money.fromString("50.00"))
        )
        val realized = sumRealizedFromPayments(payments)
        assertEquals(0, Money.fromString("1000.00").compareTo(realized),
            "Face amortizado = 1000; desconto embutido no face, não subtrai separado")
    }

    @Test
    fun `titulo 1000 pago com 50 juros e 30 multa realizado e 1000 encargos ignorados`() {
        val payments = listOf(
            Payment(
                principal = Money.fromString("1000.00"),
                interest  = Money.fromString("50.00"),
                fine      = Money.fromString("30.00")
            )
        )
        val realized = sumRealizedFromPayments(payments)
        assertEquals(0, Money.fromString("1000.00").compareTo(realized),
            "Juros (50) e multa (30) ignorados: realizado = 1000")
    }

    @Test
    fun `titulo 1000 com juros 50 e desconto 100 operador paga 900 face 1000 realizado e 1000`() {
        // D-PRINCIPAL (B): face = cash + desconto = 900 + 100 = 1000. Juros ignorados.
        // Realizado = Σ(principal_amount) = 1000.
        val payments = listOf(
            Payment(
                principal = Money.fromString("1000.00"),
                interest  = Money.fromString("50.00"),
                discount  = Money.fromString("100.00")
            )
        )
        val realized = sumRealizedFromPayments(payments)
        assertEquals(0, Money.fromString("1000.00").compareTo(realized),
            "Face amortizado = 1000; desconto embutido no face; juros ignorados")
    }
}
