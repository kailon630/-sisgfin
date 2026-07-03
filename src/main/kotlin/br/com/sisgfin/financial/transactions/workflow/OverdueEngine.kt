package br.com.sisgfin.financial.transactions.workflow

import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionStatus
import java.time.LocalDate

/**
 * Motor de vencimento — domínio puro, invocável por scheduler futuro.
 */
object OverdueEngine {

    fun shouldMarkOverdue(transaction: Transaction, today: LocalDate = LocalDate.now()): Boolean {
        if (transaction.status != TransactionStatus.PENDING) return false
        if (!transaction.isActive) return false
        return transaction.dueDate.toLocalDate().isBefore(today)
    }

    fun applyOverdueStatus(transaction: Transaction, today: LocalDate = LocalDate.now()): Transaction {
        return if (shouldMarkOverdue(transaction, today)) {
            transaction.copy(status = TransactionStatus.OVERDUE)
        } else {
            transaction
        }
    }
}
