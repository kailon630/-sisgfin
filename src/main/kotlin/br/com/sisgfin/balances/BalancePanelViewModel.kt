package br.com.sisgfin.balances

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.FinancialAccountService
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

data class AccountBalanceSummary(
    val account: FinancialAccount,
    val currentBalance: Money,
    val pendingTotal: Money,
    val pendingCount: Int,
    val overdueTotal: Money,
    val overdueCount: Int,
    val lastPaymentDate: LocalDateTime?
)

data class BalancePanelUiState(
    val summaries: List<AccountBalanceSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class BalancePanelViewModel(
    private val accountRepository: FinancialAccountRepository,
    private val accountService: FinancialAccountService,
    private val transactionRepository: TransactionRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(BalancePanelUiState(isLoading = true))
    val uiState: StateFlow<BalancePanelUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    val accounts = accountRepository.findAll().filter { it.isActive }
                    accounts.map { account ->
                        AccountBalanceSummary(
                            account = account,
                            currentBalance = accountService.calculateBalance(account.id),
                            pendingTotal = transactionRepository.sumByStatus(account.id, TransactionStatus.PENDING),
                            pendingCount = transactionRepository.countByStatus(account.id, TransactionStatus.PENDING),
                            overdueTotal = transactionRepository.sumByStatus(account.id, TransactionStatus.OVERDUE),
                            overdueCount = transactionRepository.countByStatus(account.id, TransactionStatus.OVERDUE),
                            lastPaymentDate = transactionRepository.lastPaymentDate(account.id)
                        )
                    }
                }
            }.onSuccess { summaries ->
                _uiState.value = BalancePanelUiState(summaries = summaries)
            }.onFailure { e ->
                _uiState.value = BalancePanelUiState(errorMessage = e.message)
            }
        }
    }
}
