package br.com.sisgfin.cashflow

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import java.time.LocalDate

data class SimulationEntry(
    val description: String,
    val amount: Money,
    val dueDate: LocalDate,
    val accountId: Int? = null
)

data class DailyCashFlowEntry(
    val date: LocalDate,
    val transactions: List<Transaction>,
    val totalOutflow: Money,
    val totalInflow: Money,
    val projectedBalance: Money,
    val isSimulated: Boolean = false,
    val simulationLabel: String? = null
)

data class CashFlowUiState(
    val currentBalance: Money = Money.ZERO,
    val entries: List<DailyCashFlowEntry> = emptyList(),
    val overdueTransactions: List<Transaction> = emptyList(),
    val overdueTotal: Money = Money.ZERO,
    val totalCommitted: Money = Money.ZERO,
    val projectedFinalBalance: Money = Money.ZERO,
    val baseProjectedBalance: Money = Money.ZERO,
    val windowDays: Int = 14,
    val selectedAccountId: Int? = null,
    val accounts: List<FinancialAccount> = emptyList(),
    val simulationEntries: List<SimulationEntry> = emptyList(),
    val isSimulationPanelOpen: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isSimulating: Boolean get() = simulationEntries.isNotEmpty()
    val simulationDelta: Money get() = projectedFinalBalance - baseProjectedBalance
}
