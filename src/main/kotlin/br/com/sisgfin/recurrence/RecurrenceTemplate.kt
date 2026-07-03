package br.com.sisgfin.recurrence

import br.com.sisgfin.core.domain.Identifiable
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionType
import java.time.LocalDateTime

data class RecurrenceTemplate(
    override val id: Int = 0,
    val description: String,
    val amount: Money,
    val type: TransactionType,
    val interval: RecurrenceInterval,
    val dayOfMonth: Int,
    val accountId: Int,
    val supplierId: Int? = null,
    val categoryId: Int? = null,
    val costCenterId: Int? = null,
    val documentType: String? = null,
    val notes: String? = null,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime? = null,
    val isActive: Boolean = true,
    val contractId: Int? = null,
    val createdBy: Int? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : Identifiable
