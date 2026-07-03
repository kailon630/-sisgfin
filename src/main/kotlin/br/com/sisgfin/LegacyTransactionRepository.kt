package br.com.sisgfin

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.money.toMoney
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

/** Legado V1 — tabela `transactions` vinculada a `accounts` (dashboard). */
data class LegacyTransaction(
    val id: Int,
    val description: String,
    val amount: Money,
    val type: String,
    val date: LocalDateTime,
    val accountId: Int
)

class LegacyTransactionRepository {
    fun getRecent(limit: Int = 10): List<LegacyTransaction> = transaction {
        Transactions.selectAll()
            .orderBy(Transactions.date to SortOrder.DESC)
            .limit(limit)
            .map {
                LegacyTransaction(
                    it[Transactions.id],
                    it[Transactions.description],
                    it[Transactions.amount].toMoney(),
                    it[Transactions.type],
                    it[Transactions.date],
                    it[Transactions.accountId]
                )
            }
    }
}
