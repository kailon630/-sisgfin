package br.com.sisgfin.financial.payments

import br.com.sisgfin.financial.money.Money
import java.time.LocalDate
import java.time.LocalDateTime

data class TransactionPayment(
    val id: Int = 0,
    val transactionId: Int,
    val paymentDate: LocalDate,
    val accountId: Int,
    val principalAmount: Money,
    val interestAmount: Money = Money.ZERO,
    val fineAmount: Money = Money.ZERO,
    val discountAmount: Money = Money.ZERO,
    val reversedById: Int? = null,
    val idempotencyKey: String? = null,
    val notes: String? = null,
    val createdBy: Int? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    val cashEffective: Money
        get() = principalAmount + interestAmount + fineAmount - discountAmount
}

data class ReconciliationDivergence(
    val transactionId: Int,
    val transactionAmount: Money,
    val paidAmount: Money,
    val somaBaixas: Money
) {
    val delta: Money get() = somaBaixas - paidAmount
}
