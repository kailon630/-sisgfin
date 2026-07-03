package br.com.sisgfin.contracts

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object ContractsTable : Table("contracts") {
    val id                   = integer("id").autoIncrement()
    val number               = varchar("number", 30)
    val description          = varchar("description", 255)
    val contractorId         = integer("contractor_id")
    val type                 = varchar("type", 20)
    val totalValue           = decimal("total_value", 19, 2)
    val startDate            = datetime("start_date")
    val endDate              = datetime("end_date").nullable()
    val status               = varchar("status", 20).default("VIGENTE")
    val notes                = text("notes").nullable()
    val recurrenceTemplateId = integer("recurrence_template_id").nullable()
    val createdBy            = integer("created_by").nullable()
    val createdAt            = datetime("created_at")
    val updatedAt            = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}
