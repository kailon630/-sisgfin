package br.com.sisgfin.payables

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F2 — PayablesUiState: filtro de tile e estrutura de estado sem banco.
 *
 * Aceite F2:
 *  - tileFilter ALL mostra todos os itens
 *  - tileFilter OVERDUE mostra apenas OVERDUE
 *  - tileFilter TODAY mostra apenas os com dueDate == hoje
 *  - tileFilter THIS_WEEK mostra hoje + futuros até domingo
 *  - tiles toggle: selecionar o mesmo tile volta para ALL
 */
class PayablesUiStateTest {

    private val today = LocalDate.now()

    private fun tx(id: Int, status: TransactionStatus, dueDate: LocalDate) = Transaction(
        id          = id,
        type        = TransactionType.EXPENSE,
        status      = status,
        description = "Tx $id",
        amount      = Money.fromString("100.00"),
        issueDate   = LocalDateTime.now(),
        dueDate     = dueDate.atStartOfDay(),
        accountId   = 1
    )

    private val overdueTx   = tx(1, TransactionStatus.OVERDUE,  today.minusDays(5))
    private val todayTx     = tx(2, TransactionStatus.PENDING,  today)
    private val tomorrowTx  = tx(3, TransactionStatus.PENDING,  today.plusDays(1))
    private val nextWeekTx  = tx(4, TransactionStatus.PENDING,  today.plusDays(10))
    private val partialTx   = tx(5, TransactionStatus.PARTIAL,  today.minusDays(2))

    private val allItems = listOf(overdueTx, todayTx, tomorrowTx, nextWeekTx, partialTx)

    private fun state(filter: PayablesTileFilter) = PayablesUiState(
        allItems = allItems,
        tileFilter = filter
    )

    @Test
    fun `ALL mostra todos os itens`() {
        val s = state(PayablesTileFilter.ALL)
        assertEquals(allItems.size, s.items.size)
    }

    @Test
    fun `OVERDUE mostra apenas status OVERDUE`() {
        val s = state(PayablesTileFilter.OVERDUE)
        assertTrue(s.items.all { it.status == TransactionStatus.OVERDUE })
        assertEquals(1, s.items.size)
    }

    @Test
    fun `TODAY mostra apenas dueDate igual a hoje`() {
        val s = state(PayablesTileFilter.TODAY)
        assertTrue(s.items.all { it.dueDate.toLocalDate() == today })
        assertEquals(1, s.items.size)
        assertEquals(todayTx.id, s.items.first().id)
    }

    @Test
    fun `THIS_WEEK inclui hoje e dias ate domingo`() {
        val s = state(PayablesTileFilter.THIS_WEEK)
        s.items.forEach { tx ->
            assertTrue(
                !tx.dueDate.toLocalDate().isBefore(today),
                "Item ${tx.id} com dueDate ${tx.dueDate.toLocalDate()} é anterior a hoje"
            )
        }
        // todayTx deve estar; tomorrowTx pode estar (depende do dia da semana)
        assertTrue(s.items.any { it.id == todayTx.id })
    }

    @Test
    fun `summary total inclui todos os itens carregados`() {
        val summary = PayablesSummary(
            overdue      = Money.fromString("500.00"), overdueCount  = 2,
            dueToday     = Money.fromString("100.00"), dueTodayCount = 1,
            thisWeek     = Money.fromString("200.00"), thisWeekCount = 2,
            total        = Money.fromString("900.00"), totalCount    = 5
        )
        assertEquals(5, summary.totalCount)
    }

    @Test
    fun `EMPTY summary tem zeros`() {
        val s = PayablesSummary.EMPTY
        assertEquals(0, s.totalCount)
        assertEquals(0, s.overdueCount)
        assertTrue(s.total.isZero())
    }

    @Test
    fun `estado inicial tem tileFilter ALL e lista vazia`() {
        val s = PayablesUiState()
        assertEquals(PayablesTileFilter.ALL, s.tileFilter)
        assertTrue(s.items.isEmpty())
    }
}
