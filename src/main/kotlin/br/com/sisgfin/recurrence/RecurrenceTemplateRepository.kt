package br.com.sisgfin.recurrence

import br.com.sisgfin.financial.money.toMoney
import br.com.sisgfin.financial.transactions.TransactionType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class RecurrenceTemplateRepository {

    fun findAll(): List<RecurrenceTemplate> = transaction {
        RecurrenceTemplatesTable.selectAll()
            .orderBy(RecurrenceTemplatesTable.description to SortOrder.ASC)
            .map { rowToTemplate(it) }
    }

    fun findAllActive(): List<RecurrenceTemplate> = transaction {
        RecurrenceTemplatesTable.selectAll()
            .where { RecurrenceTemplatesTable.isActive eq true }
            .map { rowToTemplate(it) }
    }

    fun findById(id: Int): RecurrenceTemplate? = transaction {
        RecurrenceTemplatesTable.selectAll()
            .where { RecurrenceTemplatesTable.id eq id }
            .map { rowToTemplate(it) }
            .singleOrNull()
    }

    fun insert(template: RecurrenceTemplate): Int = transaction {
        RecurrenceTemplatesTable.insert {
            it[description]  = template.description
            it[amount]       = template.amount.value
            it[type]         = template.type.name
            it[intervalType] = template.interval.name
            it[dayOfMonth]   = template.dayOfMonth
            it[accountId]    = template.accountId
            it[supplierId]   = template.supplierId
            it[categoryId]   = template.categoryId
            it[costCenterId] = template.costCenterId
            it[documentType] = template.documentType
            it[notes]        = template.notes
            it[startsAt]     = template.startsAt
            it[endsAt]       = template.endsAt
            it[isActive]     = template.isActive
            it[contractId]   = template.contractId
            it[createdBy]    = template.createdBy
            it[createdAt]    = template.createdAt
            it[updatedAt]    = template.updatedAt
        } get RecurrenceTemplatesTable.id
    }

    fun update(template: RecurrenceTemplate) {
        transaction {
            RecurrenceTemplatesTable.update({ RecurrenceTemplatesTable.id eq template.id }) {
                it[description]  = template.description
                it[amount]       = template.amount.value
                it[type]         = template.type.name
                it[intervalType] = template.interval.name
                it[dayOfMonth]   = template.dayOfMonth
                it[accountId]    = template.accountId
                it[supplierId]   = template.supplierId
                it[categoryId]   = template.categoryId
                it[costCenterId] = template.costCenterId
                it[documentType] = template.documentType
                it[notes]        = template.notes
                it[startsAt]     = template.startsAt
                it[endsAt]       = template.endsAt
                it[isActive]     = template.isActive
                it[contractId]   = template.contractId
                it[updatedAt]    = LocalDateTime.now()
            }
        }
    }

    fun setActive(id: Int, active: Boolean) {
        transaction {
            RecurrenceTemplatesTable.update({ RecurrenceTemplatesTable.id eq id }) {
                it[isActive]  = active
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    private fun rowToTemplate(row: ResultRow) = RecurrenceTemplate(
        id           = row[RecurrenceTemplatesTable.id],
        description  = row[RecurrenceTemplatesTable.description],
        amount       = row[RecurrenceTemplatesTable.amount].toMoney(),
        type         = TransactionType.valueOf(row[RecurrenceTemplatesTable.type]),
        interval     = RecurrenceInterval.valueOf(row[RecurrenceTemplatesTable.intervalType]),
        dayOfMonth   = row[RecurrenceTemplatesTable.dayOfMonth],
        accountId    = row[RecurrenceTemplatesTable.accountId],
        supplierId   = row[RecurrenceTemplatesTable.supplierId],
        categoryId   = row[RecurrenceTemplatesTable.categoryId],
        costCenterId = row[RecurrenceTemplatesTable.costCenterId],
        documentType = row[RecurrenceTemplatesTable.documentType],
        notes        = row[RecurrenceTemplatesTable.notes],
        startsAt     = row[RecurrenceTemplatesTable.startsAt],
        endsAt       = row[RecurrenceTemplatesTable.endsAt],
        isActive     = row[RecurrenceTemplatesTable.isActive],
        contractId   = row[RecurrenceTemplatesTable.contractId],
        createdBy    = row[RecurrenceTemplatesTable.createdBy],
        createdAt    = row[RecurrenceTemplatesTable.createdAt],
        updatedAt    = row[RecurrenceTemplatesTable.updatedAt]
    )
}
