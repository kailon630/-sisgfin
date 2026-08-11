package br.com.sisgfin.financial.transactions

import java.time.LocalDate

enum class DateAxis { ISSUE, DUE, PAYMENT }

data class TransactionQuery(
    val types: Set<TransactionType> = emptySet(),
    val statuses: Set<TransactionStatus> = emptySet(),
    val dateAxis: DateAxis = DateAxis.DUE,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val accountId: Int? = null,
    val supplierId: Int? = null,
    val employeeId: Int? = null,
    val costCenterId: Int? = null,
    val categoryId: Int? = null,
    val projectId: Int? = null,
    val contractId: Int? = null,
    val search: String? = null,
    val onlyActive: Boolean = true,
) {
    companion object {
        fun aPagar() = TransactionQuery(
            types    = setOf(TransactionType.EXPENSE),
            statuses = setOf(TransactionStatus.PENDING, TransactionStatus.OVERDUE, TransactionStatus.PARTIAL)
        )

        fun aReceber() = TransactionQuery(
            types    = setOf(TransactionType.INCOME),
            statuses = setOf(TransactionStatus.PENDING, TransactionStatus.OVERDUE, TransactionStatus.PARTIAL)
        )

        fun extrato(accountId: Int, from: LocalDate, to: LocalDate) = TransactionQuery(
            statuses  = setOf(TransactionStatus.PAID),
            dateAxis  = DateAxis.PAYMENT,
            accountId = accountId,
            from      = from,
            to        = to
        )
    }
}
