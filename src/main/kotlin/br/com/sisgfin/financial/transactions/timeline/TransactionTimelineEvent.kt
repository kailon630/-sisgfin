package br.com.sisgfin.financial.transactions.timeline

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionStatus
import java.time.LocalDateTime

data class TransactionTimelineEvent(
    val id: Int = 0,
    val transactionId: Int,
    val eventType: TimelineEventType,
    val message: String,
    val amountValue: Money? = null,
    val statusFrom: TransactionStatus? = null,
    val statusTo: TransactionStatus? = null,
    val performedBy: Int? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
