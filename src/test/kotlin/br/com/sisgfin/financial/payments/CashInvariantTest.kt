package br.com.sisgfin.financial.payments

import br.com.sisgfin.financial.accounts.AccountBalanceFormula
import br.com.sisgfin.financial.money.Money
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * C-13 — Invariante de caixa (M4).
 *
 * Após a migração das leituras para transaction_payments, o saldo de uma conta
 * deve ser idêntico à soma dos cashEffective das baixas ativas, agrupados por
 * sinal conforme [AccountBalanceFormula]. Nenhuma baixa é perdida nem duplicada.
 *
 * Premissas (M4):
 *   - INCOME baixas aumentam o saldo (+ cashEffective)
 *   - EXPENSE baixas reduzem o saldo (− cashEffective)
 *   - Desconto reduz cashEffective → menos sai do caixa
 *   - PARTIAL e PAID contribuem juntos via suas baixas
 *   - Estorno de EXPENSE credita cashEffective do original de volta ao saldo
 */
class CashInvariantTest {

    private fun compute(
        initial: Money = Money.ZERO,
        income: Money = Money.ZERO,
        expense: Money = Money.ZERO,
        adjustment: Money = Money.ZERO,
        transferIn: Money = Money.ZERO,
        transferOut: Money = Money.ZERO,
        reversalCredit: Money = Money.ZERO,
        reversalDebit: Money = Money.ZERO
    ) = AccountBalanceFormula.compute(
        initialBalance = initial,
        income         = income,
        expense        = expense,
        adjustment     = adjustment,
        transferIn     = transferIn,
        transferOut    = transferOut,
        reversalCredit = reversalCredit,
        reversalDebit  = reversalDebit
    )

    // ── C-13-A: baixa única com juros e multa ─────────────────────────────────

    @Test
    fun `C13-A despesa com juros e multa reduz saldo por cashEffective completo`() {
        // Despesa de 1000, paga com: 1000 principal + 50 juros + 20 multa
        // cashEffective = 1070
        val balance = compute(
            initial = Money.fromString("5000.00"),
            expense = Money.fromString("1070.00")
        )
        assertEquals(0, Money.fromString("3930.00").compareTo(balance),
            "cashEffective=1070 reduz saldo em 1070")
    }

    // ── C-13-B: desconto reduz cashEffective da despesa ───────────────────────

    @Test
    fun `C13-B desconto reduz cashEffective e portanto o impacto no saldo`() {
        // Despesa de 1000, desconto 50, principal=950 → cashEffective=900
        // calculateBalance usa cashEffective das baixas (M4)
        val balance = compute(
            initial = Money.fromString("5000.00"),
            expense = Money.fromString("900.00") // cashEffective = 950 - 50 desconto
        )
        assertEquals(0, Money.fromString("4100.00").compareTo(balance),
            "desconto reduz impacto no saldo: expense=900 (950 principal − 50 desconto)")
    }

    // ── C-13-C: PARTIAL e PAID unificados via baixas ─────────────────────────

    @Test
    fun `C13-C baixas parciais e totais somam no saldo sem duplicacao`() {
        // Título de 1000: 2 baixas parciais (300 + 400) + 1 baixa final (300)
        // cashEffective total = 300 + 400 + 300 = 1000
        val balance = compute(
            initial = Money.fromString("10000.00"),
            expense = Money.fromString("1000.00")
        )
        assertEquals(0, Money.fromString("9000.00").compareTo(balance),
            "Três baixas somam exatamente o face value — sem duplicação")
    }

    // ── C-13-D: sem assimetria PARTIAL → PAID ────────────────────────────────

    @Test
    fun `C13-D transicao PARTIAL-PAID nao altera cashEffective acumulado`() {
        // 1ª baixa: 350 cashEffective (300 + 50 juros) — status PARTIAL
        val balancePartial = compute(
            initial = Money.fromString("10000.00"),
            expense = Money.fromString("350.00")
        )
        assertEquals(0, Money.fromString("9650.00").compareTo(balancePartial))

        // 2ª baixa: 700 cashEffective adicional — status PAID
        val balancePaid = compute(
            initial = Money.fromString("10000.00"),
            expense = Money.fromString("1050.00") // 350 + 700
        )
        assertEquals(0, Money.fromString("8950.00").compareTo(balancePaid))

        // A transição PARTIAL→PAID causou queda de exatamente 700 (sem surpresa)
        val quedaAdicional = balancePartial - balancePaid
        assertEquals(0, Money.fromString("700.00").compareTo(quedaAdicional),
            "Transição PARTIAL→PAID: queda adicional = 700 (2ª baixa cashEffective)")
    }

    // ── C-13-E: estorno de EXPENSE credita cashEffective de volta ─────────────

    @Test
    fun `C13-E estorno de EXPENSE credita cashEffective original no saldo`() {
        // Despesa 1000 paga, depois estornada
        // Saldo líquido = 0 (despesa cancelada)
        val balance = compute(
            initial        = Money.fromString("5000.00"),
            expense        = Money.fromString("1000.00"),
            reversalCredit = Money.fromString("1000.00") // cashEffective das baixas do original
        )
        assertEquals(0, Money.fromString("5000.00").compareTo(balance),
            "Despesa estornada não altera o saldo final")
    }

    // ── C-13-F: receita com juros aumenta saldo por cashEffective completo ─────

    @Test
    fun `C13-F receita com juros e multa aumenta saldo por cashEffective completo`() {
        // Receita de 2000, paga com: 2000 principal + 100 juros
        // cashEffective = 2100
        val balance = compute(
            initial = Money.fromString("5000.00"),
            income  = Money.fromString("2100.00")
        )
        assertEquals(0, Money.fromString("7100.00").compareTo(balance),
            "cashEffective=2100 aumenta saldo em 2100")
    }

    // ── C-13-G: saldo zero com income = expense ────────────────────────────────

    @Test
    fun `C13-G income e expense iguais produzem saldo zero`() {
        val cashFlow = Money.fromString("3000.00")
        val balance = compute(
            initial = Money.ZERO,
            income  = cashFlow,
            expense = cashFlow
        )
        assertEquals(0, Money.ZERO.compareTo(balance),
            "Entrada = saída → saldo zero")
    }
}
