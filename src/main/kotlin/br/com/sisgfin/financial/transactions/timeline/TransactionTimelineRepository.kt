package br.com.sisgfin.financial.transactions.timeline

import br.com.sisgfin.financial.money.toMoney
import br.com.sisgfin.financial.transactions.TransactionStatus
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class TransactionTimelineRepository {

    fun findByTransactionId(transactionId: Int): List<TransactionTimelineEvent> = transaction {
        TransactionTimelineTable
            .selectAll()
            .where { TransactionTimelineTable.transactionId eq transactionId }
            .orderBy(TransactionTimelineTable.createdAt to SortOrder.ASC)
            .map { rowToEvent(it) }
    }

    fun insert(event: TransactionTimelineEvent): Int = transaction {
        TransactionTimelineTable.insert {
            it[transactionId] = event.transactionId
            it[eventType] = event.eventType.name
            it[message] = event.message
            it[amountValue] = event.amountValue?.value
            it[statusFrom] = event.statusFrom?.name
            it[statusTo] = event.statusTo?.name
            it[performedBy] = event.performedBy
            it[createdAt] = event.createdAt
        } get TransactionTimelineTable.id
    }

    private fun rowToEvent(row: ResultRow) = TransactionTimelineEvent(
        id = row[TransactionTimelineTable.id],
        transactionId = row[TransactionTimelineTable.transactionId],
        eventType = TimelineEventType.valueOf(row[TransactionTimelineTable.eventType]),
        message = row[TransactionTimelineTable.message],
        amountValue = row[TransactionTimelineTable.amountValue]?.toMoney(),
        statusFrom = row[TransactionTimelineTable.statusFrom]?.let { TransactionStatus.valueOf(it) },
        statusTo = row[TransactionTimelineTable.statusTo]?.let { TransactionStatus.valueOf(it) },
        performedBy = row[TransactionTimelineTable.performedBy],
        createdAt = row[TransactionTimelineTable.createdAt]
    )
}
