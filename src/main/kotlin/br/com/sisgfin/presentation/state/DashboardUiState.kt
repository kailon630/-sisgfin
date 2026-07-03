package br.com.sisgfin.presentation.state

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.cashflow.DailyCashFlowEntry
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction

data class AccountBalance(
    val account: FinancialAccount,
    val balance: Money
)

data class DashboardUiState(
    val isLoading: Boolean = false,
    val consolidatedBalance: Money = Money.ZERO,
    val accountBalances: List<AccountBalance> = emptyList(),
    val monthIncome: Money = Money.ZERO,
    val monthExpense: Money = Money.ZERO,
    val overdueItems: List<Transaction> = emptyList(),
    val dueSoonItems: List<Transaction> = emptyList(),
    val overdueReceivables: List<Transaction> = emptyList(),
    val recentPaid: List<Transaction> = emptyList(),
    val cashFlowPreview: List<DailyCashFlowEntry> = emptyList(),
    val projectedBalance: Money = Money.ZERO,
    val errorMessage: String? = null
) {
    val overdueCount: Int   get() = overdueItems.size
    val dueSoonCount: Int   get() = dueSoonItems.size
    val overdueAmount: Money  get() = overdueItems.fold(Money.ZERO) { a, t -> a + t.amount }
    val dueSoonAmount: Money  get() = dueSoonItems.fold(Money.ZERO) { a, t -> a + t.amount }
    val monthBalance: Money   get() = monthIncome - monthExpense
    val overdueReceivablesCount: Int  get() = overdueReceivables.size
    val overdueReceivablesAmount: Money get() = overdueReceivables.fold(Money.ZERO) { a, t -> a + t.amount }
}
