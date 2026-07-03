package br.com.sisgfin.financial.transactions.workflow

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object TransactionEventsTable : Table("transaction_events") {
    val id = integer("id").autoIncrement()
    val transactionId = integer("transaction_id")
    val eventType = varchar("event_type", 50)
    val description = varchar("description", 255)
    val amount = decimal("amount", 19, 2).nullable()
    val statusFrom = varchar("status_from", 20).nullable()
    val statusTo = varchar("status_to", 20).nullable()
    val performedBy = integer("performed_by").nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

data class TransactionEvent(
    val id: Int = 0,
    val transactionId: Int,
    val eventType: String,
    val description: String,
    val amount: br.com.sisgfin.financial.money.Money? = null,
    val statusFrom: String? = null,
    val statusTo: String? = null,
    val performedBy: Int? = null,
    val createdAt: java.time.LocalDateTime = java.time.LocalDateTime.now()
)
