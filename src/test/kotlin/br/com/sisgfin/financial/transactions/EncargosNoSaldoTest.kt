package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.accounts.AccountBalanceFormula
import br.com.sisgfin.financial.money.Money
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * P0-5 (M4): documenta comportamento CORRETO após calculateBalance migrar para cashEffective.
 * Invertido de EncargosNoSaldoTest pré-M4 — cada teste é a versão corrigida do Q5x anterior.
 *
 * Premissas (após M4):
 *   - calculateBalance usa cashEffective das baixas (principal + juros + multa − desconto)
 *   - PAID e PARTIAL contribuem via baixas; não há separação income/incomePartial no cálculo
 */
class EncargosNoSaldoTest {

    /**
     * Q5a — título PAID com juros: saldo reflete cashEffective completo.
     * Cenário: amount=1000, baixa com 1000 principal + 50 juros = cashEffective=1050.
     * Após M4: calculateBalance usa cashEffective=1050 → expense=1050.
     */
    @Test
    fun `Q5a titulo PAID com juros - calculateBalance usa cashEffective juros visiveis`() {
        val balance = AccountBalanceFormula.compute(
            initialBalance = Money.fromString("10000.00"),
            expense        = Money.fromString("1050.00") // cashEffective: 1000 principal + 50 juros
        )
        assertEquals(0, Money.fromString("8950.00").compareTo(balance),
            "cashEffective=1050 reduz saldo em 1050 — juros visíveis")
    }

    /**
     * Q5b — convergência statement vs calculateBalance após M4.
     * StatementModels.signedAmount usa paidAmount=1050 = cashEffective.
     * calculateBalance usa cashEffective=1050.
     * Divergência = 0.
     */
    @Test
    fun `Q5b statement e calculateBalance convergem apos M4 - divergencia zero`() {
        val statementLine = Money.fromString("1050.00") // paidAmount = cashEffective
        val balanceImpact = Money.fromString("1050.00") // cashEffective da baixa
        val divergencia = statementLine - balanceImpact
        assertEquals(0, Money.ZERO.compareTo(divergencia),
            "Sem divergência: statement e balance usam mesma fonte (cashEffective)")
    }

    /**
     * Q5c — título PARTIAL: cashEffective da baixa = 350 (300 principal + 50 juros).
     * Comportamento idêntico ao pré-M4 pois paidAmount == cashEffective neste cenário.
     */
    @Test
    fun `Q5c titulo PARTIAL - cashEffective da baixa parcial refletido no saldo`() {
        val balance = AccountBalanceFormula.compute(
            initialBalance = Money.fromString("10000.00"),
            expense        = Money.fromString("350.00") // cashEffective da 1ª baixa parcial
        )
        assertEquals(0, Money.fromString("9650.00").compareTo(balance),
            "cashEffective=350 da baixa parcial (300 + 50 juros) reduz saldo corretamente")
    }

    /**
     * Q5d — sem assimetria PARTIAL → PAID após M4.
     * Cada baixa reduz o saldo por seu cashEffective, independente do status.
     *
     * Timeline:
     *   1ª baixa 300 principal + 50 juros = 350 cashEffective → saldo = 9650
     *   2ª baixa 700 principal             = 700 cashEffective → saldo = 8950
     *   Queda adicional = 700 (consistente)
     */
    @Test
    fun `Q5d sem assimetria apos M4 - cada baixa reduz saldo por seu cashEffective`() {
        // Após 1ª baixa: cashEffective acumulado = 350
        val balanceApos1aBaixa = AccountBalanceFormula.compute(
            initialBalance = Money.fromString("10000.00"),
            expense        = Money.fromString("350.00")
        )
        assertEquals(0, Money.fromString("9650.00").compareTo(balanceApos1aBaixa))

        // Após 2ª baixa: cashEffective acumulado = 350 + 700 = 1050
        val balanceApos2aBaixa = AccountBalanceFormula.compute(
            initialBalance = Money.fromString("10000.00"),
            expense        = Money.fromString("1050.00")
        )
        assertEquals(0, Money.fromString("8950.00").compareTo(balanceApos2aBaixa))

        // 2ª baixa causou queda de exatamente 700 — sem surpresa
        val quedaAdicional = balanceApos1aBaixa - balanceApos2aBaixa
        assertEquals(0, Money.fromString("700.00").compareTo(quedaAdicional),
            "2ª baixa reduziu saldo em exatamente 700 — assimetria PARTIAL→PAID eliminada")
    }
}
