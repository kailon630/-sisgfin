package br.com.sisgfin.financial.transactions

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F0 — TransactionQuery: factory methods e estrutura sem banco.
 *
 * Aceite F0:
 *  - aPagar() estruturalmente equivale a filterActionRequired() filtrado por EXPENSE
 *  - extrato() estruturalmente equivale a findStatementEntries() com eixo PAYMENT
 */
class TransactionQueryTest {

    @Test
    fun `defaults — tipos e status vazios, eixo DUE, onlyActive true`() {
        val q = TransactionQuery()
        assertTrue(q.types.isEmpty())
        assertTrue(q.statuses.isEmpty())
        assertEquals(DateAxis.DUE, q.dateAxis)
        assertNull(q.from)
        assertNull(q.to)
        assertNull(q.accountId)
        assertTrue(q.onlyActive)
    }

    // ── aPagar ───────────────────────────────────────────────────────────────

    @Test
    fun `aPagar() filtra somente EXPENSE`() {
        assertEquals(setOf(TransactionType.EXPENSE), TransactionQuery.aPagar().types)
    }

    @Test
    fun `aPagar() equivale a filterActionRequired filtrado por EXPENSE`() {
        val q = TransactionQuery.aPagar()
        // filterActionRequired usa PENDING + OVERDUE + PARTIAL de qualquer tipo;
        // aPagar() restringe aos mesmos status, mas somente EXPENSE.
        assertEquals(
            setOf(TransactionStatus.PENDING, TransactionStatus.OVERDUE, TransactionStatus.PARTIAL),
            q.statuses
        )
        assertEquals(setOf(TransactionType.EXPENSE), q.types)
        assertEquals(DateAxis.DUE, q.dateAxis)
        assertTrue(q.onlyActive)
        assertNull(q.from)
        assertNull(q.to)
    }

    @Test
    fun `aPagar() nao inclui PAID nem CANCELED`() {
        val statuses = TransactionQuery.aPagar().statuses
        assertFalse(statuses.contains(TransactionStatus.PAID))
        assertFalse(statuses.contains(TransactionStatus.CANCELED))
    }

    // ── aReceber ─────────────────────────────────────────────────────────────

    @Test
    fun `aReceber() filtra somente INCOME`() {
        assertEquals(setOf(TransactionType.INCOME), TransactionQuery.aReceber().types)
    }

    @Test
    fun `aReceber() usa os mesmos status que aPagar()`() {
        assertEquals(TransactionQuery.aPagar().statuses, TransactionQuery.aReceber().statuses)
    }

    @Test
    fun `aPagar e aReceber tipos disjuntos`() {
        val intersect = TransactionQuery.aPagar().types.intersect(TransactionQuery.aReceber().types)
        assertTrue(intersect.isEmpty())
    }

    // ── extrato ──────────────────────────────────────────────────────────────

    @Test
    fun `extrato() equivale a findStatementEntries com eixo PAYMENT`() {
        val from = LocalDate.of(2026, 3, 1)
        val to   = LocalDate.of(2026, 3, 31)
        val q    = TransactionQuery.extrato(accountId = 5, from = from, to = to)

        // findStatementEntries filtra: accountId, PAID, isActive, paymentDate no período
        assertEquals(DateAxis.PAYMENT, q.dateAxis)
        assertEquals(setOf(TransactionStatus.PAID), q.statuses)
        assertEquals(5, q.accountId)
        assertEquals(from, q.from)
        assertEquals(to, q.to)
        assertTrue(q.onlyActive)
        // tipos vazio = todos (INCOME, EXPENSE, REVERSAL etc.)
        assertTrue(q.types.isEmpty())
    }

    @Test
    fun `extrato() nao inclui status em aberto`() {
        val q = TransactionQuery.extrato(1, LocalDate.now(), LocalDate.now())
        assertFalse(q.statuses.contains(TransactionStatus.PENDING))
        assertFalse(q.statuses.contains(TransactionStatus.OVERDUE))
        assertFalse(q.statuses.contains(TransactionStatus.PARTIAL))
    }

    @Test
    fun `extrato() eixo PAYMENT implica paymentDate como criterio de data`() {
        val q = TransactionQuery.extrato(1, LocalDate.now(), LocalDate.now())
        assertEquals(DateAxis.PAYMENT, q.dateAxis)
    }

    // ── composição manual ────────────────────────────────────────────────────

    @Test
    fun `query com search e costCenterId`() {
        val q = TransactionQuery(
            types       = setOf(TransactionType.EXPENSE),
            costCenterId = 3,
            search      = "folha"
        )
        assertEquals("folha", q.search)
        assertEquals(3, q.costCenterId)
        assertNull(q.supplierId)
    }

    @Test
    fun `query com onlyActive false inclui inativos`() {
        val q = TransactionQuery(onlyActive = false)
        assertFalse(q.onlyActive)
    }
}
