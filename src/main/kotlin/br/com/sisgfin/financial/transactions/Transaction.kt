package br.com.sisgfin.financial.transactions

import br.com.sisgfin.core.domain.Activatable
import br.com.sisgfin.core.domain.Identifiable
import br.com.sisgfin.financial.money.Money
import java.time.LocalDateTime

/**
 * Entidade oficial do motor transacional (contas a pagar/receber).
 * Persistida em `financial_transactions` — separada do legado `transactions`.
 */
data class Transaction(
    override val id: Int = 0,
    val type: TransactionType,
    val status: TransactionStatus,
    val description: String,
    val amount: Money,
    val issueDate: LocalDateTime,
    val dueDate: LocalDateTime,
    val paymentDate: LocalDateTime? = null,
    val paidAmount: Money? = null,
    val accountId: Int,
    val supplierId: Int? = null,
    val costCenterId: Int? = null,
    val notes: String? = null,
    val documentType: String? = null,
    val documentNumber: String? = null,
    val installmentCurrent: Int? = null,
    val installmentTotal: Int? = null,
    val categoryId: Int? = null,
    val createdBy: Int? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    override val isActive: Boolean = true,
    val parentTransactionId: Int? = null,
    val ledgerEntryId: Int? = null,
    val employeeId: Int? = null,
    val ofxFitId: String? = null,
    val reconciledWithFitId: String? = null,
    val recurrenceTemplateId: Int? = null,
    val contractId: Int? = null,
    val interestAmount: Money? = null,
    val fineAmount: Money? = null,
    val projectId: Int? = null,
    /** Tipo do original quando `type == REVERSAL`; define a direção no saldo. */
    val reversedType: TransactionType? = null,
    val origin: TransactionOrigin = TransactionOrigin.MANUAL,
    val version: Int = 0
) : Identifiable, Activatable {

    /** Principal amortizado. `paidAmount` é principal puro — encargos são colunas à parte. */
    val principalPaid: Money
        get() = paidAmount ?: Money.ZERO

    /** Saldo devedor do título. */
    val outstandingPrincipal: Money
        get() = amount - principalPaid
}
