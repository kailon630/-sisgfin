package br.com.sisgfin.financial.categories

import br.com.sisgfin.core.domain.MutableEntityRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExpenseCategoryRepository : MutableEntityRepository<ExpenseCategory> {

    override fun findAll(): List<ExpenseCategory> = transaction {
        ExpenseCategoriesTable
            .selectAll()
            .where { ExpenseCategoriesTable.isActive eq true }
            .orderBy(ExpenseCategoriesTable.code to SortOrder.ASC)
            .map { rowToCategory(it) }
    }

    override fun findById(id: Int): ExpenseCategory? = transaction {
        ExpenseCategoriesTable
            .selectAll()
            .where { ExpenseCategoriesTable.id eq id }
            .map { rowToCategory(it) }
            .singleOrNull()
    }

    fun findByCode(code: String): ExpenseCategory? = transaction {
        ExpenseCategoriesTable
            .selectAll()
            .where { ExpenseCategoriesTable.code eq code }
            .map { rowToCategory(it) }
            .singleOrNull()
    }

    fun findAllIncomes(): List<ExpenseCategory> = transaction {
        ExpenseCategoriesTable
            .selectAll()
            .where {
                (ExpenseCategoriesTable.isActive eq true) and
                (ExpenseCategoriesTable.isIncome eq true)
            }
            .orderBy(ExpenseCategoriesTable.code to SortOrder.ASC)
            .map { rowToCategory(it) }
    }

    fun findAllExpenses(): List<ExpenseCategory> = transaction {
        ExpenseCategoriesTable
            .selectAll()
            .where {
                (ExpenseCategoriesTable.isActive eq true) and
                (ExpenseCategoriesTable.isIncome eq false)
            }
            .orderBy(ExpenseCategoriesTable.code to SortOrder.ASC)
            .map { rowToCategory(it) }
    }

    override fun insert(entity: ExpenseCategory): Int = transaction {
        ExpenseCategoriesTable.insert {
            it[code]        = entity.code
            it[name]        = entity.name
            it[description] = entity.description
            it[groupCode]   = entity.groupCode
            it[groupName]   = entity.groupName
            it[isIncome]    = entity.isIncome
            it[isActive]    = entity.isActive
        } get ExpenseCategoriesTable.id
    }

    override fun update(entity: ExpenseCategory) {
        transaction {
            ExpenseCategoriesTable.update(
                { ExpenseCategoriesTable.id eq entity.id }
            ) {
                it[code]        = entity.code
                it[name]        = entity.name
                it[description] = entity.description
                it[groupCode]   = entity.groupCode
                it[groupName]   = entity.groupName
                it[isIncome]    = entity.isIncome
                it[isActive]    = entity.isActive
            }
        }
    }

    private fun rowToCategory(row: ResultRow) = ExpenseCategory(
        id          = row[ExpenseCategoriesTable.id],
        code        = row[ExpenseCategoriesTable.code],
        name        = row[ExpenseCategoriesTable.name],
        description = row[ExpenseCategoriesTable.description],
        groupCode   = row[ExpenseCategoriesTable.groupCode],
        groupName   = row[ExpenseCategoriesTable.groupName],
        isIncome    = row[ExpenseCategoriesTable.isIncome],
        isActive    = row[ExpenseCategoriesTable.isActive]
    )
}
