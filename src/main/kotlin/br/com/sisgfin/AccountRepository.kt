package br.com.sisgfin

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.toMoney
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

data class Account(val id: Int, val name: String, val balance: Money)

class AccountRepository {
    fun getAll(): List<Account> = transaction {
        Accounts.selectAll().map {
            Account(it[Accounts.id], it[Accounts.name], it[Accounts.balance].toMoney())
        }
    }
}
