package br.com.sisgfin

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.math.BigDecimal
import java.time.LocalDateTime

object DatabaseFactory {

    fun tryConnect(config: DbConfig): Result<Unit> = runCatching {
        DbConfigStore.testConnection(config).getOrThrow()
        Database.connect(
            url      = config.url,
            driver   = "org.postgresql.Driver",
            user     = config.user,
            password = config.password
        )
    }

    fun runMigrations(): Result<Unit> = runCatching {
        val config = DbConfigStore.load()
            ?: error("Configuração de banco não encontrada — impossível migrar.")
        Flyway.configure()
            .dataSource(config.url, config.user, config.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        seed()
    }

    private fun seed() = transaction {
        if (Users.selectAll().empty()) {
            val adminId = Users.insert {
                it[name]         = "Administrador Geral"
                it[username]     = "admin"
                it[email]        = "admin@sisgfin.com"
                it[passwordHash] = BCrypt.hashpw("123", BCrypt.gensalt())
                it[role]         = UserRole.ADMIN.name
                it[isActive]     = true
            } get Users.id

            Users.insert {
                it[name]         = "Operador Financeiro"
                it[username]     = "user"
                it[email]        = "user@sisgfin.com"
                it[passwordHash] = BCrypt.hashpw("123", BCrypt.gensalt())
                it[role]         = UserRole.OPERADOR.name
                it[isActive]     = true
                it[createdBy]    = adminId
            }

            val accountId = Accounts.insert {
                it[name]    = "Conta Principal"
                it[balance] = BigDecimal("45230.00")
            } get Accounts.id

            Transactions.insert {
                it[description]    = "Pagamento Aluguel"
                it[amount]         = BigDecimal("2500.00")
                it[type]           = "DESPESA"
                it[date]           = LocalDateTime.now().minusDays(1)
                it[this.accountId] = accountId
            }

            Transactions.insert {
                it[description]    = "Venda Projeto X"
                it[amount]         = BigDecimal("8000.00")
                it[type]           = "RECEITA"
                it[date]           = LocalDateTime.now().minusDays(2)
                it[this.accountId] = accountId
            }

            Transactions.insert {
                it[description]    = "Supermercado"
                it[amount]         = BigDecimal("450.00")
                it[type]           = "DESPESA"
                it[date]           = LocalDateTime.now().minusHours(5)
                it[this.accountId] = accountId
            }
        }
    }
}
