package br.com.sisgfin.recurrence

import java.time.LocalDate
import java.time.YearMonth

internal object RecurrenceDateCalculator {

    /**
     * Retorna todas as datas de vencimento para [template] no intervalo [[from], [horizon]].
     * Respeita [RecurrenceTemplate.startsAt], [RecurrenceTemplate.endsAt] e o
     * ajuste de meses curtos (e.g. dia 31 em fevereiro → último dia do mês).
     */
    fun nextDueDates(
        template: RecurrenceTemplate,
        from: LocalDate,
        horizon: LocalDate
    ): List<LocalDate> {
        val startsAt      = template.startsAt.toLocalDate()
        val endsAt        = template.endsAt?.toLocalDate()
        val effectiveFrom = if (startsAt.isAfter(from)) startsAt else from

        return when (template.interval) {
            RecurrenceInterval.SEMANAL   -> weekly(effectiveFrom, horizon, endsAt, weeks = 1)
            RecurrenceInterval.QUINZENAL -> weekly(effectiveFrom, horizon, endsAt, weeks = 2)
            else                         -> monthly(template, effectiveFrom, horizon, endsAt)
        }
    }

    private fun monthly(
        template: RecurrenceTemplate,
        from: LocalDate,
        horizon: LocalDate,
        endsAt: LocalDate?
    ): List<LocalDate> {
        val results      = mutableListOf<LocalDate>()
        var cursor       = YearMonth.from(from)
        val horizonMonth = YearMonth.from(horizon)

        while (!cursor.isAfter(horizonMonth)) {
            val day  = template.dayOfMonth.coerceAtMost(cursor.lengthOfMonth())
            val date = cursor.atDay(day)
            if (!date.isBefore(from) && !date.isAfter(horizon)) {
                if (endsAt == null || !date.isAfter(endsAt)) {
                    results.add(date)
                }
            }
            cursor = cursor.plusMonths(template.interval.months.coerceAtLeast(1))
        }
        return results
    }

    private fun weekly(
        from: LocalDate,
        horizon: LocalDate,
        endsAt: LocalDate?,
        weeks: Long
    ): List<LocalDate> {
        val results = mutableListOf<LocalDate>()
        var cursor  = from
        while (!cursor.isAfter(horizon)) {
            if (endsAt == null || !cursor.isAfter(endsAt)) {
                results.add(cursor)
            }
            cursor = cursor.plusWeeks(weeks)
        }
        return results
    }
}
