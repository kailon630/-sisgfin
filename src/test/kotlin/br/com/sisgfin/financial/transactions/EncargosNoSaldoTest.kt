package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.accounts.AccountBalanceFormula
import br.com.sisgfin.financial.money.Money
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * P0-5: documenta comportamento ATUAL dos encargos no saldo de conta.
 * Não corrige — serve de baseline para eventual P0-6.
 *
 * Premissas (após P0-4):
 *   - paidAmount armazena principal + juros + multa acumulados
 *   - calculateBalance usa sumPaid → SUM(amount) para PAID
 *   - calculateBalance usa sumPartialPaid → SUM(paid_amount) para PARTIAL
 */
class EncargosNoSaldoTest {

    /**
     * Q5a — título PAID (baixa única): calculateBalance usa amount, não paidAmount.
     * Cenário: amount=1000, quitado com 1000 principal + 50 juros.
     * Após P0-4: paidAmount=1050, interestAmount=50, status=PAID.
     * sumPaid → SUM(amount) = 1000 → expense = 1000, não 1050.
     */
    @Test
    fun `Q5a titulo PAID baixa unica - calculateBalance usa amount nao paidAmount - juros invisiveis`() {
        val balance = AccountBalanceFormula.compute(
            initialBalance = Money.fromString("10000.00"),
            expense        = Money.fromString("1000.00") // amount, não paidAmount=1050
        )
        // R$50 de juros pagos são invisíveis ao saldo
        assertEquals(0, Money.fromString("9000.00").compareTo(balance),
            "Balance usa amount=1000, não paidAmount=1050 — R$50 de juros desaparecem do saldo")
    }

    /**
     * Q5b — divergência statement vs calculateBalance.
     * StatementModels.signedAmount usa tx.paidAmount ?: tx.amount = 1050.
     * calculateBalance usa amount = 1000.
     * Divergência = 50 (juros).
     */
    @Test
    fun `Q5b statement mostra paidAmount 1050 mas calculateBalance ve amount 1000 - divergencia 50`() {
        val statementLine = Money.fromString("1050.00") // paidAmount após P0-4
        val balanceImpact = Money.fromString("1000.00") // amount (o que calculateBalance usa)
        val divergencia = statementLine - balanceImpact
        assertEquals(0, Money.fromString("50.00").compareTo(divergencia),
            "Divergência statement vs balance = juros pagos (50)")
    }

    /**
     * Q5c — título PARTIAL: calculateBalance usa paidAmount, que após P0-4 inclui encargos.
     * Cenário: amount=1000, 1ª baixa 300 principal + 50 juros → PARTIAL.
     * Após P0-4: paidAmount=350, interestAmount=50.
     * sumPartialPaid → SUM(paid_amount) = 350 → expensePartial = 350 (encargos visíveis).
     */
    @Test
    fun `Q5c titulo PARTIAL - calculateBalance usa paidAmount que inclui juros - encargos visiveis`() {
        val balance = AccountBalanceFormula.compute(
            initialBalance = Money.fromString("10000.00"),
            expensePartial = Money.fromString("350.00") // paidAmount = 300 principal + 50 juros
        )
        assertEquals(0, Money.fromString("9650.00").compareTo(balance),
            "Para PARTIAL, juros SÃO visíveis via paidAmount (350 = 300 + 50)")
    }

    /**
     * Q5d — assimetria PARTIAL → PAID: juros pagos durante PARTIAL somem ao quitar o saldo.
     *
     * Timeline:
     *   1ª baixa 300 principal + 50 juros → PARTIAL → saldo = 9650
     *   2ª baixa 700 principal (sem juros) → PAID   → saldo = 9000
     *
     * Esperado correto: 10000 - 350 (PARTIAL) - 700 (2ª baixa) = 8950
     * Comportamento atual: ao virar PAID, expense = amount = 1000; saldo = 9000
     *
     * Erro: R$50 de juros da 1ª baixa desaparecem quando status muda PARTIAL → PAID.
     */
    @Test
    fun `Q5d assimetria PARTIAL-PAID - juros da primeira baixa somem ao quitar status`() {
        // Enquanto PARTIAL (após 1ª baixa: 300 principal + 50 juros)
        val balanceWhilePartial = AccountBalanceFormula.compute(
            initialBalance = Money.fromString("10000.00"),
            expensePartial = Money.fromString("350.00") // paidAmount da 1ª baixa
        )
        assertEquals(0, Money.fromString("9650.00").compareTo(balanceWhilePartial))

        // Após 2ª baixa (700 principal, sem juros) → status = PAID
        // calculateBalance agora usa SUM(amount) = 1000 para expense; expensePartial = 0
        val balanceAfterPaid = AccountBalanceFormula.compute(
            initialBalance = Money.fromString("10000.00"),
            expense        = Money.fromString("1000.00") // amount — ignora paidAmount=1050
        )
        assertEquals(0, Money.fromString("9000.00").compareTo(balanceAfterPaid))

        // Saldo "melhorou" 650 em relação ao PARTIAL (9650→9000 = queda de 650)
        // mas R$700 de principal foram pagos → deveria cair 700 (para 8950)
        // Diferença = 50 = os juros da 1ª baixa que foram "esquecidos"
        val perdaReal = balanceWhilePartial - balanceAfterPaid
        assertEquals(0, Money.fromString("650.00").compareTo(perdaReal),
            "2ª baixa causou queda de 650 no saldo, não 700 — 50 de juros da 1ª baixa sumiram")
    }
}
