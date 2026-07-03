package br.com.sisgfin.recurrence

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.Supplier
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.financial.categories.ExpenseCategory
import br.com.sisgfin.financial.categories.ExpenseCategoryRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import br.com.sisgfin.financial.transactions.TransactionType
import br.com.sisgfin.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

data class RecurringUiState(
    val templates: List<RecurrenceTemplate> = emptyList(),
    val selectedTemplate: RecurrenceTemplate? = null,
    val history: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class RecurringViewModel(
    private val service: RecurrenceTemplateService,
    private val accountRepository: FinancialAccountRepository,
    private val supplierRepository: SupplierRepository,
    private val categoryRepository: ExpenseCategoryRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(RecurringUiState())
    val uiState: StateFlow<RecurringUiState> = _uiState.asStateFlow()

    private val _accounts = MutableStateFlow<List<FinancialAccount>>(emptyList())
    val accounts: StateFlow<List<FinancialAccount>> = _accounts.asStateFlow()

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers: StateFlow<List<Supplier>> = _suppliers.asStateFlow()

    private val _categories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val categories: StateFlow<List<ExpenseCategory>> = _categories.asStateFlow()

    init {
        load()
        loadReferenceData()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.IO) { service.listAll() }
            }.onSuccess { list ->
                _uiState.value = _uiState.value.copy(templates = list, isLoading = false)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun select(template: RecurrenceTemplate) {
        _uiState.value = _uiState.value.copy(selectedTemplate = template)
        loadHistory(template.id)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedTemplate = null, history = emptyList())
    }

    fun openNew(type: TransactionType) {
        val account = _accounts.value.firstOrNull()
        _uiState.value = _uiState.value.copy(
            selectedTemplate = RecurrenceTemplate(
                description = "",
                amount      = Money.fromString("0.00"),
                type        = type,
                interval    = RecurrenceInterval.MENSAL,
                dayOfMonth  = LocalDate.now().dayOfMonth,
                accountId   = account?.id ?: 0,
                startsAt    = LocalDateTime.now()
            )
        )
    }

    fun save(template: RecurrenceTemplate) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.IO) { service.save(template) }
            }.onSuccess {
                load()
                _uiState.value = _uiState.value.copy(successMessage = "Recorrência salva com sucesso.")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun pause(id: Int) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { service.pause(id) } }
                .onSuccess { load() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(errorMessage = e.message) }
        }
    }

    fun resume(id: Int) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { service.resume(id) } }
                .onSuccess {
                    load()
                    _uiState.value = _uiState.value.copy(successMessage = "Recorrência reativada. Lançamentos gerados.")
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(errorMessage = e.message) }
        }
    }

    fun cancelFuture(id: Int) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { service.cancelFuture(id) } }
                .onSuccess {
                    load()
                    _uiState.value = _uiState.value.copy(successMessage = "Recorrência pausada e lançamentos futuros cancelados.")
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(errorMessage = e.message) }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    private fun loadHistory(templateId: Int) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { service.getHistory(templateId) }
            }.onSuccess { hist ->
                _uiState.value = _uiState.value.copy(history = hist)
            }
        }
    }

    private fun loadReferenceData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _accounts.value   = accountRepository.findAll().filter { it.isActive }
                _suppliers.value  = supplierRepository.findAll().filter { it.isActive }
                _categories.value = categoryRepository.findAll().filter { it.isActive }
            }
        }
    }
}
