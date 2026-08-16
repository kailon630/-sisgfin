package br.com.sisgfin.financial.payments

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object TransactionPaymentsTable : Table("transaction_payments") {
    val id              = integer("id").autoIncrement()
    val transactionId   = integer("transaction_id")
    val paymentDate     = date("payment_date")
    val accountId       = integer("account_id")
    val principalAmount = decimal("principal_amount", 19, 4)
    val interestAmount  = decimal("interest_amount", 19, 4)
    val fineAmount      = decimal("fine_amount", 19, 4)
    val discountAmount  = decimal("discount_amount", 19, 4)
    val reversedById    = integer("reversed_by_id").nullable()
    val idempotencyKey  = varchar("idempotency_key", 100).nullable()
    val notes           = text("notes").nullable()
    val createdBy       = integer("created_by").nullable()
    val createdAt       = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}
