package br.com.sisgfin.statement

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.payments.PaymentEntry
import br.com.sisgfin.financial.payments.TransactionPayment
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionType
import java.time.LocalDate

data class StatementFilter(
    val accountId: Int? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val type: TransactionType? = null,
    val costCenterId: Int? = null,
    val categoryId: Int? = null,
    val projectId: Int? = null
)

data class StatementEntry(
    val transaction: Transaction,
    val payment: TransactionPayment,
    val signedAmount: Money,   // positivo = crédito, negativo = débito
    val runningBalance: Money
) {
    val isCredit: Boolean get() = signedAmount.isPositive()
}

fun signedAmount(pe: PaymentEntry): Money {
    val cash = pe.payment.cashEffective
    return when (pe.transaction.type) {
        TransactionType.INCOME,
        TransactionType.REVERSAL,
        TransactionType.ADJUSTMENT -> cash
        TransactionType.EXPENSE -> cash.negate()
        TransactionType.TRANSFER ->
            if (pe.transaction.parentTransactionId != null) cash   // entrada
            else cash.negate()                                      // saída
    }
}
