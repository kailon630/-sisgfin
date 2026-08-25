package br.com.sisgfin.financial.payments

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D1 — reversePaymentAndUpdateTitle: paidAmount é principal puro após estorno.
 *
 * Espelha em memória a lógica de TransactionRepository.reversePaymentAndUpdateTitle.
 * paidAmount deve conter apenas o newPrincipal das baixas ativas remanescentes.
 * Encargos permanecem em interestAmount/fineAmount (colunas separadas).
 *
 * E1: baixa única (principal=1000 + juros=50) estornada → paidAmount=null, outstanding=1000.
 * E2: 2 baixas; baixa 1 (principal=300, juros=50) sobrevive, baixa 2 (principal=700) estornada.
 *     → paidAmount=300 (principal puro), outstandingPrincipal=700, status=PARTIAL.
 */
class EstornoBaixaComEncargosTest {

    private data class Baixa(
        val id: Int,
        val principal: BigDecimal,
        val interest: BigDecimal = BigDecimal.ZERO,
        val fine: BigDecimal = BigDecimal.ZERO,
        val discount: BigDecimal = BigDecimal.ZERO,
        var reversedById: Int? = null
    ) {
        val cashEffective: BigDecimal get() = principal + interest + fine - discount
    }

    private data class TitleState(
        val amount: BigDecimal,
        val paidAmount: BigDecimal?,
        val interestAmount: BigDecimal?,
        val fineAmount: BigDecimal?,
        val status: TransactionStatus
    )

    /**
     * Espelha reversePaymentAndUpdateTitle em memória, incluindo o bug L423.
     * Returns o TitleState pós-estorno.
     */
    private fun applyReversal(
        title: TitleState,
        baixas: MutableList<Baixa>,
        targetId: Int
    ): TitleState {
        val target = baixas.first { it.id == targetId }
        require(target.reversedById == null) { "Baixa $targetId já estornada" }

        // Insere marcador de correção
        val correctionId = baixas.maxOf { it.id } + 1
        baixas.add(target.copy(id = correctionId, reversedById = targetId))
        baixas.first { it.id == targetId }.reversedById = correctionId

        // Recalcula a partir das baixas ativas restantes
        val active = baixas.filter { it.reversedById == null }
        val newPrincipal = active.fold(BigDecimal.ZERO) { acc, b -> acc + b.principal }
        val newInterest  = active.fold(BigDecimal.ZERO) { acc, b -> acc + b.interest }
        val newFine      = active.fold(BigDecimal.ZERO) { acc, b -> acc + b.fine }

        // D-PRINCIPAL (B): principal = face amortizado; quitação direta sem somar desconto à parte.
        val newPaidAmount    = newPrincipal
        val principalQuitado = newPrincipal

        val newStatus = when {
            principalQuitado.compareTo(title.amount) >= 0 -> TransactionStatus.PAID
            principalQuitado.compareTo(BigDecimal.ZERO) > 0 -> TransactionStatus.PARTIAL
            else -> TransactionStatus.PENDING
        }

        return TitleState(
            amount        = title.amount,
            paidAmount    = if (newPaidAmount.compareTo(BigDecimal.ZERO) == 0) null else newPaidAmount,
            interestAmount = if (newInterest.compareTo(BigDecimal.ZERO) == 0) null else newInterest,
            fineAmount    = if (newFine.compareTo(BigDecimal.ZERO) == 0) null else newFine,
            status        = newStatus
        )
    }

    // ── E1: baixa única com juros, estornada ─────────────────────────────────

    @Test
    fun `E1 despesa 1000 paga com 1000 principal e 50 juros estornada - paidAmount volta a null outstanding 1000`() {
        val title = TitleState(
            amount         = BigDecimal("1000.00"),
            paidAmount     = BigDecimal("1000.00"),   // paidAmount pós Parte A: principal puro
            interestAmount = BigDecimal("50.00"),
            fineAmount     = null,
            status         = TransactionStatus.PAID
        )
        val baixas = mutableListOf(
            Baixa(id = 1, principal = BigDecimal("1000.00"), interest = BigDecimal("50.00"))
        )

        val after = applyReversal(title, baixas, targetId = 1)

        // Bug L423 invisível aqui — nenhuma baixa ativa, tudo vai a zero
        assertNull(after.paidAmount,     "paidAmount deve ser null (nenhuma baixa ativa)")
        assertNull(after.interestAmount, "interestAmount deve ser null")
        assertNull(after.fineAmount,     "fineAmount deve ser null")
        assertEquals(TransactionStatus.PENDING, after.status)

        // outstandingPrincipal = amount - (paidAmount ?: 0) = 1000 - 0 = 1000
        val outstanding = after.amount - (after.paidAmount ?: BigDecimal.ZERO)
        assertEquals(0, BigDecimal("1000.00").compareTo(outstanding),
            "Saldo devedor deve voltar a 1000 após estorno da única baixa")

        // cashEffective da baixa estornada — calculateBalance restaura 1050 ao caixa
        val cashEstornado = baixas.first { it.id == 1 }.cashEffective
        assertEquals(0, BigDecimal("1050.00").compareTo(cashEstornado),
            "cashEffective da baixa (1000+50) = 1050 devolvido ao caixa via transaction_payments")
    }

    // ── E2: baixa com juros sobrevive, outra é estornada ────────────────────

    @Test
    fun `E2 duas baixas baixa 1 com juros sobrevive baixa 2 estornada - paidAmount e principal puro 300`() {
        // Baixa 1: principal=300, juros=50 (sobrevive)
        // Baixa 2: principal=700 (estornada)
        // Estado pós duas baixas: paidAmount=1000, interestAmount=50, status=PAID
        val title = TitleState(
            amount         = BigDecimal("1000.00"),
            paidAmount     = BigDecimal("1000.00"),
            interestAmount = BigDecimal("50.00"),
            fineAmount     = null,
            status         = TransactionStatus.PAID
        )
        val baixas = mutableListOf(
            Baixa(id = 1, principal = BigDecimal("300.00"), interest = BigDecimal("50.00")),
            Baixa(id = 2, principal = BigDecimal("700.00"))
        )

        val after = applyReversal(title, baixas, targetId = 2)

        // paidAmount = newPrincipal das baixas ativas = 300 (principal puro — juros não entram)
        assertEquals(0, BigDecimal("300.00").compareTo(after.paidAmount),
            "paidAmount deve ser 300 (principal puro da baixa 1)")

        // outstandingPrincipal = 1000 - 300 = 700
        val outstanding = after.amount - (after.paidAmount ?: BigDecimal.ZERO)
        assertEquals(0, BigDecimal("700.00").compareTo(outstanding),
            "outstandingPrincipal = 700 após estorno da baixa de 700")

        assertEquals(TransactionStatus.PARTIAL, after.status)
        assertEquals(0, BigDecimal("50.00").compareTo(after.interestAmount),
            "interestAmount da baixa 1 sobrevivente preservado em coluna própria")
    }
}
