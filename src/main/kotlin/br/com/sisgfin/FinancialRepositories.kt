package br.com.sisgfin

import br.com.sisgfin.core.domain.MutableEntityRepository
import br.com.sisgfin.financial.money.toMoney
import br.com.sisgfin.suppliers.EntityType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class SupplierRepository : MutableEntityRepository<Supplier> {
    fun findByDocument(document: String): Supplier? = transaction {
        Suppliers.selectAll()
            .where { Suppliers.document eq document }
            .map { rowToSupplier(it) }
            .singleOrNull()
    }

    override fun findAll(): List<Supplier> = transaction {
        Suppliers.selectAll().orderBy(Suppliers.name to SortOrder.ASC).map { rowToSupplier(it) }
    }

    fun findSuppliers(): List<Supplier> = transaction {
        Suppliers.selectAll()
            .where { Suppliers.entityType inList listOf(EntityType.FORNECEDOR.name, EntityType.AMBOS.name) }
            .orderBy(Suppliers.name to SortOrder.ASC)
            .map { rowToSupplier(it) }
    }

    fun findClients(): List<Supplier> = transaction {
        Suppliers.selectAll()
            .where { Suppliers.entityType inList listOf(EntityType.CLIENTE.name, EntityType.AMBOS.name) }
            .orderBy(Suppliers.name to SortOrder.ASC)
            .map { rowToSupplier(it) }
    }

    override fun findById(id: Int): Supplier? = transaction {
        Suppliers.selectAll().where { Suppliers.id eq id }.map { rowToSupplier(it) }.singleOrNull()
    }

    override fun insert(supplier: Supplier): Int = transaction {
        Suppliers.insert {
            it[document] = supplier.document
            it[name] = supplier.name
            it[tradeName] = supplier.tradeName
            it[email] = supplier.email
            it[phone] = supplier.phone
            it[pixKey] = supplier.pixKey
            it[bank] = supplier.bank
            it[agency] = supplier.agency
            it[account] = supplier.account
            it[notes] = supplier.notes
            it[entityType] = supplier.entityType.name
            it[isActive] = supplier.isActive
            it[createdBy] = supplier.createdBy
        } get Suppliers.id
    }

    override fun update(supplier: Supplier) {
        transaction {
            Suppliers.update({ Suppliers.id eq supplier.id }) {
                it[document] = supplier.document
                it[name] = supplier.name
                it[tradeName] = supplier.tradeName
                it[email] = supplier.email
                it[phone] = supplier.phone
                it[pixKey] = supplier.pixKey
                it[bank] = supplier.bank
                it[agency] = supplier.agency
                it[account] = supplier.account
                it[notes] = supplier.notes
                it[entityType] = supplier.entityType.name
                it[isActive] = supplier.isActive
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    fun rowToSupplier(row: ResultRow) = Supplier(
        id = row[Suppliers.id],
        document = row[Suppliers.document],
        name = row[Suppliers.name],
        tradeName = row[Suppliers.tradeName],
        email = row[Suppliers.email],
        phone = row[Suppliers.phone],
        pixKey = row[Suppliers.pixKey],
        bank = row[Suppliers.bank],
        agency = row[Suppliers.agency],
        account = row[Suppliers.account],
        notes = row[Suppliers.notes],
        entityType = runCatching { EntityType.valueOf(row[Suppliers.entityType]) }.getOrDefault(EntityType.FORNECEDOR),
        isActive = row[Suppliers.isActive],
        createdAt = row[Suppliers.createdAt],
        updatedAt = row[Suppliers.updatedAt],
        createdBy = row[Suppliers.createdBy]
    )
}

class FinancialAccountRepository : MutableEntityRepository<FinancialAccount> {
    override fun findAll(): List<FinancialAccount> = transaction {
        FinancialAccounts.selectAll().map { rowToAccount(it) }
    }

    override fun findById(id: Int): FinancialAccount? = transaction {
        FinancialAccounts.selectAll().where { FinancialAccounts.id eq id }.map { rowToAccount(it) }.singleOrNull()
    }

    override fun insert(account: FinancialAccount): Int = transaction {
        FinancialAccounts.insert {
            it[name] = account.name
            it[bankName] = account.bankName
            it[agency] = account.agency
            it[accountNumber] = account.accountNumber
            it[accountType] = account.accountType.name
            it[initialBalance] = account.initialBalance.value
            it[investmentBroker] = account.investmentBroker
            it[isActive] = account.isActive
            it[createdBy] = account.createdBy
        } get FinancialAccounts.id
    }

    override fun update(account: FinancialAccount) {
        transaction {
        FinancialAccounts.update({ FinancialAccounts.id eq account.id }) {
            it[name] = account.name
            it[bankName] = account.bankName
            it[agency] = account.agency
            it[accountNumber] = account.accountNumber
            it[accountType] = account.accountType.name
            it[initialBalance] = account.initialBalance.value
            it[investmentBroker] = account.investmentBroker
            it[isActive] = account.isActive
            it[updatedAt] = LocalDateTime.now()
        }
        }
    }

    private fun rowToAccount(row: ResultRow) = FinancialAccount(
        id = row[FinancialAccounts.id],
        name = row[FinancialAccounts.name],
        bankName = row[FinancialAccounts.bankName],
        agency = row[FinancialAccounts.agency],
        accountNumber = row[FinancialAccounts.accountNumber],
        accountType = FinancialAccountType.valueOf(row[FinancialAccounts.accountType]),
        initialBalance = row[FinancialAccounts.initialBalance].toMoney(),
        investmentBroker = row[FinancialAccounts.investmentBroker],
        isActive = row[FinancialAccounts.isActive],
        createdAt = row[FinancialAccounts.createdAt],
        updatedAt = row[FinancialAccounts.updatedAt],
        createdBy = row[FinancialAccounts.createdBy]
    )
}

class CostCenterRepository : MutableEntityRepository<CostCenter> {
    override fun findAll(): List<CostCenter> = transaction {
        CostCenters.selectAll().map { rowToCostCenter(it) }
    }

    override fun findById(id: Int): CostCenter? = transaction {
        CostCenters.selectAll().where { CostCenters.id eq id }.map { rowToCostCenter(it) }.singleOrNull()
    }

    override fun insert(costCenter: CostCenter): Int = transaction {
        CostCenters.insert {
            it[code] = costCenter.code
            it[name] = costCenter.name
            it[description] = costCenter.description
            it[startDate] = costCenter.startDate
            it[endDate] = costCenter.endDate
            it[isActive] = costCenter.isActive
            it[createdBy] = costCenter.createdBy
        } get CostCenters.id
    }

    override fun update(costCenter: CostCenter) {
        transaction {
            CostCenters.update({ CostCenters.id eq costCenter.id }) {
                it[code] = costCenter.code
                it[name] = costCenter.name
                it[description] = costCenter.description
                it[startDate] = costCenter.startDate
                it[endDate] = costCenter.endDate
                it[isActive] = costCenter.isActive
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    fun hardDelete(costCenterId: Int) = transaction {
        CostCenters.deleteWhere { with(SqlExpressionBuilder) { id eq costCenterId } }
    }

    private fun rowToCostCenter(row: ResultRow) = CostCenter(
        id = row[CostCenters.id],
        code = row[CostCenters.code],
        name = row[CostCenters.name],
        description = row[CostCenters.description],
        startDate = row[CostCenters.startDate],
        endDate = row[CostCenters.endDate],
        isActive = row[CostCenters.isActive],
        createdAt = row[CostCenters.createdAt],
        updatedAt = row[CostCenters.updatedAt],
        createdBy = row[CostCenters.createdBy]
    )
}
