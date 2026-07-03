package br.com.sisgfin.financial.transactions.workflow

import br.com.sisgfin.financial.money.toMoney
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class TransactionTimelineRepository {

    fun insert(event: TransactionEvent) = transaction {
        TransactionEventsTable.insert {
            it[transactionId] = event.transactionId
            it[eventType] = event.eventType
            it[description] = event.description
            it[amount] = event.amount?.value
            it[statusFrom] = event.statusFrom
            it[statusTo] = event.statusTo
            it[performedBy] = event.performedBy
            it[createdAt] = event.createdAt
        }
    }

    fun findByTransactionId(transactionId: Int): List<TransactionEvent> = transaction {
        TransactionEventsTable
            .selectAll()
            .where { TransactionEventsTable.transactionId eq transactionId }
            .orderBy(TransactionEventsTable.createdAt to SortOrder.DESC)
            .map { rowToEvent(it) }
    }

    private fun rowToEvent(row: ResultRow) = TransactionEvent(
        id = row[TransactionEventsTable.id],
        transactionId = row[TransactionEventsTable.transactionId],
        eventType = row[TransactionEventsTable.eventType],
        description = row[TransactionEventsTable.description],
        amount = row[TransactionEventsTable.amount]?.toMoney(),
        statusFrom = row[TransactionEventsTable.statusFrom],
        statusTo = row[TransactionEventsTable.statusTo],
        performedBy = row[TransactionEventsTable.performedBy],
        createdAt = row[TransactionEventsTable.createdAt]
    )
}
