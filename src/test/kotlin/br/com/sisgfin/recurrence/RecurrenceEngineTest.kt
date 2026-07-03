package br.com.sisgfin.recurrence

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes de lógica pura do RecurrenceDateCalculator — sem banco.
 * Cobre: geração mensal, bimestral, semestral, semanal, quinzenal,
 * edge cases de meses curtos, startsAt futuro, endsAt, janela zero.
 */
class RecurrenceEngineTest {

    // ── fixture ─────────────────────────────────────────────────────────────

    private val amount = Money.fromDouble(100.0)

    private fun template(
        interval: RecurrenceInterval,
        dayOfMonth: Int,
        startsAt: LocalDate = LocalDate.of(2025, 1, 1),
        endsAt: LocalDate? = null,
    ) = RecurrenceTemplate(
        description = "Teste",
        amount      = amount,
        type        = TransactionType.EXPENSE,
        interval    = interval,
        dayOfMonth  = dayOfMonth,
        accountId   = 1,
        startsAt    = startsAt.atStartOfDay(),
        endsAt      = endsAt?.atStartOfDay()
    )

    private fun dates(
        t: RecurrenceTemplate,
        from: LocalDate,
        to: LocalDate
    ) = RecurrenceDateCalculator.nextDueDates(t, from, to)

    // ── mensal básico ────────────────────────────────────────────────────────

    @Test
    fun `mensal dia 15 gera tres datas de jan a mar`() {
        val t  = template(RecurrenceInterval.MENSAL, dayOfMonth = 15)
        val ds = dates(t, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31))
        assertEquals(
            listOf(LocalDate.of(2025, 1, 15), LocalDate.of(2025, 2, 15), LocalDate.of(2025, 3, 15)),
            ds
        )
    }

    @Test
    fun `mensal gera exatamente uma data quando from e horizon no mesmo mes`() {
        val t  = template(RecurrenceInterval.MENSAL, dayOfMonth = 10)
        val ds = dates(t, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31))
        assertEquals(listOf(LocalDate.of(2025, 3, 10)), ds)
    }

    @Test
    fun `mensal nao gera nada quando dia do mes ficou antes de from`() {
        val t  = template(RecurrenceInterval.MENSAL, dayOfMonth = 5)
        val ds = dates(t, LocalDate.of(2025, 3, 10), LocalDate.of(2025, 3, 31))
        assertTrue(ds.isEmpty(), "Dia 5 ficou antes do from=10, não deve gerar")
    }

    // ── edge cases de meses curtos ───────────────────────────────────────────

    @Test
    fun `dia 31 em fevereiro ajusta para ultimo dia do mes`() {
        val t  = template(RecurrenceInterval.MENSAL, dayOfMonth = 31)
        val ds = dates(t, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31))
        assertEquals(LocalDate.of(2025, 1, 31), ds[0])
        assertEquals(LocalDate.of(2025, 2, 28), ds[1])  // 2025 não é bissexto
        assertEquals(LocalDate.of(2025, 3, 31), ds[2])
    }

    @Test
    fun `dia 31 em fevereiro bissexto ajusta para 29`() {
        val t  = template(RecurrenceInterval.MENSAL, dayOfMonth = 31,
            startsAt = LocalDate.of(2024, 1, 1))
        val ds = dates(t, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
        assertEquals(listOf(LocalDate.of(2024, 2, 29)), ds)
    }

    @Test
    fun `dia 31 em abril ajusta para 30`() {
        val t  = template(RecurrenceInterval.MENSAL, dayOfMonth = 31)
        val ds = dates(t, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30))
        assertEquals(listOf(LocalDate.of(2025, 4, 30)), ds)
    }

    @Test
    fun `dia 30 em fevereiro ajusta para ultimo dia do mes`() {
        val t  = template(RecurrenceInterval.MENSAL, dayOfMonth = 30)
        val ds = dates(t, LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28))
        assertEquals(listOf(LocalDate.of(2025, 2, 28)), ds)
    }

    // ── startsAt e endsAt ────────────────────────────────────────────────────

    @Test
    fun `startsAt no futuro e respeitado como ponto inicial`() {
        val t  = template(RecurrenceInterval.MENSAL, dayOfMonth = 15,
            startsAt = LocalDate.of(2025, 3, 1))
        val ds = dates(t, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 5, 31))
        assertEquals(LocalDate.of(2025, 3, 15), ds.first())
        assertFalse(ds.any { it.isBefore(LocalDate.of(2025, 3, 1)) },
            "Nenhuma data deve ser anterior ao startsAt")
    }

    @Test
    fun `endsAt e respeitado - nao gera alem do fim`() {
        val t  = template(RecurrenceInterval.MENSAL, dayOfMonth = 10,
            endsAt = LocalDate.of(2025, 2, 28))
        val ds = dates(t, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 30))
        assertEquals(2, ds.size)
        assertEquals(LocalDate.of(2025, 2, 10), ds.last())
    }

    @Test
    fun `janela zero nao gera datas alem de hoje`() {
        val today = LocalDate.now()
        val t     = template(RecurrenceInterval.MENSAL, dayOfMonth = today.dayOfMonth,
            startsAt = today.minusMonths(1))
        val ds    = dates(t, today, today)
        assertTrue(ds.all { !it.isAfter(today) }, "Nenhuma data pode ultrapassar horizon=hoje")
    }

    // ── intervalos compostos ─────────────────────────────────────────────────

    @Test
    fun `bimestral dia 10 gera jan mar mai em janela de 6 meses`() {
        val t  = template(RecurrenceInterval.BIMESTRAL, dayOfMonth = 10)
        val ds = dates(t, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30))
        assertEquals(
            listOf(
                LocalDate.of(2025, 1, 10),
                LocalDate.of(2025, 3, 10),
                LocalDate.of(2025, 5, 10)
            ),
            ds
        )
    }

    @Test
    fun `trimestral dia 1 gera jan abr jul em 9 meses`() {
        val t  = template(RecurrenceInterval.TRIMESTRAL, dayOfMonth = 1)
        val ds = dates(t, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 9, 30))
        assertEquals(
            listOf(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 7, 1)
            ),
            ds
        )
    }

    @Test
    fun `semestral gera dois vencimentos em 12 meses`() {
        val t  = template(RecurrenceInterval.SEMESTRAL, dayOfMonth = 1)
        val ds = dates(t, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))
        assertEquals(
            listOf(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 7, 1)),
            ds
        )
    }

    @Test
    fun `anual gera apenas uma data em 12 meses`() {
        val t  = template(RecurrenceInterval.ANUAL, dayOfMonth = 15)
        val ds = dates(t, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))
        assertEquals(listOf(LocalDate.of(2025, 1, 15)), ds)
    }

    // ── semanal e quinzenal ──────────────────────────────────────────────────

    @Test
    fun `semanal gera 5 datas em 29 dias`() {
        val start = LocalDate.of(2025, 1, 6)  // segunda-feira
        val t     = template(RecurrenceInterval.SEMANAL, dayOfMonth = 1,
            startsAt = start)
        val ds    = dates(t, start, start.plusDays(28))
        assertEquals(5, ds.size)
        assertEquals(start, ds.first())
        assertEquals(start.plusWeeks(4), ds.last())
    }

    @Test
    fun `quinzenal gera datas a cada 14 dias`() {
        val start = LocalDate.of(2025, 1, 1)
        val t     = template(RecurrenceInterval.QUINZENAL, dayOfMonth = 1,
            startsAt = start)
        val ds    = dates(t, start, start.plusDays(42))
        assertEquals(4, ds.size)
        for (i in ds.indices) {
            assertEquals(start.plusWeeks(i.toLong() * 2), ds[i])
        }
    }

    @Test
    fun `quinzenal respeita endsAt`() {
        val start  = LocalDate.of(2025, 1, 1)
        val endsAt = LocalDate.of(2025, 1, 20)
        val t      = template(RecurrenceInterval.QUINZENAL, dayOfMonth = 1,
            startsAt = start, endsAt = endsAt)
        val ds     = dates(t, start, start.plusDays(60))
        assertTrue(ds.all { !it.isAfter(endsAt) })
        assertEquals(2, ds.size)
    }
}
