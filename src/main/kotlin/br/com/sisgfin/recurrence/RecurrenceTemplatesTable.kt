package br.com.sisgfin.recurrence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object RecurrenceTemplatesTable : Table("recurrence_templates") {
    val id           = integer("id").autoIncrement()
    val description  = varchar("description", 255)
    val amount       = decimal("amount", 19, 2)
    val type         = varchar("type", 20)
    val intervalType = varchar("interval_type", 20)
    val dayOfMonth   = integer("day_of_month")
    val accountId    = integer("account_id")
    val supplierId   = integer("supplier_id").nullable()
    val categoryId   = integer("category_id").nullable()
    val costCenterId = integer("cost_center_id").nullable()
    val documentType = varchar("document_type", 20).nullable()
    val notes        = text("notes").nullable()
    val startsAt     = datetime("starts_at")
    val endsAt       = datetime("ends_at").nullable()
    val isActive     = bool("is_active").default(true)
    val contractId   = integer("contract_id").nullable()
    val createdBy    = integer("created_by").nullable()
    val createdAt    = datetime("created_at")
    val updatedAt    = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}
