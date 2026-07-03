package br.com.sisgfin.contracts

import br.com.sisgfin.financial.money.toMoney
import br.com.sisgfin.financial.transactions.TransactionType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class ContractRepository {

    fun findAll(): List<Contract> = transaction {
        ContractsTable.selectAll()
            .orderBy(ContractsTable.startDate to SortOrder.DESC)
            .map { rowToContract(it) }
    }

    fun findByStatus(status: ContractStatus): List<Contract> = transaction {
        ContractsTable.selectAll()
            .where { ContractsTable.status eq status.name }
            .orderBy(ContractsTable.startDate to SortOrder.DESC)
            .map { rowToContract(it) }
    }

    fun findActive(): List<Contract> = findByStatus(ContractStatus.VIGENTE)

    fun findByContractor(supplierId: Int): List<Contract> = transaction {
        ContractsTable.selectAll()
            .where { ContractsTable.contractorId eq supplierId }
            .orderBy(ContractsTable.startDate to SortOrder.DESC)
            .map { rowToContract(it) }
    }

    fun findById(id: Int): Contract? = transaction {
        ContractsTable.selectAll()
            .where { ContractsTable.id eq id }
            .map { rowToContract(it) }
            .singleOrNull()
    }

    fun numberExists(number: String, excludeId: Int = 0): Boolean = transaction {
        ContractsTable.selectAll()
            .where { (ContractsTable.number eq number) and (ContractsTable.id neq excludeId) }
            .count() > 0
    }

    fun insert(contract: Contract): Int = transaction {
        ContractsTable.insert {
            it[number]               = contract.number
            it[description]          = contract.description
            it[contractorId]         = contract.contractorId
            it[type]                 = contract.type.name
            it[totalValue]           = contract.totalValue.value
            it[startDate]            = contract.startDate
            it[endDate]              = contract.endDate
            it[status]               = contract.status.name
            it[notes]                = contract.notes
            it[recurrenceTemplateId] = contract.recurrenceTemplateId
            it[createdBy]            = contract.createdBy
            it[createdAt]            = contract.createdAt
            it[updatedAt]            = contract.updatedAt
        } get ContractsTable.id
    }

    fun update(contract: Contract) {
        transaction {
            ContractsTable.update({ ContractsTable.id eq contract.id }) {
                it[number]               = contract.number
                it[description]          = contract.description
                it[contractorId]         = contract.contractorId
                it[type]                 = contract.type.name
                it[totalValue]           = contract.totalValue.value
                it[startDate]            = contract.startDate
                it[endDate]              = contract.endDate
                it[status]               = contract.status.name
                it[notes]                = contract.notes
                it[recurrenceTemplateId] = contract.recurrenceTemplateId
                it[updatedAt]            = LocalDateTime.now()
            }
        }
    }

    private fun rowToContract(row: ResultRow) = Contract(
        id                   = row[ContractsTable.id],
        number               = row[ContractsTable.number],
        description          = row[ContractsTable.description],
        contractorId         = row[ContractsTable.contractorId],
        type                 = TransactionType.valueOf(row[ContractsTable.type]),
        totalValue           = row[ContractsTable.totalValue].toMoney(),
        startDate            = row[ContractsTable.startDate],
        endDate              = row[ContractsTable.endDate],
        status               = ContractStatus.valueOf(row[ContractsTable.status]),
        notes                = row[ContractsTable.notes],
        recurrenceTemplateId = row[ContractsTable.recurrenceTemplateId],
        createdBy            = row[ContractsTable.createdBy],
        createdAt            = row[ContractsTable.createdAt],
        updatedAt            = row[ContractsTable.updatedAt]
    )
}
