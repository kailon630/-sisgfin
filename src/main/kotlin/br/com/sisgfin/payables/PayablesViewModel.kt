package br.com.sisgfin.payables

import br.com.sisgfin.core.errors.AppLogger
import br.com.sisgfin.core.errors.ErrorClassifier
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.CounterpartyMap
import br.com.sisgfin.financial.transactions.CounterpartyResolver
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionQuery
import br.com.sisgfin.financial.transactions.TransactionService
import br.com.sisgfin.financial.transactions.TransactionStatus
import br.com.sisgfin.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

enum class PayablesTileFilter { ALL, OVERDUE, TODAY, THIS_WEEK }

data class PayablesSummary(
    val overdue: Money,
    val overdueCount: Int,
    val dueToday: Money,
    val dueTodayCount: Int,
    val thisWeek: Money,
    val thisWeekCount: Int,
    val total: Money,
    val totalCount: Int
) {
    companion object {
        val EMPTY = PayablesSummary(
            Money.ZERO, 0, Money.ZERO, 0, Money.ZERO, 0, Money.ZERO, 0
        )
    }
}

data class PayablesUiState(
    val isLoading: Boolean = false,
    val allItems: List<Transaction> = emptyList(),
    val tileFilter: PayablesTileFilter = PayablesTileFilter.ALL,
    val summary: PayablesSummary = PayablesSummary.EMPTY,
    val counterparties: CounterpartyMap = CounterpartyMap.EMPTY,
    val selectedItem: Transaction? = null,
    val errorMessage: String? = null
) {
    val items: List<Transaction>
        get() {
            val today   = LocalDate.now()
            val weekEnd = today.with(DayOfWeek.SUNDAY)
            return when (tileFilter) {
                PayablesTileFilter.ALL      -> allItems
                PayablesTileFilter.OVERDUE  -> allItems.filter { it.status == TransactionStatus.OVERDUE }
                PayablesTileFilter.TODAY    -> allItems.filter { it.dueDate.toLocalDate() == today }
                PayablesTileFilter.THIS_WEEK -> allItems.filter {
                    it.dueDate.toLocalDate().let { d -> d >= today && d <= weekEnd }
                }
            }
        }
}

class PayablesViewModel(
    private val transactionService: TransactionService,
    private val counterpartyResolver: CounterpartyResolver
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(PayablesUiState(isLoading = true))
    val uiState: StateFlow<PayablesUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    transactionService.syncOverdueStatuses()
                    val all = transactionService.listByQuery(TransactionQuery.aPagar())
                    Triple(all, counterpartyResolver.resolve(all), buildSummary(all))
                }
            }
            result.fold(
                onSuccess = { (items, cps, summary) ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allItems = items,
                        counterparties = cps,
                        summary = summary,
                        errorMessage = null
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Erro ao carregar contas a pagar"
                    )
                }
            )
        }
    }

    fun selectTile(filter: PayablesTileFilter) {
        val next = if (_uiState.value.tileFilter == filter) PayablesTileFilter.ALL else filter
        _uiState.value = _uiState.value.copy(tileFilter = next)
    }

    fun selectTransaction(tx: Transaction) {
        _uiState.value = _uiState.value.copy(selectedItem = tx)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedItem = null)
    }

    fun canConfirmPayment(): Boolean = transactionService.canConfirmPayment()

    fun markAsPaidFull(id: Int) {
        viewModelScope.launch {
            val tx = _uiState.value.allItems.find { it.id == id } ?: return@launch
            val remaining = tx.outstandingPrincipal
            withContext(Dispatchers.IO) {
                runCatching {
                    transactionService.recordPayment(id, LocalDateTime.now(), remaining, null, null)
                }
            }.onSuccess {
                load()
            }.onFailure { err ->
                val appError = ErrorClassifier.classify(err)
                AppLogger.error(appError)
                _uiState.value = _uiState.value.copy(errorMessage = appError.userMessage)
            }
        }
    }

    fun cancelTransaction(id: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { transactionService.cancel(id) }
            }.onSuccess {
                load()
            }.onFailure { err ->
                val appError = ErrorClassifier.classify(err)
                AppLogger.error(appError)
                _uiState.value = _uiState.value.copy(errorMessage = appError.userMessage)
            }
        }
    }

    fun duplicateTransaction(id: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { transactionService.duplicate(id) }
            }.onSuccess {
                load()
            }.onFailure { err ->
                val appError = ErrorClassifier.classify(err)
                AppLogger.error(appError)
                _uiState.value = _uiState.value.copy(errorMessage = appError.userMessage)
            }
        }
    }

    private fun buildSummary(items: List<Transaction>): PayablesSummary {
        val today   = LocalDate.now()
        val weekEnd = today.with(DayOfWeek.SUNDAY)

        val overdue   = items.filter { it.status == TransactionStatus.OVERDUE }
        val dueToday  = items.filter { it.dueDate.toLocalDate() == today }
        val thisWeek  = items.filter {
            it.dueDate.toLocalDate().let { d -> d >= today && d <= weekEnd }
        }
        fun sum(l: List<Transaction>) = l.fold(Money.ZERO) { a, t -> a + t.amount }
        return PayablesSummary(
            overdue      = sum(overdue),  overdueCount   = overdue.size,
            dueToday     = sum(dueToday), dueTodayCount  = dueToday.size,
            thisWeek     = sum(thisWeek), thisWeekCount  = thisWeek.size,
            total        = sum(items),    totalCount     = items.size
        )
    }
}
