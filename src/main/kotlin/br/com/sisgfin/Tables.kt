package br.com.sisgfin

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val username = varchar("username", 50).uniqueIndex()
    val email = varchar("email", 100)
    val passwordHash = varchar("password_hash", 100)
    val role = varchar("role", 20).default("OPERADOR")
    val isActive = bool("is_active").default(true)
    val lastLoginAt = datetime("last_login_at").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
    val createdBy = integer("created_by").nullable() // Will add reference later or use manual constraint
    override val primaryKey = PrimaryKey(id)
}

object AuditLogs : Table("audit_logs") {
    val id = integer("id").autoIncrement()
    val entityType = varchar("entity_type", 50)
    val entityId = integer("entity_id")
    val action = varchar("action", 100)
    val oldValue = text("old_value").nullable()
    val newValue = text("new_value").nullable()
    val performedBy = integer("performed_by").references(Users.id).nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}

object Suppliers : Table("suppliers") {
    val id = integer("id").autoIncrement()
    val document = varchar("document", 20).uniqueIndex()
    val name = varchar("name", 100)
    val tradeName = varchar("trade_name", 100).nullable()
    val email = varchar("email", 100).nullable()
    val phone = varchar("phone", 20).nullable()
    val pixKey = varchar("pix_key", 100).nullable()
    val bank = varchar("bank", 50).nullable()
    val agency = varchar("agency", 20).nullable()
    val account = varchar("account", 20).nullable()
    val notes = text("notes").nullable()
    val entityType = varchar("entity_type", 20).default("FORNECEDOR")
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
    val createdBy = integer("created_by").references(Users.id).nullable()
    override val primaryKey = PrimaryKey(id)
}

object FinancialAccounts : Table("financial_accounts") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val bankName = varchar("bank_name", 50).nullable()
    val agency = varchar("agency", 20).nullable()
    val accountNumber = varchar("account_number", 20).nullable()
    val accountType = varchar("account_type", 20)
    val initialBalance = decimal("initial_balance", 19, 2).default(java.math.BigDecimal.ZERO)
    val investmentBroker = varchar("investment_broker", 100).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
    val createdBy = integer("created_by").references(Users.id).nullable()
    override val primaryKey = PrimaryKey(id)
}

object CostCenters : Table("projects") {
    val id = integer("id").autoIncrement()
    val code = varchar("code", 50).uniqueIndex()
    val name = varchar("name", 100)
    val description = text("description").nullable()
    val startDate = datetime("start_date").nullable()
    val endDate = datetime("end_date").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
    val createdBy = integer("created_by").references(Users.id).nullable()
    override val primaryKey = PrimaryKey(id)
}

object Accounts : Table("accounts") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val balance = decimal("balance", 19, 2).default(java.math.BigDecimal.ZERO)
    override val primaryKey = PrimaryKey(id)
}

object Transactions : Table("transactions") {
    val id = integer("id").autoIncrement()
    val description = varchar("description", 255)
    val amount = decimal("amount", 19, 2)
    val type = varchar("type", 20) // "RECEITA" or "DESPESA"
    val date = datetime("date").default(LocalDateTime.now())
    val accountId = integer("account_id").references(Accounts.id)
    override val primaryKey = PrimaryKey(id)
}

object Employees : Table("employees") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val document = varchar("document", 20)
    val phone = varchar("phone", 20)
    val email = varchar("email", 100)
    val role = varchar("role", 50)
    val salary = decimal("salary", 19, 2)
    val paymentDay     = integer("payment_day")
    val paymentDays    = varchar("payment_days", 50).nullable()
    val employmentType = varchar("employment_type", 10).nullable()
    val active = bool("active").default(true)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}
