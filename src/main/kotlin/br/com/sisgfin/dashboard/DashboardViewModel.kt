package br.com.sisgfin.dashboard

import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.FinancialAccountService
import br.com.sisgfin.cashflow.CashFlowService
import br.com.sisgfin.core.errors.AppLogger
import br.com.sisgfin.core.errors.ErrorClassifier
import br.com.sisgfin.core.result.Result
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import br.com.sisgfin.presentation.state.AccountBalance
import br.com.sisgfin.presentation.state.DashboardUiState
import br.com.sisgfin.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class DashboardViewModel(
    private val accountRepository: FinancialAccountRepository,
    private val accountService: FinancialAccountService,
    private val transactionRepository: TransactionRepository,
    private val cashFlowService: CashFlowService
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = withContext(Dispatchers.IO) {
                try {
                    val today      = LocalDate.now()
                    val monthStart = today.withDayOfMonth(1)
                    val dueSoonEnd = today.plusDays(7)

                    // Contas ativas + saldo calculado pelo motor novo
                    val accounts = accountRepository.findAll().filter { it.isActive }
                    val accountBalances = accounts.map { acc ->
                        AccountBalance(acc, accountService.calculateBalance(acc.id))
                    }
                    val consolidated = accountBalances.fold(Money.ZERO) { a, ab -> a + ab.balance }

                    // Receita e despesa do mês corrente (PAID)
                    val monthPaid = transactionRepository.findAllPaid(from = monthStart, to = today)
                    val monthIncome = monthPaid
                        .filter { it.type == TransactionType.INCOME || it.type == TransactionType.REVERSAL }
                        .fold(Money.ZERO) { a, t -> a + (t.paidAmount ?: t.amount) }
                    val monthExpense = monthPaid
                        .filter { it.type == TransactionType.EXPENSE }
                        .fold(Money.ZERO) { a, t -> a + (t.paidAmount ?: t.amount) }

                    // Vencidos (despesas)
                    val overdueAll = transactionRepository.filterOverdue()
                    val overdueItems = overdueAll.filter { it.type != TransactionType.INCOME }
                    val overdueReceivables = overdueAll.filter { it.type == TransactionType.INCOME }

                    // A vencer nos próximos 7 dias (apenas pendentes/agendados)
                    val dueSoonItems = transactionRepository.filterByDuePeriod(today, dueSoonEnd)
                        .filter {
                            it.status == TransactionStatus.PENDING ||
                            it.status == TransactionStatus.SCHEDULED ||
                            it.status == TransactionStatus.PARTIAL
                        }

                    // Últimas 15 liquidações
                    val recentPaid = transactionRepository.findAllPaid()
                        .takeLast(15)
                        .reversed()

                    // Projeção dos próximos 7 dias — primeiros 3 dias com compromissos
                    val projection = cashFlowService.project(accountId = null, windowDays = 7)
                    val cashFlowPreview = projection.entries.take(3)

                    Result.Success(
                        DashboardUiState(
                            isLoading            = false,
                            consolidatedBalance  = consolidated,
                            accountBalances      = accountBalances,
                            monthIncome          = monthIncome,
                            monthExpense         = monthExpense,
                            overdueItems         = overdueItems,
                            dueSoonItems         = dueSoonItems,
                            overdueReceivables   = overdueReceivables,
                            recentPaid           = recentPaid,
                            cashFlowPreview      = cashFlowPreview,
                            projectedBalance     = projection.projectedFinalBalance
                        )
                    )
                } catch (e: Exception) {
                    Result.Error(ErrorClassifier.classify(e))
                }
            }

            when (result) {
                is Result.Success    -> _uiState.value = result.data
                is Result.Error      -> {
                    AppLogger.error(result.error)
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.error.userMessage)
                }
                is Result.Validation -> Unit
            }
        }
    }
}
