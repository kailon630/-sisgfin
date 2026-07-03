package br.com.sisgfin.financial.transactions

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine

object TransactionValidator {

    fun validate(transaction: Transaction, previousStatus: TransactionStatus? = null) {
        val errors = mutableListOf<String>()

        if (transaction.description.isBlank()) {
            errors += "Descrição é obrigatória."
        }
        if (transaction.accountId <= 0) {
            errors += "Conta financeira é obrigatória."
        }
        if (transaction.amount.isZero() || transaction.amount.isNegative()) {
            errors += "Valor deve ser maior que zero."
        }

        when (transaction.status) {
            TransactionStatus.PAID -> {
                if (transaction.paymentDate == null) {
                    errors += "Status Pago exige data de pagamento."
                } else if (transaction.paymentDate.isBefore(transaction.issueDate)) {
                    errors += "Data de pagamento não pode ser anterior à data de emissão."
                }
                if (transaction.paidAmount == null || transaction.paidAmount.isZero()) {
                    errors += "Status Pago exige valor pago."
                }
            }
            TransactionStatus.PARTIAL -> {
                if (transaction.paymentDate == null) {
                    errors += "Status Parcial exige data de pagamento."
                } else if (transaction.paymentDate.isBefore(transaction.issueDate)) {
                    errors += "Data de pagamento não pode ser anterior à data de emissão."
                }
                if (transaction.paidAmount == null || transaction.paidAmount.isZero()) {
                    errors += "Status Parcial exige valor pago."
                }
                if (transaction.paidAmount != null && transaction.paidAmount.compareTo(transaction.amount) >= 0) {
                    errors += "Pagamento parcial deve ser menor que o valor total."
                }
            }
            else -> Unit
        }

        if (previousStatus != null && previousStatus != transaction.status) {
            try {
                TransactionStateMachine.assertTransition(previousStatus, transaction.status)
            } catch (e: IllegalStateException) {
                errors += e.message ?: "Transição inválida."
            }
        }

        if (transaction.type == TransactionType.TRANSFER && transaction.supplierId != null) {
            errors += "Transferência não deve vincular fornecedor nesta versão."
        }

        if (errors.isNotEmpty()) {
            throw IllegalArgumentException(errors.joinToString(" "))
        }
    }

    /**
     * RN-08: retorna mensagem de aviso se dueDate estiver fora do período do convênio.
     * Retorna null quando tudo está ok ou quando o projeto não tem período definido.
     * Não lança exceção — é aviso, não bloqueio.
     */
    fun checkProjectPeriod(
        dueDate: java.time.LocalDateTime,
        startDate: java.time.LocalDateTime?,
        endDate: java.time.LocalDateTime?
    ): String? {
        if (startDate == null && endDate == null) return null
        val beforeStart = startDate != null && dueDate.isBefore(startDate)
        val afterEnd = endDate != null && dueDate.isAfter(endDate)
        if (!beforeStart && !afterEnd) return null
        val start = startDate?.toLocalDate()?.toString() ?: "?"
        val end = endDate?.toLocalDate()?.toString() ?: "?"
        return "Aviso: data de vencimento fora do período do convênio ($start a $end)."
    }

    fun validateForSave(transaction: Transaction, existing: Transaction?) {
        validate(transaction, previousStatus = existing?.status)
    }

    fun validatePayment(
        total: Money,
        paidAmount: Money,
        paymentDate: java.time.LocalDateTime,
        issueDate: java.time.LocalDateTime
    ) {
        if (paidAmount.isZero() || paidAmount.isNegative()) {
            throw IllegalArgumentException("Valor pago deve ser maior que zero.")
        }
        if (paidAmount.compareTo(total) > 0) {
            throw IllegalArgumentException("Valor pago não pode exceder o valor da transação.")
        }
        if (paymentDate.isBefore(issueDate)) {
            throw IllegalArgumentException("Data de pagamento não pode ser anterior à data de emissão.")
        }
    }
}
