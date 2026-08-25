package br.com.sisgfin.budget

import br.com.sisgfin.core.domain.MutableEntityRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.toMoney
import br.com.sisgfin.financial.payments.TransactionPaymentsTable
import br.com.sisgfin.financial.transactions.FinancialTransactionsTable
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.year
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
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

    // Balancete: realizado no mês específico (para filtro mensal).
    // D-PRINCIPAL (B): principal_amount é face amortizado — realizado = Σ(principal_amount).
    // Juros/multa não consomem dotação. Desconto já está embutido no face (sem subtração extra).
    // C-11: soma EXPENSE; subtrai estornos de EXPENSE via baixas do título original.
    fun sumRealizedMonth(costCenterId: Int, categoryId: Int, year: Int, month: Int): Money = transaction {
        val from = java.time.LocalDate.of(year, month, 1)
        val to   = from.plusMonths(1)

        val expenses = TransactionPaymentsTable
            .join(FinancialTransactionsTable, JoinType.INNER,
                onColumn = TransactionPaymentsTable.transactionId,
                otherColumn = FinancialTransactionsTable.id)
            .select(TransactionPaymentsTable.principalAmount)
            .where {
                (FinancialTransactionsTable.costCenterId eq costCenterId) and
                (FinancialTransactionsTable.categoryId   eq categoryId) and
                (FinancialTransactionsTable.isActive     eq true) and
                (FinancialTransactionsTable.type         eq TransactionType.EXPENSE.name) and
                TransactionPaymentsTable.reversedById.isNull() and
                (TransactionPaymentsTable.paymentDate greaterEq from) and
                (TransactionPaymentsTable.paymentDate less to)
            }
            .fold(BigDecimal.ZERO) { acc, row ->
                acc + row[TransactionPaymentsTable.principalAmount]
            }.toMoney()

        val reversals = TransactionPaymentsTable
            .join(FinancialTransactionsTable, JoinType.INNER,
                additionalConstraint = {
                    TransactionPaymentsTable.transactionId eq FinancialTransactionsTable.parentTransactionId
                })
            .select(TransactionPaymentsTable.principalAmount)
            .where {
                (FinancialTransactionsTable.costCenterId eq costCenterId) and
                (FinancialTransactionsTable.categoryId   eq categoryId) and
                (FinancialTransactionsTable.isActive     eq true) and
                (FinancialTransactionsTable.type         eq TransactionType.REVERSAL.name) and
                (FinancialTransactionsTable.status       eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.reversedType eq TransactionType.EXPENSE.name) and
                (FinancialTransactionsTable.paymentDate greaterEq from.atStartOfDay()) and
                (FinancialTransactionsTable.paymentDate less to.atStartOfDay()) and
                TransactionPaymentsTable.reversedById.isNull()
            }
            .fold(BigDecimal.ZERO) { acc, row ->
                acc + row[TransactionPaymentsTable.principalAmount]
            }.toMoney()

        expenses - reversals
    }

    // RN-24: realizado no ano por CC × categoria.
    // D-PRINCIPAL (B): principal_amount é face amortizado — realizado = Σ(principal_amount).
    // Juros/multa não consomem dotação. Desconto já está embutido no face (sem subtração extra).
    // C-11: soma EXPENSE; subtrai estornos de EXPENSE via baixas do título original.
    fun sumRealized(costCenterId: Int, categoryId: Int, year: Int): Money = transaction {
        val expenses = TransactionPaymentsTable
            .join(FinancialTransactionsTable, JoinType.INNER,
                onColumn = TransactionPaymentsTable.transactionId,
                otherColumn = FinancialTransactionsTable.id)
            .select(TransactionPaymentsTable.principalAmount)
            .where {
                (FinancialTransactionsTable.costCenterId eq costCenterId) and
                (FinancialTransactionsTable.categoryId   eq categoryId) and
                (FinancialTransactionsTable.isActive     eq true) and
                (FinancialTransactionsTable.type         eq TransactionType.EXPENSE.name) and
                TransactionPaymentsTable.reversedById.isNull() and
                (TransactionPaymentsTable.paymentDate.year() eq year)
            }
            .fold(BigDecimal.ZERO) { acc, row ->
                acc + row[TransactionPaymentsTable.principalAmount]
            }.toMoney()

        val reversals = TransactionPaymentsTable
            .join(FinancialTransactionsTable, JoinType.INNER,
                additionalConstraint = {
                    TransactionPaymentsTable.transactionId eq FinancialTransactionsTable.parentTransactionId
                })
            .select(TransactionPaymentsTable.principalAmount)
            .where {
                (FinancialTransactionsTable.costCenterId eq costCenterId) and
                (FinancialTransactionsTable.categoryId   eq categoryId) and
                (FinancialTransactionsTable.isActive     eq true) and
                (FinancialTransactionsTable.type         eq TransactionType.REVERSAL.name) and
                (FinancialTransactionsTable.status       eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.reversedType eq TransactionType.EXPENSE.name) and
                (FinancialTransactionsTable.paymentDate.year() eq year) and
                TransactionPaymentsTable.reversedById.isNull()
            }
            .fold(BigDecimal.ZERO) { acc, row ->
                acc + row[TransactionPaymentsTable.principalAmount]
            }.toMoney()

        expenses - reversals
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
