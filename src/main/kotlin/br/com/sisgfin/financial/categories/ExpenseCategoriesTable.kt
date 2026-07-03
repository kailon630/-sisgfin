package br.com.sisgfin.financial.categories

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object ExpenseCategoriesTable : Table("expense_categories") {
    val id          = integer("id").autoIncrement()
    val code        = varchar("code", 10).uniqueIndex()
    val name        = varchar("name", 100)
    val description = text("description").nullable()
    val groupCode   = varchar("group_code", 10).nullable()
    val groupName   = varchar("group_name", 100).nullable()
    val isIncome    = bool("is_income").default(false)
    val isActive    = bool("is_active").default(true)
    val createdAt   = datetime("created_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}
