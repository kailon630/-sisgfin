package br.com.sisgfin.financial.transactions.timeline

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object TransactionTimelineTable : Table("transaction_events") {
    val id = integer("id").autoIncrement()
    val transactionId = integer("transaction_id")
    val eventType = varchar("event_type", 50)
    val message = varchar("description", 255)
    val amountValue = decimal("amount", 19, 2).nullable()
    val statusFrom = varchar("status_from", 20).nullable()
    val statusTo = varchar("status_to", 20).nullable()
    val performedBy = integer("performed_by").nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}
