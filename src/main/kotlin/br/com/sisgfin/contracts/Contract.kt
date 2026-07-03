package br.com.sisgfin.contracts

import br.com.sisgfin.core.domain.Identifiable
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionType
import java.time.LocalDateTime

data class Contract(
    override val id: Int = 0,
    val number: String,
    val description: String,
    val contractorId: Int,
    val type: TransactionType,
    val totalValue: Money,
    val startDate: LocalDateTime,
    val endDate: LocalDateTime? = null,
    val status: ContractStatus = ContractStatus.VIGENTE,
    val notes: String? = null,
    val recurrenceTemplateId: Int? = null,
    val createdBy: Int? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : Identifiable
