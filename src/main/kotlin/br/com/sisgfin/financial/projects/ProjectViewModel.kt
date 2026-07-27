package br.com.sisgfin.financial.projects

import br.com.sisgfin.core.crud.BaseCrudViewModel
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectViewModel(
    service: ProjectService,
    private val transactionRepository: TransactionRepository
) : BaseCrudViewModel<Project>(
    operations  = service,
    emptyFactory = { Project(code = "", name = "") },
    itemFilter   = { item, query ->
        val q = query.lowercase()
        item.name.lowercase().contains(q) || item.code.lowercase().contains(q)
    }
) {
    private val _realizedMap = MutableStateFlow<Map<Int, Money>>(emptyMap())
    val realizedMap: StateFlow<Map<Int, Money>> = _realizedMap.asStateFlow()

    override fun load() {
        super.load()
        refreshRealized()
    }

    override suspend fun onSaveSuccess() {
        refreshRealized()
    }

    fun refreshRealized(projectIds: List<Int>? = null) {
        viewModelScope.launch {
            val ids = projectIds ?: uiState.value.items.map { it.id }
            if (ids.isEmpty()) return@launch
            val map = withContext(Dispatchers.IO) {
                ids.associateWith { pid -> transactionRepository.sumRealizedByProject(pid) }
            }
            _realizedMap.value = map
        }
    }
}
