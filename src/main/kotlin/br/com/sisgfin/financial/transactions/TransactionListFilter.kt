package br.com.sisgfin.financial.transactions

import java.time.LocalDate

sealed class TransactionListFilter {
    data object All : TransactionListFilter()
    // Ajuste 1: filtro padrão — PENDING + OVERDUE + PARTIAL (tudo que exige ação)
    data object ActionRequired : TransactionListFilter()
    data class ByStatus(val status: TransactionStatus) : TransactionListFilter()
    data class ByType(val type: TransactionType) : TransactionListFilter()
    data object DueToday : TransactionListFilter()
    data object Overdue : TransactionListFilter()
    data object Paid : TransactionListFilter()
    data class DuePeriod(val from: LocalDate, val to: LocalDate) : TransactionListFilter()
}
