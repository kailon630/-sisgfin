package br.com.sisgfin.cashflow

import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionService
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.financial.transactions.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class CashFlowViewModel(
    private val cashFlowService: CashFlowService,
    private val accountRepository: FinancialAccountRepository,
    private val transactionService: TransactionService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(CashFlowUiState(isLoading = true))
    val uiState: StateFlow<CashFlowUiState> = _uiState.asStateFlow()

    init { load() }

    // ── Projeção ─────────────────────────────────────────────────────────────

    fun setWindowDays(days: Int) {
        _uiState.value = _uiState.value.copy(windowDays = days)
        load()
    }

    fun setAccount(accountId: Int?) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
        load()
    }

    fun load() {
        val snapshot = _uiState.value
        scope.launch {
            _uiState.value = snapshot.copy(isLoading = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    val accounts = accountRepository.findAll().filter { it.isActive }
                    val base     = cashFlowService.project(snapshot.selectedAccountId, snapshot.windowDays)
                    val withSim  = if (snapshot.simulationEntries.isNotEmpty())
                        cashFlowService.projectWithSimulation(
                            snapshot.selectedAccountId,
                            snapshot.windowDays,
                            snapshot.simulationEntries
                        ) else base
                    Triple(accounts, base, withSim)
                }
            }.onSuccess { (accounts, base, withSim) ->
                _uiState.value = snapshot.copy(
                    isLoading             = false,
                    errorMessage          = null,
                    accounts              = accounts,
                    currentBalance        = withSim.currentBalance,
                    entries               = withSim.entries,
                    overdueTransactions   = withSim.overdueTransactions,
                    overdueTotal          = withSim.overdueTotal,
                    totalCommitted        = withSim.totalCommitted,
                    projectedFinalBalance = withSim.projectedFinalBalance,
                    baseProjectedBalance  = base.projectedFinalBalance
                )
            }.onFailure { e ->
                _uiState.value = snapshot.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    // ── Painel de simulação ───────────────────────────────────────────────────

    fun openSimulationPanel()  { _uiState.value = _uiState.value.copy(isSimulationPanelOpen = true)  }
    fun closeSimulationPanel() { _uiState.value = _uiState.value.copy(isSimulationPanelOpen = false) }

    fun addSimulation(entry: SimulationEntry) {
        val updated = _uiState.value.simulationEntries + entry
        _uiState.value = _uiState.value.copy(simulationEntries = updated)
        load()
    }

    fun removeSimulation(index: Int) {
        val updated = _uiState.value.simulationEntries.toMutableList().also { it.removeAt(index) }
        _uiState.value = _uiState.value.copy(simulationEntries = updated)
        load()
    }

    fun clearSimulations() {
        _uiState.value = _uiState.value.copy(simulationEntries = emptyList())
        load()
    }

    // Converte uma SimulationEntry em lançamento PENDING real e remove da simulação
    fun commitSimulation(index: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val entry = _uiState.value.simulationEntries.getOrNull(index) ?: return
        val accountId = entry.accountId
            ?: _uiState.value.accounts.firstOrNull()?.id
            ?: run { onError("Nenhuma conta disponível para criar o lançamento."); return }

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val now = LocalDateTime.now()
                    transactionService.create(
                        Transaction(
                            type        = TransactionType.EXPENSE,
                            status      = TransactionStatus.PENDING,
                            description = entry.description,
                            amount      = entry.amount,
                            issueDate   = now,
                            dueDate     = entry.dueDate.atStartOfDay(),
                            accountId   = accountId
                        )
                    )
                }
            }.onSuccess {
                removeSimulation(index)
                onSuccess()
            }.onFailure { e ->
                onError(e.message ?: "Erro ao criar lançamento.")
            }
        }
    }
}
