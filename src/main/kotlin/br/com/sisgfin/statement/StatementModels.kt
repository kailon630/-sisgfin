package br.com.sisgfin.statement

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionType
import java.time.LocalDate

data class StatementFilter(
    val accountId: Int? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val type: TransactionType? = null,
    val costCenterId: Int? = null,
    val categoryId: Int? = null
)

data class StatementEntry(
    val transaction: Transaction,
    val signedAmount: Money,   // positivo = crédito, negativo = débito
    val runningBalance: Money
) {
    val isCredit: Boolean get() = signedAmount.isPositive()
}

fun signedAmount(tx: Transaction): Money = when (tx.type) {
    TransactionType.INCOME,
    TransactionType.REVERSAL,
    TransactionType.ADJUSTMENT -> tx.paidAmount ?: tx.amount
    TransactionType.EXPENSE -> (tx.paidAmount ?: tx.amount).negate()
    TransactionType.TRANSFER ->
        if (tx.parentTransactionId != null) tx.paidAmount ?: tx.amount   // entrada
        else (tx.paidAmount ?: tx.amount).negate()                        // saída
}
