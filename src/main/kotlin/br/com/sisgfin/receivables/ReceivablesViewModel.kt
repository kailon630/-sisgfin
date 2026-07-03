package br.com.sisgfin.receivables

import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.core.errors.AppLogger
import br.com.sisgfin.core.errors.ErrorClassifier
import br.com.sisgfin.core.result.Result
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class AgingBucket(
    val label: String,
    val count: Int,
    val total: Money
)

data class ReceivablesGroup(
    val clientName: String,
    val items: List<Transaction>
) {
    val total: Money get() = items.fold(Money.ZERO) { a, t -> a + t.amount }
}

data class ReceivablesUiState(
    val isLoading: Boolean = false,
    val groups: List<ReceivablesGroup> = emptyList(),
    val aging: List<AgingBucket> = emptyList(),
    val errorMessage: String? = null
) {
    val totalCount: Int  get() = groups.sumOf { it.items.size }
    val grandTotal: Money get() = groups.fold(Money.ZERO) { a, g -> a + g.total }
}

class ReceivablesViewModel(
    private val transactionRepository: TransactionRepository,
    private val supplierRepository: SupplierRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(ReceivablesUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val today = LocalDate.now()
                    val all = transactionRepository.findReceivables()
                    val supplierNames = supplierRepository.findAll().associate { it.id to it.name }

                    val groups = all
                        .groupBy { it.supplierId }
                        .map { (suppId, txs) ->
                            ReceivablesGroup(
                                clientName = suppId?.let { supplierNames[it] } ?: "Sem cliente",
                                items = txs.sortedBy { it.dueDate }
                            )
                        }
                        .sortedByDescending { g -> g.items.count { it.dueDate.toLocalDate() < today } }

                    val aging = buildAgingBuckets(all, today)
                    Pair(groups, aging)
                }.fold(
                    onSuccess = { (g, a) -> Result.Success(Pair(g, a)) },
                    onFailure = { Result.Error(ErrorClassifier.classify(it)) }
                )
            }
            when (result) {
                is Result.Success -> _uiState.value = ReceivablesUiState(
                    isLoading = false,
                    groups = result.data.first,
                    aging = result.data.second
                )
                is Result.Error -> {
                    AppLogger.error(result.error)
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.error.userMessage)
                }
                is Result.Validation -> Unit
            }
        }
    }

    private fun buildAgingBuckets(items: List<Transaction>, today: LocalDate): List<AgingBucket> {
        val dueSoon  = mutableListOf<Transaction>()
        val late30   = mutableListOf<Transaction>()
        val late60   = mutableListOf<Transaction>()
        val late61p  = mutableListOf<Transaction>()

        for (t in items) {
            val due = t.dueDate.toLocalDate()
            val days = java.time.temporal.ChronoUnit.DAYS.between(due, today).toInt()
            when {
                days <= 0  -> dueSoon.add(t)
                days <= 30 -> late30.add(t)
                days <= 60 -> late60.add(t)
                else       -> late61p.add(t)
            }
        }

        fun sum(list: List<Transaction>) = list.fold(Money.ZERO) { a, t -> a + t.amount }
        return listOf(
            AgingBucket("A vencer",   dueSoon.size, sum(dueSoon)),
            AgingBucket("1–30 dias",  late30.size,  sum(late30)),
            AgingBucket("31–60 dias", late60.size,  sum(late60)),
            AgingBucket("61+ dias",   late61p.size, sum(late61p))
        )
    }
}
