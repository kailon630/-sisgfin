package br.com.sisgfin.budget

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object BudgetItemsTable : Table("budget_items") {
    val id            = integer("id").autoIncrement()
    val costCenterId  = integer("project_id")
    val categoryId    = integer("category_id")
    val year          = integer("year")
    val monthlyAmount = decimal("monthly_amount", 19, 2)
    val annualAmount  = decimal("annual_amount", 19, 2)
    val notes         = text("notes").nullable()
    val isActive      = bool("is_active").default(true)
    val createdBy     = integer("created_by").nullable()
    val createdAt     = datetime("created_at").default(LocalDateTime.now())
    val updatedAt     = datetime("updated_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}
