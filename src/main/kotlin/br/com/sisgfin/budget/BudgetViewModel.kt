package br.com.sisgfin.budget

import br.com.sisgfin.CostCenter
import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.core.crud.BaseCrudViewModel
import br.com.sisgfin.financial.categories.ExpenseCategory
import br.com.sisgfin.financial.categories.ExpenseCategoryRepository
import br.com.sisgfin.financial.money.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class BudgetViewModel(
    private val service: BudgetItemService,
    private val costCenterRepository: CostCenterRepository,
    private val categoryRepository: ExpenseCategoryRepository
) : BaseCrudViewModel<BudgetItem>(
    operations = service,
    emptyFactory = {
        BudgetItem(
            costCenterId  = 0,
            categoryId    = 0,
            year          = LocalDate.now().year,
            monthlyAmount = Money.ZERO,
            annualAmount  = Money.ZERO
        )
    },
    itemFilter = { _, _ -> true }
) {
    private val _selectedYear = MutableStateFlow(LocalDate.now().year)
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _costCenters = MutableStateFlow<List<CostCenter>>(emptyList())
    val costCenters: StateFlow<List<CostCenter>> = _costCenters.asStateFlow()

    private val _categories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val categories: StateFlow<List<ExpenseCategory>> = _categories.asStateFlow()

    private val _summaries = MutableStateFlow<List<BudgetItemSummary>>(emptyList())
    val summaries: StateFlow<List<BudgetItemSummary>> = _summaries.asStateFlow()

    private val _isLoadingSummaries = MutableStateFlow(false)
    val isLoadingSummaries: StateFlow<Boolean> = _isLoadingSummaries.asStateFlow()

    init {
        loadReferenceData()
    }

    private fun loadReferenceData() {
        viewModelScope.launch {
            val (projs, cats) = withContext(Dispatchers.IO) {
                Pair(
                    costCenterRepository.findAll().filter { it.isActive },
                    categoryRepository.findAll()
                )
            }
            _costCenters.value = projs
            _categories.value = cats
            loadSummaries(_selectedYear.value)
        }
    }

    fun selectYear(year: Int) {
        _selectedYear.value = year
        loadSummaries(year)
    }

    private fun loadSummaries(year: Int) {
        viewModelScope.launch {
            _isLoadingSummaries.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val items = service.findByYear(year)
                    val costCenterMap = costCenterRepository.findAll().associateBy { it.id }
                    val categoryMap = categoryRepository.findAll().associateBy { it.id }

                    items.map { item ->
                        val realized = service.getRealized(item)
                        val balance  = item.annualAmount - realized
                        val pct = if (item.annualAmount.isZero()) 0.0
                                  else realized.value.toDouble() / item.annualAmount.value.toDouble() * 100.0

                        val costCenter = costCenterMap[item.costCenterId]
                        val category = categoryMap[item.categoryId]
                        BudgetItemSummary(
                            item            = item,
                            costCenterName  = costCenter?.name ?: "CC #${item.costCenterId}",
                            categoryCode    = category?.code ?: "",
                            categoryName    = category?.name ?: "Categoria #${item.categoryId}",
                            annualRealized  = realized,
                            annualBalance   = balance,
                            utilizationPct  = pct
                        )
                    }
                }
            }.onSuccess { _summaries.value = it }
             .onFailure { _summaries.value = emptyList() }

            _isLoadingSummaries.value = false
        }
    }

    // Recalcula os summaries após salvar/alterar um item
    override fun load() {
        super.load()
        loadSummaries(_selectedYear.value)
    }
}
