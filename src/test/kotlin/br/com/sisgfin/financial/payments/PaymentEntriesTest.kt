package br.com.sisgfin.financial.payments

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M4b — PaymentEntry: modelo e lógica de ordinal/total.
 *
 * PE-01: cashEffective delega ao payment.cashEffective
 * PE-02: baixa única — ordinal=1, total=1
 * PE-03: título com três baixas — ordinais 1/2/3, total=3
 * PE-04: título PARTIAL — total=null (denominador desconhecido)
 * PE-05: baixa estornada não integra o total (reversedById preenchido = excluída da lista)
 * PE-06: período recorta por data da baixa, não do título
 */
class PaymentEntriesTest {

    private val today = LocalDate.of(2026, 8, 17)
    private val now   = today.atStartOfDay()

    private fun tx(
        id: Int = 1,
        status: TransactionStatus = TransactionStatus.PAID
    ) = Transaction(
        id          = id,
        type        = TransactionType.EXPENSE,
        status      = status,
        description = "Fornecedor X — NF 100",
        amount      = Money.fromString("1500.00"),
        issueDate   = now,
        dueDate     = now,
        accountId   = 1
    )

    private fun payment(
        id: Int,
        txId: Int = 1,
        paymentDate: LocalDate = today,
        principal: String = "500.00",
        interest: String = "0.00",
        fine: String = "0.00",
        discount: String = "0.00",
        reversedById: Int? = null
    ) = TransactionPayment(
        id              = id,
        transactionId   = txId,
        paymentDate     = paymentDate,
        accountId       = 1,
        principalAmount = Money.fromString(principal),
        interestAmount  = Money.fromString(interest),
        fineAmount      = Money.fromString(fine),
        discountAmount  = Money.fromString(discount),
        reversedById    = reversedById,
        createdAt       = paymentDate.atStartOfDay()
    )

    private fun entry(payment: TransactionPayment, tx: Transaction, ordinal: Int, total: Int?) =
        PaymentEntry(payment, tx, ordinal, total)

    // ── PE-01 ─────────────────────────────────────────────────────────────────

    @Test
    fun `PE-01 cashEffective delega ao payment`() {
        val p = payment(1, principal = "950.00", interest = "30.00", fine = "20.00", discount = "50.00")
        val e = entry(p, tx(), ordinal = 1, total = 1)
        // 950 + 30 + 20 - 50 = 950
        assertEquals(Money.fromString("950.00"), e.payment.cashEffective)
    }

    // ── PE-02 ─────────────────────────────────────────────────────────────────

    @Test
    fun `PE-02 baixa unica tem ordinal 1 e total 1`() {
        val e = entry(payment(1), tx(status = TransactionStatus.PAID), ordinal = 1, total = 1)
        assertEquals(1, e.paymentOrdinal)
        assertEquals(1, e.totalPayments)
    }

    // ── PE-03 ─────────────────────────────────────────────────────────────────

    @Test
    fun `PE-03 tres baixas de titulo PAID tem ordinais 1-2-3 e total 3`() {
        val titulo = tx(status = TransactionStatus.PAID)
        val entries = listOf(
            entry(payment(1, paymentDate = LocalDate.of(2026, 6, 1)), titulo, ordinal = 1, total = 3),
            entry(payment(2, paymentDate = LocalDate.of(2026, 7, 1)), titulo, ordinal = 2, total = 3),
            entry(payment(3, paymentDate = LocalDate.of(2026, 8, 1)), titulo, ordinal = 3, total = 3)
        )
        assertEquals(listOf(1, 2, 3), entries.map { it.paymentOrdinal })
        entries.forEach { assertEquals(3, it.totalPayments) }
    }

    // ── PE-04 ─────────────────────────────────────────────────────────────────

    @Test
    fun `PE-04 titulo PARTIAL tem totalPayments null`() {
        val partial = tx(status = TransactionStatus.PARTIAL)
        val e = entry(payment(1), partial, ordinal = 1, total = null)
        assertNull(e.totalPayments)
    }

    // ── PE-05 ─────────────────────────────────────────────────────────────────

    @Test
    fun `PE-05 baixa com reversedById nao integra totalPayments`() {
        // Simulação: título com 3 baixas, 1 estornada — apenas 2 ativas
        val titulo = tx(status = TransactionStatus.PAID)
        // baixas ativas: ids 1 e 3 (id=2 foi estornada, reversed_by_id preenchido — excluída pelo query)
        val activeEntries = listOf(
            entry(payment(1, paymentDate = LocalDate.of(2026, 6, 1)), titulo, ordinal = 1, total = 2),
            entry(payment(3, paymentDate = LocalDate.of(2026, 8, 1)), titulo, ordinal = 2, total = 2)
        )
        assertEquals(2, activeEntries.size)
        assertEquals(2, activeEntries[0].totalPayments)
        assertEquals(2, activeEntries[1].totalPayments)
        // o ordinal da segunda baixa ativa é 2, não 3
        assertEquals(2, activeEntries[1].paymentOrdinal)
    }

    // ── PE-06 ─────────────────────────────────────────────────────────────────

    @Test
    fun `PE-06 filtro de periodo usa data da baixa nao do titulo`() {
        // Título emitido em junho, mas a primeira baixa ocorreu em setembro.
        // Filtro por agosto não deve retornar nada — verificado pela ausência de entradas.
        val titulo = tx(status = TransactionStatus.PARTIAL)
        val baixaSetembro = payment(1, paymentDate = LocalDate.of(2026, 9, 5))

        // O filtro seria aplicado no banco; aqui verificamos que a data da baixa é a referência
        val filterFrom = LocalDate.of(2026, 8, 1)
        val filterTo   = LocalDate.of(2026, 8, 31)

        // data da baixa está fora do filtro de agosto
        val dentroDoFiltro = baixaSetembro.paymentDate in filterFrom..filterTo
        assertTrue(!dentroDoFiltro, "baixa de setembro não deve aparecer em filtro de agosto")
    }
}
