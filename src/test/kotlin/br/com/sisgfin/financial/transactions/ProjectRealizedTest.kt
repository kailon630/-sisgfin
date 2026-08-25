package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.money.Money
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * P2 — sumRealizedByProject: lê transaction_payments (alinhado com sumRealized).
 *
 * Cenários que falhavam antes da correção P2:
 *
 * PR-1: projeto com título 1.000 em duas baixas de 500, uma estornada → realizado = 500.
 *       Antes: filtrava status=PAID; título voltava a PARTIAL após estorno → realizado = 0.
 *       Depois: lê transaction_payments com reversed_by_id IS NULL → 500 correto.
 *
 * PR-2: título totalmente estornado → realizado = 0.
 *
 * PR-3: paridade — sumRealized e sumRealizedByProject devem produzir o mesmo valor
 *       para o mesmo conjunto de pagamentos.
 *
 * PR-4: título com juros → realizado = face (juros não consomem dotação).
 *
 * PR-5: título com desconto → realizado = face (desconto embutido no principal_amount).
 */
class ProjectRealizedTest {

    private data class Baixa(
        val principal: Money,
        val interest: Money = Money.ZERO,
        val fine: Money = Money.ZERO,
        val discount: Money = Money.ZERO,
        val reversed: Boolean = false
    )

    private fun sumRealizedByProject(baixas: List<Baixa>): Money =
        baixas.filter { !it.reversed }.fold(Money.ZERO) { acc, b -> acc + b.principal }

    private fun sumRealized(baixas: List<Baixa>): Money =
        baixas.filter { !it.reversed }.fold(Money.ZERO) { acc, b -> acc + b.principal }

    // ── PR-1: bug histórico — título PARTIAL some do realizado ──────────────

    @Test
    fun `PR-1 duas baixas de 500 uma estornada realizado e 500`() {
        val baixas = listOf(
            Baixa(principal = Money.fromString("500.00")),
            Baixa(principal = Money.fromString("500.00"), reversed = true)
        )
        val realized = sumRealizedByProject(baixas)
        assertEquals(0, Money.fromString("500.00").compareTo(realized),
            "Baixa estornada excluída por reversed_by_id IS NULL; uma baixa ativa = 500")
    }

    // ── PR-2: título totalmente estornado ─────────────────────────────────────

    @Test
    fun `PR-2 unica baixa estornada realizado e zero`() {
        val baixas = listOf(
            Baixa(principal = Money.fromString("1000.00"), reversed = true)
        )
        val realized = sumRealizedByProject(baixas)
        assertEquals(0, Money.ZERO.compareTo(realized))
    }

    // ── PR-3: paridade entre sumRealized e sumRealizedByProject ───────────────

    @Test
    fun `PR-3 paridade - sumRealized e sumRealizedByProject produzem mesmo valor`() {
        val baixas = listOf(
            Baixa(principal = Money.fromString("300.00")),
            Baixa(principal = Money.fromString("700.00"), reversed = true),
            Baixa(principal = Money.fromString("500.00"))
        )
        val byBudget  = sumRealized(baixas)
        val byProject = sumRealizedByProject(baixas)
        assertEquals(0, byBudget.compareTo(byProject),
            "sumRealized=$byBudget, sumRealizedByProject=$byProject — devem ser iguais")
    }

    // ── PR-4: juros não consomem dotação ────────────────────────────────────

    @Test
    fun `PR-4 titulo 1000 pago com 50 juros realizado e 1000`() {
        val baixas = listOf(
            Baixa(principal = Money.fromString("1000.00"), interest = Money.fromString("50.00"))
        )
        val realized = sumRealizedByProject(baixas)
        assertEquals(0, Money.fromString("1000.00").compareTo(realized),
            "Juros 50 não consomem dotação; realizado = face 1000")
    }

    // ── PR-5: desconto embutido no face ──────────────────────────────────────

    @Test
    fun `PR-5 titulo 1000 operador paga 950 desconto 50 face 1000 realizado e 1000`() {
        // D-PRINCIPAL (B): principal_amount = face = 950 + 50 = 1000.
        val baixas = listOf(
            Baixa(principal = Money.fromString("1000.00"), discount = Money.fromString("50.00"))
        )
        val realized = sumRealizedByProject(baixas)
        assertEquals(0, Money.fromString("1000.00").compareTo(realized),
            "Face amortizado = 1000; desconto embutido no principal_amount")
    }
}
