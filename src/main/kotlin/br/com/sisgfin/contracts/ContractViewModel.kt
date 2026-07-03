package br.com.sisgfin.contracts

import br.com.sisgfin.Supplier
import br.com.sisgfin.SupplierRepository
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
import java.math.BigDecimal
import java.time.LocalDateTime

data class ContractUiState(
    val contracts: List<Contract> = emptyList(),
    val selectedContract: Contract? = null,
    val execution: ContractExecution? = null,
    val recentTransactions: List<Transaction> = emptyList(),
    val statusFilter: ContractStatus? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class ContractViewModel(
    private val service: ContractService,
    private val supplierRepository: SupplierRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(ContractUiState())
    val uiState: StateFlow<ContractUiState> = _uiState.asStateFlow()

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers: StateFlow<List<Supplier>> = _suppliers.asStateFlow()

    init {
        load()
        loadSuppliers()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.IO) { service.listAll() }
            }.onSuccess { list ->
                val filtered = _uiState.value.statusFilter?.let { f -> list.filter { it.status == f } } ?: list
                _uiState.value = _uiState.value.copy(contracts = filtered, isLoading = false)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun applyStatusFilter(status: ContractStatus?) {
        _uiState.value = _uiState.value.copy(statusFilter = status)
        load()
    }

    fun select(contract: Contract) {
        _uiState.value = _uiState.value.copy(selectedContract = contract)
        loadExecution(contract.id)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedContract = null,
            execution = null,
            recentTransactions = emptyList()
        )
    }

    fun openNew(type: TransactionType) {
        _uiState.value = _uiState.value.copy(
            selectedContract = Contract(
                number      = "",
                description = "",
                contractorId = 0,
                type        = type,
                totalValue  = Money(BigDecimal.ZERO),
                startDate   = LocalDateTime.now()
            ),
            execution = null,
            recentTransactions = emptyList()
        )
    }

    fun save(contract: Contract) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.IO) { service.save(contract) }
            }.onSuccess {
                load()
                _uiState.value = _uiState.value.copy(
                    successMessage = "Contrato salvo com sucesso.",
                    selectedContract = null
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun updateStatus(id: Int, newStatus: ContractStatus) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { service.updateStatus(id, newStatus) } }
                .onSuccess {
                    load()
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Status atualizado para ${newStatus.displayName}.",
                        selectedContract = null
                    )
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(errorMessage = e.message) }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    private fun loadExecution(contractId: Int) {
        if (contractId == 0) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    service.getExecutionSummary(contractId) to service.getRecentTransactions(contractId)
                }
            }.onSuccess { (exec, txs) ->
                _uiState.value = _uiState.value.copy(execution = exec, recentTransactions = txs)
            }
        }
    }

    private fun loadSuppliers() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { supplierRepository.findAll().filter { it.isActive } } }
                .onSuccess { _suppliers.value = it }
        }
    }
}
