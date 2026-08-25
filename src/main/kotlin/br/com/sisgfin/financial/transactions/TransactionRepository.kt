package br.com.sisgfin.financial.transactions

import br.com.sisgfin.core.domain.MutableEntityRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.toMoney
import br.com.sisgfin.financial.payments.TransactionPayment
import br.com.sisgfin.financial.payments.TransactionPaymentRepository
import br.com.sisgfin.financial.payments.TransactionPaymentsTable
import br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class PaymentReversalResult(
    val transactionId: Int,
    val originalCashEffective: Money,
    val correctionId: Int,
    val previousStatus: TransactionStatus,
    val newStatus: TransactionStatus
)

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

    // F0: ponto de entrada único — todos os findX de leitura expressáveis como TransactionQuery
    fun find(query: TransactionQuery): List<Transaction> = transaction {
        val stmt = FinancialTransactionsTable.selectAll()

        if (query.onlyActive)
            stmt.andWhere { FinancialTransactionsTable.isActive eq true }
        if (query.types.isNotEmpty())
            stmt.andWhere { FinancialTransactionsTable.type inList query.types.map { it.name } }
        if (query.statuses.isNotEmpty())
            stmt.andWhere { FinancialTransactionsTable.status inList query.statuses.map { it.name } }

        query.from?.let { f ->
            stmt.andWhere {
                when (query.dateAxis) {
                    DateAxis.ISSUE   -> FinancialTransactionsTable.issueDate greaterEq f.atStartOfDay()
                    DateAxis.DUE     -> FinancialTransactionsTable.dueDate greaterEq f.atStartOfDay()
                    DateAxis.PAYMENT -> FinancialTransactionsTable.paymentDate greaterEq f.atStartOfDay()
                }
            }
        }
        query.to?.let { t ->
            stmt.andWhere {
                when (query.dateAxis) {
                    DateAxis.ISSUE   -> FinancialTransactionsTable.issueDate less t.plusDays(1).atStartOfDay()
                    DateAxis.DUE     -> FinancialTransactionsTable.dueDate less t.plusDays(1).atStartOfDay()
                    DateAxis.PAYMENT -> FinancialTransactionsTable.paymentDate less t.plusDays(1).atStartOfDay()
                }
            }
        }

        query.accountId?.let    { stmt.andWhere { FinancialTransactionsTable.accountId    eq it } }
        query.supplierId?.let   { stmt.andWhere { FinancialTransactionsTable.supplierId   eq it } }
        query.employeeId?.let   { stmt.andWhere { FinancialTransactionsTable.employeeId   eq it } }
        query.costCenterId?.let { stmt.andWhere { FinancialTransactionsTable.costCenterId eq it } }
        query.categoryId?.let   { stmt.andWhere { FinancialTransactionsTable.categoryId   eq it } }
        query.projectId?.let    { stmt.andWhere { FinancialTransactionsTable.projectId    eq it } }
        query.contractId?.let   { stmt.andWhere { FinancialTransactionsTable.contractId   eq it } }
        query.search?.trim()?.takeIf { it.isNotBlank() }?.let { s ->
            stmt.andWhere { FinancialTransactionsTable.description like "%$s%" }
        }

        stmt
            .orderBy(
                FinancialTransactionsTable.dueDate to SortOrder.ASC,
                FinancialTransactionsTable.id      to SortOrder.ASC
            )
            .map { rowToTransaction(it) }
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
            it[FinancialTransactionsTable.interestAmount]       = entity.interestAmount?.value
            it[FinancialTransactionsTable.fineAmount]           = entity.fineAmount?.value
            it[FinancialTransactionsTable.projectId]            = entity.projectId
            it[FinancialTransactionsTable.reversedType]         = entity.reversedType?.name
            it[FinancialTransactionsTable.origin]               = entity.origin.name
            it[FinancialTransactionsTable.version]              = 0
        } get FinancialTransactionsTable.id
    }

    fun updateWithPayment(entity: Transaction, payment: TransactionPayment): Boolean = transaction {
        val rows = FinancialTransactionsTable.update({
            (FinancialTransactionsTable.id eq entity.id) and
            (FinancialTransactionsTable.version eq entity.version)
        }) {
            it[FinancialTransactionsTable.type]          = entity.type.name
            it[FinancialTransactionsTable.status]        = entity.status.name
            it[description]                              = entity.description
            it[amount]                                   = entity.amount.value
            it[issueDate]                                = entity.issueDate
            it[dueDate]                                  = entity.dueDate
            it[paymentDate]                              = entity.paymentDate
            it[paidAmount]                               = entity.paidAmount?.value
            it[accountId]                                = entity.accountId
            it[supplierId]                               = entity.supplierId
            it[costCenterId]                             = entity.costCenterId
            it[notes]                                    = entity.notes
            it[documentType]                             = entity.documentType
            it[documentNumber]                           = entity.documentNumber
            it[installmentCurrent]                       = entity.installmentCurrent
            it[installmentTotal]                         = entity.installmentTotal
            it[categoryId]                               = entity.categoryId
            it[updatedAt]                                = LocalDateTime.now()
            it[isActive]                                 = entity.isActive
            it[parentTransactionId]                      = entity.parentTransactionId
            it[ledgerEntryId]                            = entity.ledgerEntryId
            it[FinancialTransactionsTable.reconciledWithFitId]  = entity.reconciledWithFitId
            it[FinancialTransactionsTable.recurrenceTemplateId] = entity.recurrenceTemplateId
            it[FinancialTransactionsTable.contractId]           = entity.contractId
            it[FinancialTransactionsTable.interestAmount]       = entity.interestAmount?.value
            it[FinancialTransactionsTable.fineAmount]           = entity.fineAmount?.value
            it[FinancialTransactionsTable.projectId]            = entity.projectId
            it[FinancialTransactionsTable.employeeId]           = entity.employeeId
            it[FinancialTransactionsTable.version]              = entity.version + 1
            // reversedType não é atualizado — define a direção do estorno no saldo (V29) e é imutável
        }
        if (rows == 0) throw ConcurrentModificationException("Conflito de versão no lançamento #${entity.id}")

        val key = payment.idempotencyKey
        if (key != null) {
            val exists = TransactionPaymentsTable.selectAll()
                .where { TransactionPaymentsTable.idempotencyKey eq key }
                .count() > 0
            if (exists) return@transaction false
        }

        try {
            TransactionPaymentsTable.insert {
                it[TransactionPaymentsTable.transactionId]   = payment.transactionId
                it[TransactionPaymentsTable.paymentDate]     = payment.paymentDate
                it[TransactionPaymentsTable.accountId]       = payment.accountId
                it[TransactionPaymentsTable.principalAmount] = payment.principalAmount.value
                it[TransactionPaymentsTable.interestAmount]  = payment.interestAmount.value
                it[TransactionPaymentsTable.fineAmount]      = payment.fineAmount.value
                it[TransactionPaymentsTable.discountAmount]  = payment.discountAmount.value
                it[TransactionPaymentsTable.reversedById]    = payment.reversedById
                it[TransactionPaymentsTable.idempotencyKey]  = payment.idempotencyKey
                it[TransactionPaymentsTable.notes]           = payment.notes
                it[TransactionPaymentsTable.createdBy]       = payment.createdBy
                it[TransactionPaymentsTable.createdAt]       = payment.createdAt
            }
            true
        } catch (e: Exception) {
            val isUnique = generateSequence(e as Throwable) { it.cause }
                .filterIsInstance<SQLException>()
                .any { it.sqlState == "23505" }
            if (isUnique) false else throw e
        }
    }

    fun insertTransferPair(source: Transaction, destinationTemplate: Transaction): Pair<Int, Int> = transaction {
        val sourceId = insert(source)
        val destination = destinationTemplate.copy(parentTransactionId = sourceId)
        val destinationId = insert(destination)
        sourceId to destinationId
    }

    // M5-A, D5: transferência como evento consumado — pernas nascem PAID com baixas na mesma transação
    fun insertTransferPairWithBaixas(
        source: Transaction,
        destinationTemplate: Transaction,
        amount: Money,
        paymentDate: LocalDateTime,
        userId: Int?
    ): Pair<Int, Int> = transaction {
        val now = LocalDateTime.now()
        val sourceId = insert(source)
        val destination = destinationTemplate.copy(parentTransactionId = sourceId)
        val destinationId = insert(destination)

        TransactionPaymentsTable.insert {
            it[TransactionPaymentsTable.transactionId]   = sourceId
            it[TransactionPaymentsTable.paymentDate]     = paymentDate.toLocalDate()
            it[TransactionPaymentsTable.accountId]       = source.accountId
            it[TransactionPaymentsTable.principalAmount] = amount.value
            it[TransactionPaymentsTable.interestAmount]  = BigDecimal.ZERO
            it[TransactionPaymentsTable.fineAmount]      = BigDecimal.ZERO
            it[TransactionPaymentsTable.discountAmount]  = BigDecimal.ZERO
            it[TransactionPaymentsTable.createdBy]       = userId
            it[TransactionPaymentsTable.createdAt]       = now
        }

        TransactionPaymentsTable.insert {
            it[TransactionPaymentsTable.transactionId]   = destinationId
            it[TransactionPaymentsTable.paymentDate]     = paymentDate.toLocalDate()
            it[TransactionPaymentsTable.accountId]       = destinationTemplate.accountId
            it[TransactionPaymentsTable.principalAmount] = amount.value
            it[TransactionPaymentsTable.interestAmount]  = BigDecimal.ZERO
            it[TransactionPaymentsTable.fineAmount]      = BigDecimal.ZERO
            it[TransactionPaymentsTable.discountAmount]  = BigDecimal.ZERO
            it[TransactionPaymentsTable.createdBy]       = userId
            it[TransactionPaymentsTable.createdAt]       = now
        }

        sourceId to destinationId
    }

    // M5-A, D1: estorno de baixa individual — atômico, padrão C-15
    fun reversePaymentAndUpdateTitle(
        paymentId: Int,
        justification: String,
        userId: Int?,
        now: LocalDateTime = LocalDateTime.now()
    ): PaymentReversalResult = transaction {
        val baixaRow = TransactionPaymentsTable.selectAll()
            .where { TransactionPaymentsTable.id eq paymentId }
            .firstOrNull()
            ?: throw IllegalArgumentException("Baixa não encontrada: #$paymentId")

        if (baixaRow[TransactionPaymentsTable.reversedById] != null) {
            throw IllegalStateException("Baixa #$paymentId já foi estornada.")
        }

        val transactionId = baixaRow[TransactionPaymentsTable.transactionId]
        val title = findById(transactionId)
            ?: throw IllegalArgumentException("Lançamento #$transactionId não encontrado.")

        // Insere marcador de correção com reversed_by_id = paymentId (excluído imediatamente das somas)
        val correctionId = TransactionPaymentsTable.insert {
            it[TransactionPaymentsTable.transactionId]   = transactionId
            it[TransactionPaymentsTable.paymentDate]     = baixaRow[TransactionPaymentsTable.paymentDate]
            it[TransactionPaymentsTable.accountId]       = baixaRow[TransactionPaymentsTable.accountId]
            it[TransactionPaymentsTable.principalAmount] = baixaRow[TransactionPaymentsTable.principalAmount]
            it[TransactionPaymentsTable.interestAmount]  = baixaRow[TransactionPaymentsTable.interestAmount]
            it[TransactionPaymentsTable.fineAmount]      = baixaRow[TransactionPaymentsTable.fineAmount]
            it[TransactionPaymentsTable.discountAmount]  = baixaRow[TransactionPaymentsTable.discountAmount]
            it[TransactionPaymentsTable.reversedById]    = paymentId
            it[TransactionPaymentsTable.notes]           = justification
            it[TransactionPaymentsTable.createdBy]       = userId
            it[TransactionPaymentsTable.createdAt]       = now
        } get TransactionPaymentsTable.id

        // Fecha referência cruzada — original também excluído das somas
        TransactionPaymentsTable.update({ TransactionPaymentsTable.id eq paymentId }) {
            it[TransactionPaymentsTable.reversedById] = correctionId
        }

        // Recalcula status a partir das baixas ativas restantes
        val activeBaixas = TransactionPaymentsTable.selectAll()
            .where {
                (TransactionPaymentsTable.transactionId eq transactionId) and
                TransactionPaymentsTable.reversedById.isNull()
            }
            .toList()

        val newPrincipal = activeBaixas.fold(BigDecimal.ZERO) { acc, r -> acc + r[TransactionPaymentsTable.principalAmount] }
        val newInterest  = activeBaixas.fold(BigDecimal.ZERO) { acc, r -> acc + r[TransactionPaymentsTable.interestAmount] }
        val newFine      = activeBaixas.fold(BigDecimal.ZERO) { acc, r -> acc + r[TransactionPaymentsTable.fineAmount] }
        // D-PRINCIPAL (B): principalAmount é face amortizado (inclui desconto).
        // paid_amount = soma de faces; quitação direta sem somar desconto à parte.
        val newPaidAmount    = newPrincipal
        val principalQuitado = newPrincipal

        val newStatus = when {
            principalQuitado.compareTo(title.amount.value) >= 0 -> TransactionStatus.PAID
            principalQuitado.compareTo(BigDecimal.ZERO) > 0    -> TransactionStatus.PARTIAL
            LocalDate.now().isAfter(title.dueDate.toLocalDate()) -> TransactionStatus.OVERDUE
            else -> TransactionStatus.PENDING
        }

        TransactionStateMachine.assertReversalTransition(title.status, newStatus)

        val updated = title.copy(
            status         = newStatus,
            paidAmount     = if (newPaidAmount.compareTo(BigDecimal.ZERO) == 0) null else newPaidAmount.toMoney(),
            interestAmount = if (newInterest.compareTo(BigDecimal.ZERO) == 0) null else newInterest.toMoney(),
            fineAmount     = if (newFine.compareTo(BigDecimal.ZERO) == 0) null else newFine.toMoney(),
            paymentDate    = if (newStatus == TransactionStatus.PAID) title.paymentDate else null,
            updatedAt      = now
        )
        update(updated)

        val originalCashEffective = (
            baixaRow[TransactionPaymentsTable.principalAmount] +
            baixaRow[TransactionPaymentsTable.interestAmount] +
            baixaRow[TransactionPaymentsTable.fineAmount] -
            baixaRow[TransactionPaymentsTable.discountAmount]
        ).toMoney()

        PaymentReversalResult(
            transactionId         = transactionId,
            originalCashEffective = originalCashEffective,
            correctionId          = correctionId,
            previousStatus        = title.status,
            newStatus             = newStatus
        )
    }

    override fun update(entity: Transaction) {
        transaction {
            val rows = FinancialTransactionsTable.update({
                (FinancialTransactionsTable.id eq entity.id) and
                (FinancialTransactionsTable.version eq entity.version)
            }) {
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
                it[FinancialTransactionsTable.interestAmount]       = entity.interestAmount?.value
                it[FinancialTransactionsTable.fineAmount]           = entity.fineAmount?.value
                it[FinancialTransactionsTable.projectId]            = entity.projectId
                it[FinancialTransactionsTable.employeeId]           = entity.employeeId
                it[FinancialTransactionsTable.version]              = entity.version + 1
                // reversedType não é atualizado — define a direção do estorno no saldo (V29) e é imutável
            }
            if (rows == 0) throw ConcurrentModificationException("Conflito de versão no lançamento #${entity.id}")
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

    // RN-04 (PARTIAL): soma de paidAmount de lançamentos PARTIAL por conta e tipo
    fun sumPartialPaid(accountId: Int, type: TransactionType): Money = transaction {
        val sumExpr = FinancialTransactionsTable.paidAmount.sum()
        FinancialTransactionsTable
            .select(sumExpr)
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.type eq type.name) and
                (FinancialTransactionsTable.status eq TransactionStatus.PARTIAL.name) and
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

    // RN-04 (C1): estornos PAID filtrados pelo tipo do lançamento original (reversed_type)
    fun sumPaidReversalOf(accountId: Int, originalTypes: List<TransactionType>): Money = transaction {
        if (originalTypes.isEmpty()) return@transaction Money.ZERO
        val sumExpr = FinancialTransactionsTable.amount.sum()
        FinancialTransactionsTable
            .select(sumExpr)
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.type eq TransactionType.REVERSAL.name) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.reversedType inList originalTypes.map { it.name })
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
        categoryId: Int? = null,
        projectId: Int? = null
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
                projectId?.let { pjid -> cond = cond and (FinancialTransactionsTable.projectId eq pjid) }
                cond
            }
            .orderBy(
                FinancialTransactionsTable.paymentDate to SortOrder.ASC,
                FinancialTransactionsTable.id to SortOrder.ASC
            )
            .map { rowToTransaction(it) }
    }

    // M4b: uma linha por baixa; a consulta por período usa payment_date da baixa, não do título.
    fun findPaymentEntries(
        accountId: Int? = null,
        from: LocalDate? = null,
        to: LocalDate? = null,
        type: TransactionType? = null,
        costCenterId: Int? = null,
        categoryId: Int? = null,
        projectId: Int? = null
    ): List<br.com.sisgfin.financial.payments.PaymentEntry> = transaction {

        // Query 1: baixas do período com o título associado
        val filteredRows = TransactionPaymentsTable
            .join(FinancialTransactionsTable, JoinType.INNER,
                onColumn = TransactionPaymentsTable.transactionId,
                otherColumn = FinancialTransactionsTable.id)
            .selectAll()
            .where {
                var cond: org.jetbrains.exposed.sql.Op<Boolean> =
                    (FinancialTransactionsTable.isActive eq true) and
                    TransactionPaymentsTable.reversedById.isNull()
                accountId?.let { a -> cond = cond and (TransactionPaymentsTable.accountId eq a) }
                from?.let { f -> cond = cond and (TransactionPaymentsTable.paymentDate greaterEq f) }
                to?.let { t -> cond = cond and (TransactionPaymentsTable.paymentDate less t.plusDays(1)) }
                type?.let { tp -> cond = cond and (FinancialTransactionsTable.type eq tp.name) }
                costCenterId?.let { cc -> cond = cond and (FinancialTransactionsTable.costCenterId eq cc) }
                categoryId?.let { cid -> cond = cond and (FinancialTransactionsTable.categoryId eq cid) }
                projectId?.let { pid -> cond = cond and (FinancialTransactionsTable.projectId eq pid) }
                cond
            }
            .orderBy(
                TransactionPaymentsTable.paymentDate to SortOrder.ASC,
                TransactionPaymentsTable.id to SortOrder.ASC
            )
            .map { row ->
                val tx = rowToTransaction(row)
                val payment = br.com.sisgfin.financial.payments.TransactionPayment(
                    id              = row[TransactionPaymentsTable.id],
                    transactionId   = row[TransactionPaymentsTable.transactionId],
                    paymentDate     = row[TransactionPaymentsTable.paymentDate],
                    accountId       = row[TransactionPaymentsTable.accountId],
                    principalAmount = row[TransactionPaymentsTable.principalAmount].toMoney(),
                    interestAmount  = row[TransactionPaymentsTable.interestAmount].toMoney(),
                    fineAmount      = row[TransactionPaymentsTable.fineAmount].toMoney(),
                    discountAmount  = row[TransactionPaymentsTable.discountAmount].toMoney(),
                    reversedById    = row[TransactionPaymentsTable.reversedById],
                    idempotencyKey  = row[TransactionPaymentsTable.idempotencyKey],
                    notes           = row[TransactionPaymentsTable.notes],
                    createdBy       = row[TransactionPaymentsTable.createdBy],
                    createdAt       = row[TransactionPaymentsTable.createdAt]
                )
                payment to tx
            }

        if (filteredRows.isEmpty()) return@transaction emptyList()

        // Query 2: todos os IDs de baixas ativas por título (para ordinal e total globais)
        val txIds = filteredRows.map { (p, _) -> p.transactionId }.distinct()
        val allIdsByTx: Map<Int, List<Int>> = TransactionPaymentsTable
            .select(TransactionPaymentsTable.id, TransactionPaymentsTable.transactionId)
            .where {
                (TransactionPaymentsTable.transactionId inList txIds) and
                TransactionPaymentsTable.reversedById.isNull()
            }
            .orderBy(
                TransactionPaymentsTable.paymentDate to SortOrder.ASC,
                TransactionPaymentsTable.id to SortOrder.ASC
            )
            .groupBy({ it[TransactionPaymentsTable.transactionId] }, { it[TransactionPaymentsTable.id] })

        filteredRows.map { (payment, tx) ->
            val ids     = allIdsByTx[payment.transactionId] ?: listOf(payment.id)
            val ordinal = ids.indexOf(payment.id) + 1
            val total   = if (tx.status == TransactionStatus.PAID) ids.size else null
            br.com.sisgfin.financial.payments.PaymentEntry(payment, tx, ordinal, total)
        }
    }

    // Realizado orçamentário por projeto = Σ(principal_amount) das baixas ativas de EXPENSE.
    // Fonte: transaction_payments (alinhado com sumRealized/sumRealizedMonth de BudgetItemRepository).
    // Estorno de baixa individual (reversed_by_id) e de título (REVERSAL) são excluídos pelo
    // filtro reversed_by_id IS NULL — nenhum netting manual necessário.
    fun sumRealizedByProject(projectId: Int): br.com.sisgfin.financial.money.Money = transaction {
        val sumExpr = TransactionPaymentsTable.principalAmount.sum()
        TransactionPaymentsTable
            .join(FinancialTransactionsTable, JoinType.INNER,
                onColumn    = TransactionPaymentsTable.transactionId,
                otherColumn = FinancialTransactionsTable.id)
            .select(sumExpr)
            .where {
                (FinancialTransactionsTable.projectId eq projectId) and
                (FinancialTransactionsTable.type     eq TransactionType.EXPENSE.name) and
                (FinancialTransactionsTable.isActive eq true) and
                TransactionPaymentsTable.reversedById.isNull()
            }
            .firstOrNull()?.get(sumExpr)?.toMoney()
            ?: br.com.sisgfin.financial.money.Money.ZERO
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

    // RN-04 (PARTIAL): saldo de abertura considera paidAmount de lançamentos PARTIAL antes do período
    private fun sumPartialPaidBefore(accountId: Int, type: TransactionType, before: LocalDate): Money = transaction {
        val sumExpr = FinancialTransactionsTable.paidAmount.sum()
        FinancialTransactionsTable.select(sumExpr)
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.type eq type.name) and
                (FinancialTransactionsTable.status eq TransactionStatus.PARTIAL.name) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.paymentDate less before.atStartOfDay())
            }
            .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
    }

    private fun sumPaidReversalOfBefore(
        accountId: Int,
        originalTypes: List<TransactionType>,
        before: LocalDate
    ): Money = transaction {
        if (originalTypes.isEmpty()) return@transaction Money.ZERO
        val sumExpr = FinancialTransactionsTable.amount.sum()
        FinancialTransactionsTable.select(sumExpr)
            .where {
                (FinancialTransactionsTable.accountId eq accountId) and
                (FinancialTransactionsTable.type eq TransactionType.REVERSAL.name) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.reversedType inList originalTypes.map { it.name }) and
                (FinancialTransactionsTable.paymentDate less before.atStartOfDay())
            }
            .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
    }

    // M4 Bloco 2: data da baixa, não do título; PARTIAL e PAID unificados via cashEffective
    fun openingBalance(
        initialBalance: Money,
        accountId: Int,
        before: LocalDate,
        paymentRepository: TransactionPaymentRepository
    ): Money {
        val income         = paymentRepository.sumCashEffectiveByAccountAndTypeBefore(accountId, TransactionType.INCOME, before)
        val expense        = paymentRepository.sumCashEffectiveByAccountAndTypeBefore(accountId, TransactionType.EXPENSE, before)
        val adjustment     = paymentRepository.sumCashEffectiveByAccountAndTypeBefore(accountId, TransactionType.ADJUSTMENT, before)
        val transferIn     = paymentRepository.sumCashEffectiveTransferInBefore(accountId, before)
        val transferOut    = paymentRepository.sumCashEffectiveTransferOutBefore(accountId, before)
        val reversalCredit = paymentRepository.sumCashEffectiveForReversalOfBefore(
            accountId, listOf(TransactionType.EXPENSE), before
        )
        val reversalDebit  = paymentRepository.sumCashEffectiveForReversalOfBefore(
            accountId, listOf(TransactionType.INCOME, TransactionType.ADJUSTMENT), before
        )
        return br.com.sisgfin.financial.accounts.AccountBalanceFormula.compute(
            initialBalance = initialBalance,
            income         = income,
            expense        = expense,
            adjustment     = adjustment,
            transferIn     = transferIn,
            transferOut    = transferOut,
            reversalCredit = reversalCredit,
            reversalDebit  = reversalDebit
        )
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

    fun deactivate(id: Int, version: Int) {
        transaction {
            val rows = FinancialTransactionsTable.update({
                (FinancialTransactionsTable.id eq id) and
                (FinancialTransactionsTable.version eq version)
            }) {
                it[FinancialTransactionsTable.isActive] = false
                it[FinancialTransactionsTable.status] = TransactionStatus.CANCELED.name
                it[updatedAt] = LocalDateTime.now()
                it[FinancialTransactionsTable.version] = version + 1
            }
            if (rows == 0) throw ConcurrentModificationException("Conflito de versão no lançamento #$id")
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
        contractId            = row[FinancialTransactionsTable.contractId],
        interestAmount        = row[FinancialTransactionsTable.interestAmount]?.toMoney(),
        fineAmount            = row[FinancialTransactionsTable.fineAmount]?.toMoney(),
        projectId             = row[FinancialTransactionsTable.projectId],
        reversedType          = row[FinancialTransactionsTable.reversedType]?.let { TransactionType.valueOf(it) },
        origin                = runCatching { TransactionOrigin.valueOf(row[FinancialTransactionsTable.origin]) }.getOrDefault(TransactionOrigin.MANUAL),
        version               = row[FinancialTransactionsTable.version]
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

    // Fase 8-C: retorna lançamentos canceláveis de um funcionário dentro do mês de referência e seguinte.
    // Filtra por origin=PAYROLL_ENGINE|PAYROLL_IMPORT para não cancelar lançamentos MANUAL com employeeId.
    fun findPendingPayrollForMonth(employeeId: Int, month: YearMonth): List<Transaction> = transaction {
        val from = month.atDay(1).atStartOfDay()
        val to = month.plusMonths(2).atDay(1).atStartOfDay()
        val cancelable = listOf(
            TransactionStatus.PENDING.name,
            TransactionStatus.DRAFT.name,
            TransactionStatus.SCHEDULED.name
        )
        val payrollOrigins = listOf(
            TransactionOrigin.PAYROLL_ENGINE.name,
            TransactionOrigin.PAYROLL_IMPORT.name
        )
        FinancialTransactionsTable.selectAll()
            .where {
                (FinancialTransactionsTable.employeeId eq employeeId) and
                (FinancialTransactionsTable.status inList cancelable) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.dueDate greaterEq from) and
                (FinancialTransactionsTable.dueDate less to) and
                (FinancialTransactionsTable.origin inList payrollOrigins)
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
            val candidates = FinancialTransactionsTable
                .select(FinancialTransactionsTable.id, FinancialTransactionsTable.version)
                .where {
                    (FinancialTransactionsTable.recurrenceTemplateId eq templateId) and
                    (FinancialTransactionsTable.dueDate greaterEq from.atStartOfDay()) and
                    (FinancialTransactionsTable.status inList cancelableStatuses) and
                    (FinancialTransactionsTable.isActive eq true)
                }
                .map { it[FinancialTransactionsTable.id] to it[FinancialTransactionsTable.version] }

            val now = LocalDateTime.now()
            candidates.forEach { (id, ver) ->
                val rows = FinancialTransactionsTable.update({
                    (FinancialTransactionsTable.id eq id) and
                    (FinancialTransactionsTable.version eq ver)
                }) {
                    it[FinancialTransactionsTable.isActive] = false
                    it[FinancialTransactionsTable.status]   = TransactionStatus.CANCELED.name
                    it[updatedAt] = now
                    it[FinancialTransactionsTable.version]  = ver + 1
                }
                if (rows == 0) throw ConcurrentModificationException("Conflito de versão no lançamento #$id")
            }
        }
    }
}
