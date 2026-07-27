package br.com.sisgfin.financial.projects

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object ProjectsTable : Table("financial_projects") {
    val id          = integer("id").autoIncrement()
    val code        = varchar("code", 50).uniqueIndex()
    val name        = varchar("name", 150)
    val description = text("description").nullable()
    val status      = varchar("status", 20).default(ProjectStatus.EM_ANDAMENTO.name)
    val budget      = decimal("budget", 15, 2).nullable()
    val startDate   = date("start_date").nullable()
    val expectedEnd = date("expected_end").nullable()
    val actualEnd   = date("actual_end").nullable()
    val isActive    = bool("is_active").default(true)
    val createdAt   = datetime("created_at")
    val updatedAt   = datetime("updated_at")
    val createdBy   = integer("created_by").nullable()

    override val primaryKey = PrimaryKey(id)
}
