package br.com.sisgfin.budget

import br.com.sisgfin.core.domain.MutableEntityRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.toMoney
import br.com.sisgfin.financial.transactions.FinancialTransactionsTable
import br.com.sisgfin.financial.transactions.TransactionStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.year
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class BudgetItemRepository : MutableEntityRepository<BudgetItem> {

    override fun findAll(): List<BudgetItem> = transaction {
        BudgetItemsTable.selectAll()
            .where { BudgetItemsTable.isActive eq true }
            .orderBy(BudgetItemsTable.year to SortOrder.DESC, BudgetItemsTable.costCenterId to SortOrder.ASC)
            .map { rowToItem(it) }
    }

    fun findByYear(year: Int): List<BudgetItem> = transaction {
        BudgetItemsTable.selectAll()
            .where { (BudgetItemsTable.isActive eq true) and (BudgetItemsTable.year eq year) }
            .orderBy(BudgetItemsTable.costCenterId to SortOrder.ASC, BudgetItemsTable.categoryId to SortOrder.ASC)
            .map { rowToItem(it) }
    }

    fun findDuplicate(costCenterId: Int, categoryId: Int, year: Int, excludeId: Int = 0): BudgetItem? = transaction {
        BudgetItemsTable.selectAll()
            .where {
                (BudgetItemsTable.costCenterId eq costCenterId) and
                (BudgetItemsTable.categoryId eq categoryId) and
                (BudgetItemsTable.year eq year) and
                (BudgetItemsTable.isActive eq true)
            }
            .map { rowToItem(it) }
            .firstOrNull { it.id != excludeId }
    }

    override fun findById(id: Int): BudgetItem? = transaction {
        BudgetItemsTable.selectAll()
            .where { BudgetItemsTable.id eq id }
            .map { rowToItem(it) }
            .singleOrNull()
    }

    override fun insert(entity: BudgetItem): Int = transaction {
        BudgetItemsTable.insert {
            it[costCenterId]  = entity.costCenterId
            it[categoryId]    = entity.categoryId
            it[year]          = entity.year
            it[monthlyAmount] = entity.monthlyAmount.value
            it[annualAmount]  = entity.annualAmount.value
            it[notes]         = entity.notes
            it[isActive]      = entity.isActive
            it[createdBy]     = entity.createdBy
            it[createdAt]     = entity.createdAt
            it[updatedAt]     = entity.updatedAt
        } get BudgetItemsTable.id
    }

    override fun update(entity: BudgetItem) {
        transaction {
            BudgetItemsTable.update({ BudgetItemsTable.id eq entity.id }) {
                it[costCenterId]  = entity.costCenterId
                it[categoryId]    = entity.categoryId
                it[year]          = entity.year
                it[monthlyAmount] = entity.monthlyAmount.value
                it[annualAmount]  = entity.annualAmount.value
                it[notes]         = entity.notes
                it[isActive]      = entity.isActive
                it[updatedAt]     = LocalDateTime.now()
            }
        }
    }

    // RN-25: saldo disponível = dotação anual − realizado
    fun getAvailableBalance(costCenterId: Int, categoryId: Int, year: Int): BudgetBalance? {
        val item = findByProjectCategory(costCenterId, categoryId, year) ?: return null
        val realized = sumRealized(costCenterId, categoryId, year)
        val available = item.annualAmount - realized
        val pct = if (item.annualAmount.isZero()) 0.0
                  else realized.value.toDouble() / item.annualAmount.value.toDouble() * 100.0
        return BudgetBalance(
            annualAmount    = item.annualAmount,
            realized        = realized,
            available       = available,
            utilizationPct  = pct
        )
    }

    private fun findByProjectCategory(costCenterId: Int, categoryId: Int, year: Int): BudgetItem? = transaction {
        BudgetItemsTable.selectAll()
            .where {
                (BudgetItemsTable.costCenterId eq costCenterId) and
                (BudgetItemsTable.categoryId eq categoryId) and
                (BudgetItemsTable.year eq year) and
                (BudgetItemsTable.isActive eq true)
            }
            .map { rowToItem(it) }
            .firstOrNull()
    }

    // Balancete: realizado no mês específico (para filtro mensal)
    fun sumRealizedMonth(costCenterId: Int, categoryId: Int, year: Int, month: Int): Money = transaction {
        val from = java.time.LocalDate.of(year, month, 1).atStartOfDay()
        val to   = java.time.LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay()
        val sumExpr = FinancialTransactionsTable.amount.sum()
        FinancialTransactionsTable
            .select(sumExpr)
            .where {
                (FinancialTransactionsTable.costCenterId eq costCenterId) and
                (FinancialTransactionsTable.categoryId eq categoryId) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.paymentDate greaterEq from) and
                (FinancialTransactionsTable.paymentDate less to)
            }
            .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
    }

    // RN-24: soma dos lançamentos PAID vinculados ao projeto × categoria no ano
    fun sumRealized(costCenterId: Int, categoryId: Int, year: Int): Money = transaction {
        val sumExpr = FinancialTransactionsTable.amount.sum()
        FinancialTransactionsTable
            .select(sumExpr)
            .where {
                (FinancialTransactionsTable.costCenterId eq costCenterId) and
                (FinancialTransactionsTable.categoryId eq categoryId) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.paymentDate.year() eq year)
            }
            .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
    }

    private fun rowToItem(row: ResultRow) = BudgetItem(
        id            = row[BudgetItemsTable.id],
        costCenterId  = row[BudgetItemsTable.costCenterId],
        categoryId    = row[BudgetItemsTable.categoryId],
        year          = row[BudgetItemsTable.year],
        monthlyAmount = row[BudgetItemsTable.monthlyAmount].toMoney(),
        annualAmount  = row[BudgetItemsTable.annualAmount].toMoney(),
        notes         = row[BudgetItemsTable.notes],
        isActive      = row[BudgetItemsTable.isActive],
        createdBy     = row[BudgetItemsTable.createdBy],
        createdAt     = row[BudgetItemsTable.createdAt],
        updatedAt     = row[BudgetItemsTable.updatedAt]
    )
}
