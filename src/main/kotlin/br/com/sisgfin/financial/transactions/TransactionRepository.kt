package br.com.sisgfin.financial.transactions

import br.com.sisgfin.core.domain.MutableEntityRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.toMoney
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class TransactionRepository : MutableEntityRepository<Transaction> {

    override fun findAll(): List<Transaction> = transaction {
        baseActiveQuery()
            .orderBy(FinancialTransactionsTable.dueDate to SortOrder.DESC)
            .map { rowToTransaction(it) }
    }

    override fun findById(id: Int): Transaction? = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where { FinancialTransactionsTable.id eq id }
            .map { rowToTransaction(it) }
            .singleOrNull()
    }

    fun findPendingActive(): List<Transaction> = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.isActive eq true) and
                    (FinancialTransactionsTable.status eq TransactionStatus.PENDING.name)
            }
            .map { rowToTransaction(it) }
    }

    fun search(query: String): List<Transaction> = transaction {
        val pattern = "%${query.trim()}%"
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.isActive eq true) and
                    (FinancialTransactionsTable.description like pattern)
            }
            .orderBy(FinancialTransactionsTable.dueDate to SortOrder.DESC)
            .map { rowToTransaction(it) }
    }

    fun filterByStatus(status: TransactionStatus): List<Transaction> = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.isActive eq true) and
                    (FinancialTransactionsTable.status eq status.name)
            }
            .orderBy(FinancialTransactionsTable.dueDate to SortOrder.DESC)
            .map { rowToTransaction(it) }
    }

    fun filterByType(type: TransactionType): List<Transaction> = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.isActive eq true) and
                    (FinancialTransactionsTable.type eq type.name)
            }
            .orderBy(FinancialTransactionsTable.dueDate to SortOrder.DESC)
            .map { rowToTransaction(it) }
    }

    fun filterDueToday(today: LocalDate = LocalDate.now()): List<Transaction> = transaction {
        val start = today.atStartOfDay()
        val end = today.plusDays(1).atStartOfDay()
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.isActive eq true) and
                    (FinancialTransactionsTable.dueDate greaterEq start) and
                    (FinancialTransactionsTable.dueDate less end) and
                    (FinancialTransactionsTable.status inList listOf(
                        TransactionStatus.PENDING.name,
                        TransactionStatus.OVERDUE.name,
                        TransactionStatus.SCHEDULED.name,
                        TransactionStatus.PARTIAL.name
                    ))
            }
            .orderBy(FinancialTransactionsTable.dueDate to SortOrder.ASC)
            .map { rowToTransaction(it) }
    }

    fun filterOverdue(): List<Transaction> = filterByStatus(TransactionStatus.OVERDUE)

    fun filterPaid(): List<Transaction> = filterByStatus(TransactionStatus.PAID)

    fun findReceivables(): List<Transaction> = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.type eq TransactionType.INCOME.name) and
                (FinancialTransactionsTable.status inList listOf(
                    TransactionStatus.PENDING.name,
                    TransactionStatus.OVERDUE.name,
                    TransactionStatus.PARTIAL.name
                ))
            }
            .orderBy(FinancialTransactionsTable.dueDate to SortOrder.ASC)
            .map { rowToTransaction(it) }
    }

    fun filterActionRequired(): List<Transaction> = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.status inList listOf(
                    TransactionStatus.OVERDUE.name,
                    TransactionStatus.PENDING.name,
                    TransactionStatus.PARTIAL.name
                ))
            }
            .orderBy(FinancialTransactionsTable.dueDate to SortOrder.ASC)
            .map { rowToTransaction(it) }
    }

    fun existsByFitId(accountId: Int, fitId: String): Boolean = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.ofxFitId  eq fitId)
            }
            .limit(1)
            .count() > 0
    }

    fun filterByDuePeriod(from: LocalDate, to: LocalDate): List<Transaction> = transaction {
        val start = from.atStartOfDay()
        val end = to.plusDays(1).atStartOfDay()
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.isActive eq true) and
                    (FinancialTransactionsTable.dueDate greaterEq start) and
                    (FinancialTransactionsTable.dueDate less end)
            }
            .orderBy(FinancialTransactionsTable.dueDate to SortOrder.ASC)
            .map { rowToTransaction(it) }
    }

    override fun insert(entity: Transaction): Int = transaction {
        FinancialTransactionsTable.insert {
            it[FinancialTransactionsTable.type] = entity.type.name
            it[FinancialTransactionsTable.status] = entity.status.name
            it[description] = entity.description
            it[amount] = entity.amount.value
            it[issueDate] = entity.issueDate
            it[dueDate] = entity.dueDate
            it[paymentDate] = entity.paymentDate
            it[paidAmount] = entity.paidAmount?.value
            it[accountId] = entity.accountId
            it[supplierId] = entity.supplierId
            it[costCenterId] = entity.costCenterId
            it[notes] = entity.notes
            it[documentType] = entity.documentType
            it[documentNumber] = entity.documentNumber
            it[installmentCurrent] = entity.installmentCurrent
            it[installmentTotal] = entity.installmentTotal
            it[categoryId] = entity.categoryId
            it[createdBy] = entity.createdBy
            it[createdAt] = entity.createdAt
            it[updatedAt] = entity.updatedAt
            it[isActive] = entity.isActive
            it[parentTransactionId] = entity.parentTransactionId
            it[ledgerEntryId] = entity.ledgerEntryId
            it[FinancialTransactionsTable.employeeId]           = entity.employeeId
            it[FinancialTransactionsTable.ofxFitId]             = entity.ofxFitId
            it[FinancialTransactionsTable.reconciledWithFitId]  = entity.reconciledWithFitId
            it[FinancialTransactionsTable.recurrenceTemplateId] = entity.recurrenceTemplateId
            it[FinancialTransactionsTable.contractId]           = entity.contractId
        } get FinancialTransactionsTable.id
    }

    override fun update(entity: Transaction) {
        transaction {
            FinancialTransactionsTable.update({ FinancialTransactionsTable.id eq entity.id }) {
                it[FinancialTransactionsTable.type] = entity.type.name
                it[FinancialTransactionsTable.status] = entity.status.name
                it[description] = entity.description
                it[amount] = entity.amount.value
                it[issueDate] = entity.issueDate
                it[dueDate] = entity.dueDate
                it[paymentDate] = entity.paymentDate
                it[paidAmount] = entity.paidAmount?.value
                it[accountId] = entity.accountId
                it[supplierId] = entity.supplierId
                it[costCenterId] = entity.costCenterId
                it[notes] = entity.notes
                it[documentType] = entity.documentType
                it[documentNumber] = entity.documentNumber
                it[installmentCurrent] = entity.installmentCurrent
                it[installmentTotal] = entity.installmentTotal
                it[categoryId] = entity.categoryId
                it[updatedAt] = LocalDateTime.now()
                it[isActive] = entity.isActive
                it[parentTransactionId] = entity.parentTransactionId
                it[ledgerEntryId] = entity.ledgerEntryId
                it[FinancialTransactionsTable.reconciledWithFitId]  = entity.reconciledWithFitId
                it[FinancialTransactionsTable.recurrenceTemplateId] = entity.recurrenceTemplateId
                it[FinancialTransactionsTable.contractId]           = entity.contractId
                // employeeId não é atualizado via update geral — é definido apenas na criação
            }
        }
    }

    fun findNextPendingForEmployee(employeeId: Int): java.time.LocalDate? = transaction {
        val today = java.time.LocalDate.now().atStartOfDay()
        val activeStatuses = listOf(TransactionStatus.PENDING.name, TransactionStatus.SCHEDULED.name)
        FinancialTransactionsTable.selectAll()
            .where {
                (FinancialTransactionsTable.employeeId eq employeeId) and
                (FinancialTransactionsTable.status inList activeStatuses) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.dueDate greaterEq today)
            }
            .orderBy(FinancialTransactionsTable.dueDate to SortOrder.ASC)
            .limit(1)
            .map { it[FinancialTransactionsTable.dueDate].toLocalDate() }
            .firstOrNull()
    }

    fun existsPaymentForEmployee(employeeId: Int, dueDate: java.time.LocalDate): Boolean = transaction {
        val dayStart = dueDate.atStartOfDay()
        val dayEnd   = dueDate.plusDays(1).atStartOfDay()
        val activeStatuses = listOf(
            TransactionStatus.PENDING.name,
            TransactionStatus.PAID.name,
            TransactionStatus.PARTIAL.name,
            TransactionStatus.SCHEDULED.name
        )
        FinancialTransactionsTable.selectAll().where {
            (FinancialTransactionsTable.employeeId eq employeeId) and
            (FinancialTransactionsTable.dueDate greaterEq dayStart) and
            (FinancialTransactionsTable.dueDate less dayEnd) and
            (FinancialTransactionsTable.isActive eq true) and
            (FinancialTransactionsTable.status inList activeStatuses)
        }.count() > 0
    }

    fun findPendingByAmountAndDateRange(
        accountId: Int,
        amount: Money,
        from: LocalDate,
        to: LocalDate
    ): List<Transaction> = transaction {
        val fromDt = from.atStartOfDay()
        val toDt   = to.plusDays(1).atStartOfDay()
        val openStatuses = listOf(TransactionStatus.PENDING.name, TransactionStatus.OVERDUE.name)
        FinancialTransactionsTable.selectAll()
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.status inList openStatuses) and
                (FinancialTransactionsTable.amount eq amount.value) and
                (FinancialTransactionsTable.dueDate greaterEq fromDt) and
                (FinancialTransactionsTable.dueDate less toDt)
            }
            .map { rowToTransaction(it) }
    }

    fun existsByCostCenterId(costCenterId: Int): Boolean = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.costCenterId eq costCenterId) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .limit(1).count() > 0
    }

    // RN-10: verifica se há lançamentos ativos vinculados à categoria
    fun existsByCategoryId(categoryId: Int): Boolean = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.categoryId eq categoryId) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .limit(1).count() > 0
    }

    // RN-05: verifica se há lançamentos ativos vinculados à conta
    fun existsByAccountId(accountId: Int): Boolean = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .limit(1).count() > 0
    }

    // RN-04: soma de lançamentos PAID por conta e tipo
    fun sumPaid(accountId: Int, type: TransactionType): Money = transaction {
        val sumExpr = FinancialTransactionsTable.amount.sum()
        FinancialTransactionsTable
            .select(sumExpr)
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.type eq type.name) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .firstOrNull()
            ?.get(sumExpr)
            ?.toMoney() ?: Money.ZERO
    }

    // RN-21: destino de uma transferência (filho com type=TRANSFER)
    fun findTransferDestination(sourceId: Int): Transaction? = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.parentTransactionId eq sourceId) and
                (FinancialTransactionsTable.type eq TransactionType.TRANSFER.name)
            }
            .map { rowToTransaction(it) }
            .firstOrNull()
    }

    // RN-14/23: verifica se já existe estorno ativo para um lançamento
    fun hasReversal(originalId: Int): Boolean = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.parentTransactionId eq originalId) and
                (FinancialTransactionsTable.type eq TransactionType.REVERSAL.name) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .limit(1)
            .count() > 0
    }

    // RN-04 (extensão): transferências que ENTRAM na conta (destino, tem parentId)
    fun sumPaidTransferIn(accountId: Int): Money = transaction {
        val sumExpr = FinancialTransactionsTable.amount.sum()
        FinancialTransactionsTable
            .select(sumExpr)
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.type eq TransactionType.TRANSFER.name) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.parentTransactionId.isNotNull())
            }
            .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
    }

    // RN-04 (extensão): transferências que SAEM da conta (origem, não tem parentId de transferência)
    fun sumPaidTransferOut(accountId: Int): Money = transaction {
        val sumExpr = FinancialTransactionsTable.amount.sum()
        FinancialTransactionsTable
            .select(sumExpr)
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.type eq TransactionType.TRANSFER.name) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.parentTransactionId.isNull())
            }
            .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
    }

    // RN-19: filhos canceláveis (PENDING ou DRAFT) de um lançamento pai
    fun findActiveChildrenOf(parentId: Int): List<Transaction> = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.parentTransactionId eq parentId) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.status inList listOf(
                    TransactionStatus.PENDING.name,
                    TransactionStatus.DRAFT.name
                ))
            }
            .map { rowToTransaction(it) }
    }

    // Livro Diário: todos os PAID de um período, opcionalmente por conta
    fun findAllPaid(
        from: LocalDate? = null,
        to: LocalDate? = null,
        accountId: Int? = null
    ): List<Transaction> = transaction {
        FinancialTransactionsTable.selectAll()
            .where {
                var cond: org.jetbrains.exposed.sql.Op<Boolean> =
                    (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                    (FinancialTransactionsTable.isActive eq true)
                accountId?.let { a -> cond = cond and (FinancialTransactionsTable.accountId eq a) }
                from?.let { f -> cond = cond and (FinancialTransactionsTable.paymentDate greaterEq f.atStartOfDay()) }
                to?.let { t -> cond = cond and (FinancialTransactionsTable.paymentDate less t.plusDays(1).atStartOfDay()) }
                cond
            }
            .orderBy(
                FinancialTransactionsTable.paymentDate to SortOrder.ASC,
                FinancialTransactionsTable.id to SortOrder.ASC
            )
            .map { rowToTransaction(it) }
    }

    // Extrato: lançamentos PAID filtrados por período, tipo, projeto, categoria
    fun findStatementEntries(
        accountId: Int,
        from: LocalDate? = null,
        to: LocalDate? = null,
        type: TransactionType? = null,
        costCenterId: Int? = null,
        categoryId: Int? = null
    ): List<Transaction> = transaction {
        FinancialTransactionsTable.selectAll()
            .where {
                var cond: org.jetbrains.exposed.sql.Op<Boolean> =
                    (FinancialTransactionsTable.accountId eq accountId) and
                    (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                    (FinancialTransactionsTable.isActive eq true)
                from?.let { f -> cond = cond and (FinancialTransactionsTable.paymentDate greaterEq f.atStartOfDay()) }
                to?.let { t -> cond = cond and (FinancialTransactionsTable.paymentDate less t.plusDays(1).atStartOfDay()) }
                type?.let { tp -> cond = cond and (FinancialTransactionsTable.type eq tp.name) }
                costCenterId?.let { pid -> cond = cond and (FinancialTransactionsTable.costCenterId eq pid) }
                categoryId?.let { cid -> cond = cond and (FinancialTransactionsTable.categoryId eq cid) }
                cond
            }
            .orderBy(
                FinancialTransactionsTable.paymentDate to SortOrder.ASC,
                FinancialTransactionsTable.id to SortOrder.ASC
            )
            .map { rowToTransaction(it) }
    }

    // Extrato: saldo antes de uma data (para saldo de abertura do período)
    private fun sumPaidBefore(accountId: Int, type: TransactionType, before: LocalDate): Money = transaction {
        val sumExpr = FinancialTransactionsTable.amount.sum()
        FinancialTransactionsTable.select(sumExpr)
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.type eq type.name) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.paymentDate less before.atStartOfDay())
            }
            .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
    }

    private fun sumPaidTransferInBefore(accountId: Int, before: LocalDate): Money = transaction {
        val sumExpr = FinancialTransactionsTable.amount.sum()
        FinancialTransactionsTable.select(sumExpr)
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.type eq TransactionType.TRANSFER.name) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.parentTransactionId.isNotNull()) and
                (FinancialTransactionsTable.paymentDate less before.atStartOfDay())
            }
            .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
    }

    private fun sumPaidTransferOutBefore(accountId: Int, before: LocalDate): Money = transaction {
        val sumExpr = FinancialTransactionsTable.amount.sum()
        FinancialTransactionsTable.select(sumExpr)
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.type eq TransactionType.TRANSFER.name) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.parentTransactionId.isNull()) and
                (FinancialTransactionsTable.paymentDate less before.atStartOfDay())
            }
            .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
    }

    fun openingBalance(initialBalance: Money, accountId: Int, before: LocalDate): Money {
        val income     = sumPaidBefore(accountId, TransactionType.INCOME, before)
        val expense    = sumPaidBefore(accountId, TransactionType.EXPENSE, before)
        val reversal   = sumPaidBefore(accountId, TransactionType.REVERSAL, before)
        val adjustment = sumPaidBefore(accountId, TransactionType.ADJUSTMENT, before)
        val transferIn  = sumPaidTransferInBefore(accountId, before)
        val transferOut = sumPaidTransferOutBefore(accountId, before)
        return initialBalance + income + reversal + adjustment + transferIn - expense - transferOut
    }

    // Painel de saldos: soma de lançamentos ATIVOS por status e conta
    fun sumByStatus(accountId: Int, status: TransactionStatus): Money = transaction {
        val sumExpr = FinancialTransactionsTable.amount.sum()
        FinancialTransactionsTable
            .select(sumExpr)
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.status eq status.name) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
    }

    // Painel de saldos: data do último lançamento PAID da conta
    fun lastPaymentDate(accountId: Int): java.time.LocalDateTime? = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .orderBy(FinancialTransactionsTable.paymentDate to SortOrder.DESC)
            .firstOrNull()
            ?.get(FinancialTransactionsTable.paymentDate)
    }

    // Painel de saldos: contagem de lançamentos pendentes/vencidos
    fun countByStatus(accountId: Int, status: TransactionStatus): Int = transaction {
        FinancialTransactionsTable
            .selectAll()
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.status eq status.name) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .count().toInt()
    }

    fun findPendingInPeriodWithoutReconciliation(
        accountId: Int,
        from: LocalDate,
        to: LocalDate
    ): List<Transaction> = transaction {
        val fromDt = from.atStartOfDay()
        val toDt   = to.plusDays(1).atStartOfDay()
        val openStatuses = listOf(TransactionStatus.PENDING.name, TransactionStatus.OVERDUE.name)
        FinancialTransactionsTable.selectAll()
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.status inList openStatuses) and
                (FinancialTransactionsTable.ofxFitId.isNull()) and
                (FinancialTransactionsTable.reconciledWithFitId.isNull()) and
                (FinancialTransactionsTable.dueDate greaterEq fromDt) and
                (FinancialTransactionsTable.dueDate less toDt)
            }
            .map { rowToTransaction(it) }
    }

    // Fluxo de Caixa: todos os não-pagos (OVERDUE sempre + PENDING/PARTIAL/SCHEDULED até windowEnd)
    fun findUnpaid(windowEnd: java.time.LocalDate, accountId: Int? = null): List<Transaction> = transaction {
        val windowEndDt = windowEnd.plusDays(1).atStartOfDay()
        val pendingStatuses = listOf(
            TransactionStatus.PENDING.name,
            TransactionStatus.PARTIAL.name,
            TransactionStatus.SCHEDULED.name
        )
        FinancialTransactionsTable.selectAll()
            .where {
                var cond = (FinancialTransactionsTable.isActive eq true) and (
                    (FinancialTransactionsTable.status eq TransactionStatus.OVERDUE.name) or
                    ((FinancialTransactionsTable.status inList pendingStatuses) and
                     (FinancialTransactionsTable.dueDate less windowEndDt))
                )
                accountId?.let { aid -> cond = cond and (FinancialTransactionsTable.accountId eq aid) }
                cond
            }
            .orderBy(FinancialTransactionsTable.dueDate to SortOrder.ASC)
            .map { rowToTransaction(it) }
    }

    fun deactivate(id: Int) {
        transaction {
            FinancialTransactionsTable.update({ FinancialTransactionsTable.id eq id }) {
                it[FinancialTransactionsTable.isActive] = false
                it[FinancialTransactionsTable.status] = TransactionStatus.CANCELED.name
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    private fun baseActiveQuery() =
        FinancialTransactionsTable
            .selectAll()
            .where { FinancialTransactionsTable.isActive eq true }

    private fun rowToTransaction(row: ResultRow) = Transaction(
        id = row[FinancialTransactionsTable.id],
        type = TransactionType.valueOf(row[FinancialTransactionsTable.type]),
        status = TransactionStatus.valueOf(row[FinancialTransactionsTable.status]),
        description = row[FinancialTransactionsTable.description],
        amount = row[FinancialTransactionsTable.amount].toMoney(),
        issueDate = row[FinancialTransactionsTable.issueDate],
        dueDate = row[FinancialTransactionsTable.dueDate],
        paymentDate = row[FinancialTransactionsTable.paymentDate],
        paidAmount = row[FinancialTransactionsTable.paidAmount]?.toMoney(),
        accountId = row[FinancialTransactionsTable.accountId],
        supplierId = row[FinancialTransactionsTable.supplierId],
        costCenterId = row[FinancialTransactionsTable.costCenterId],
        notes = row[FinancialTransactionsTable.notes],
        documentType = row[FinancialTransactionsTable.documentType],
        documentNumber = row[FinancialTransactionsTable.documentNumber],
        installmentCurrent = row[FinancialTransactionsTable.installmentCurrent],
        installmentTotal = row[FinancialTransactionsTable.installmentTotal],
        categoryId = row[FinancialTransactionsTable.categoryId],
        createdBy = row[FinancialTransactionsTable.createdBy],
        createdAt = row[FinancialTransactionsTable.createdAt],
        updatedAt = row[FinancialTransactionsTable.updatedAt],
        isActive = row[FinancialTransactionsTable.isActive],
        parentTransactionId = row[FinancialTransactionsTable.parentTransactionId],
        ledgerEntryId = row[FinancialTransactionsTable.ledgerEntryId],
        employeeId            = row[FinancialTransactionsTable.employeeId],
        ofxFitId              = row[FinancialTransactionsTable.ofxFitId],
        reconciledWithFitId   = row[FinancialTransactionsTable.reconciledWithFitId],
        recurrenceTemplateId  = row[FinancialTransactionsTable.recurrenceTemplateId],
        contractId            = row[FinancialTransactionsTable.contractId]
    )

    // Fase 7-B: soma paidAmount das transações PAID vinculadas ao contrato
    fun sumConsumedByContract(contractId: Int): br.com.sisgfin.financial.money.Money = transaction {
        val sum = FinancialTransactionsTable
            .select(FinancialTransactionsTable.paidAmount.sum())
            .where {
                (FinancialTransactionsTable.contractId eq contractId) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .singleOrNull()
            ?.get(FinancialTransactionsTable.paidAmount.sum())
        br.com.sisgfin.financial.money.Money(sum ?: java.math.BigDecimal.ZERO)
    }

    // Fase 7-B: verifica se existem lançamentos pendentes vinculados ao contrato
    fun existsPendingByContract(contractId: Int): Boolean = transaction {
        val pending = listOf(
            TransactionStatus.PENDING.name, TransactionStatus.DRAFT.name,
            TransactionStatus.SCHEDULED.name, TransactionStatus.PARTIAL.name
        )
        FinancialTransactionsTable.selectAll()
            .where {
                (FinancialTransactionsTable.contractId eq contractId) and
                (FinancialTransactionsTable.status inList pending) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .limit(1).count() > 0
    }

    // Fase 7-B: últimas 10 transações de um contrato (para exibição no painel)
    fun findByContract(contractId: Int): List<Transaction> = transaction {
        FinancialTransactionsTable.selectAll()
            .where {
                (FinancialTransactionsTable.contractId eq contractId) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .orderBy(FinancialTransactionsTable.dueDate to SortOrder.DESC)
            .limit(10)
            .map { rowToTransaction(it) }
    }

    // Fase 8-C: retorna lançamentos canceláveis de um funcionário dentro do mês de referência e seguinte
    fun findPendingPayrollForMonth(employeeId: Int, month: YearMonth): List<Transaction> = transaction {
        val from = month.atDay(1).atStartOfDay()
        val to = month.plusMonths(2).atDay(1).atStartOfDay()
        val cancelable = listOf(
            TransactionStatus.PENDING.name,
            TransactionStatus.DRAFT.name,
            TransactionStatus.SCHEDULED.name
        )
        FinancialTransactionsTable.selectAll()
            .where {
                (FinancialTransactionsTable.employeeId eq employeeId) and
                (FinancialTransactionsTable.status inList cancelable) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.dueDate greaterEq from) and
                (FinancialTransactionsTable.dueDate less to)
            }
            .map { rowToTransaction(it) }
    }

    // Fase 7-A: verifica se já existe lançamento gerado para este template neste dia
    fun existsGeneratedFor(templateId: Int, dueDate: java.time.LocalDate): Boolean = transaction {
        val dayStart = dueDate.atStartOfDay()
        val dayEnd   = dueDate.plusDays(1).atStartOfDay()
        FinancialTransactionsTable.selectAll()
            .where {
                (FinancialTransactionsTable.recurrenceTemplateId eq templateId) and
                (FinancialTransactionsTable.dueDate greaterEq dayStart) and
                (FinancialTransactionsTable.dueDate less dayEnd) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .limit(1)
            .count() > 0
    }

    // Fase 7-A: lista lançamentos gerados por um template (para exibir no painel)
    fun findByRecurrenceTemplate(templateId: Int): List<Transaction> = transaction {
        FinancialTransactionsTable.selectAll()
            .where {
                (FinancialTransactionsTable.recurrenceTemplateId eq templateId) and
                (FinancialTransactionsTable.isActive eq true)
            }
            .orderBy(FinancialTransactionsTable.dueDate to SortOrder.DESC)
            .map { rowToTransaction(it) }
    }

    // Fase 7-A: cancela todos os PENDING/DRAFT futuros de um template (cascata ao pausar)
    fun cancelFutureByRecurrenceTemplate(templateId: Int, from: java.time.LocalDate) {
        transaction {
            val cancelableStatuses = listOf(
                TransactionStatus.PENDING.name,
                TransactionStatus.DRAFT.name,
                TransactionStatus.SCHEDULED.name
            )
            FinancialTransactionsTable.update({
                (FinancialTransactionsTable.recurrenceTemplateId eq templateId) and
                (FinancialTransactionsTable.dueDate greaterEq from.atStartOfDay()) and
                (FinancialTransactionsTable.status inList cancelableStatuses) and
                (FinancialTransactionsTable.isActive eq true)
            }) {
                it[FinancialTransactionsTable.isActive] = false
                it[FinancialTransactionsTable.status]   = TransactionStatus.CANCELED.name
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
}
