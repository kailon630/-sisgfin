package br.com.sisgfin.reports

import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.budget.BudgetItemRepository
import br.com.sisgfin.financial.categories.ExpenseCategoryRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

class ReportsViewModel(
    private val transactionRepository: TransactionRepository,
    private val supplierRepository: SupplierRepository,
    private val accountRepository: FinancialAccountRepository,
    private val budgetRepository: BudgetItemRepository,
    private val costCenterRepository: CostCenterRepository,
    private val categoryRepository: ExpenseCategoryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(ReportsUiState(isLoading = true))
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            val accounts = withContext(Dispatchers.IO) {
                accountRepository.findAll().filter { it.isActive }
            }
            _uiState.value = _uiState.value.copy(accounts = accounts, isLoading = false)
            applyLivroDiarioFilter(_uiState.value.livroDiarioFilter)
            applyBalanceteFilter(_uiState.value.balanceteFilter)
            applyDemonstrativoFilter(_uiState.value.demonstrativoFilter)
        }
    }

    // ── Livro Diário ─────────────────────────────────────────────────────────

    fun applyLivroDiarioFilter(filter: LivroDiarioFilter) {
        _uiState.value = _uiState.value.copy(livroDiarioFilter = filter, isLoading = true)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val supplierMap = supplierRepository.findAll().associateBy { it.id }
                    val accountMap  = accountRepository.findAll().associateBy { it.id }
                    val txs = transactionRepository.findAllPaid(
                        from      = filter.from,
                        to        = filter.to,
                        accountId = filter.accountId
                    )
                    txs.map { tx ->
                        val supplierName = tx.supplierId?.let { supplierMap[it]?.name }
                        val accountName  = accountMap[tx.accountId]?.name ?: "#${tx.accountId}"
                        LivroDiarioEntry(
                            transaction  = tx,
                            supplierName = supplierName,
                            accountName  = accountName,
                            tcespDesc    = buildTcespDesc(tx, supplierName)
                        )
                    }
                }
            }.onSuccess { entries ->
                _uiState.value = _uiState.value.copy(livroDiarioEntries = entries, isLoading = false, errorMessage = null)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    // ── Balancete ─────────────────────────────────────────────────────────────

    fun applyBalanceteFilter(filter: BalanceteFilter) {
        _uiState.value = _uiState.value.copy(balanceteFilter = filter, isLoading = true)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val costCenterMap = costCenterRepository.findAll().associateBy { it.id }
                    val categoryMap   = categoryRepository.findAll().associateBy { it.id }
                    val items = budgetRepository.findByYear(filter.year)

                    items.map { item ->
                        val realized = if (filter.month != null) {
                            budgetRepository.sumRealizedMonth(
                                item.costCenterId, item.categoryId, filter.year, filter.month
                            )
                        } else {
                            budgetRepository.sumRealized(item.costCenterId, item.categoryId, filter.year)
                        }
                        val dotacao = if (filter.month != null) item.monthlyAmount else item.annualAmount
                        val balance = dotacao - realized
                        val pct     = if (dotacao.isZero()) 0.0
                                      else realized.value.toDouble() / dotacao.value.toDouble() * 100.0

                        val cc  = costCenterMap[item.costCenterId]
                        val cat = categoryMap[item.categoryId]
                        BalanceteRow(
                            costCenterId   = item.costCenterId,
                            costCenterCode = cc?.code  ?: "",
                            costCenterName = cc?.name  ?: "CC #${item.costCenterId}",
                            categoryId     = item.categoryId,
                            categoryCode   = cat?.code ?: "",
                            categoryName   = cat?.name ?: "Cat #${item.categoryId}",
                            monthlyAmount  = item.monthlyAmount,
                            annualAmount   = item.annualAmount,
                            realized       = realized,
                            balance        = balance,
                            utilizationPct = pct
                        )
                    }
                }
            }.onSuccess { rows ->
                _uiState.value = _uiState.value.copy(balanceteRows = rows, isLoading = false, errorMessage = null)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    // ── Demonstrativo Financeiro ─────────────────────────────────────────────

    fun applyDemonstrativoFilter(filter: DemonstrativoFilter) {
        _uiState.value = _uiState.value.copy(demonstrativoFilter = filter, isLoading = true)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val categories = categoryRepository.findAll()
                    val txs = transactionRepository.findAllPaid(from = filter.from, to = filter.to)

                    val incomeByCategory  = mutableMapOf<Int?, Money>()
                    val expenseByCategory = mutableMapOf<Int?, Money>()

                    txs.forEach { tx ->
                        val value = tx.paidAmount ?: tx.amount
                        when (tx.type) {
                            TransactionType.INCOME,
                            TransactionType.REVERSAL,
                            TransactionType.ADJUSTMENT ->
                                incomeByCategory[tx.categoryId] =
                                    (incomeByCategory[tx.categoryId] ?: Money.ZERO) + value
                            TransactionType.EXPENSE ->
                                expenseByCategory[tx.categoryId] =
                                    (expenseByCategory[tx.categoryId] ?: Money.ZERO) + value
                            else -> {}
                        }
                    }

                    val categoryMap = categories.associateBy { it.id }
                    val movedIds = (incomeByCategory.keys + expenseByCategory.keys)
                        .filterNotNull().toSet()

                    movedIds.map { catId ->
                        val cat = categoryMap[catId]
                        DemonstrativoRow(
                            categoryId   = catId,
                            categoryCode = cat?.code ?: "",
                            categoryName = cat?.name ?: "Categoria #$catId",
                            groupCode    = cat?.groupCode,
                            groupName    = cat?.groupName,
                            isIncome     = cat?.isIncome ?: false,
                            income       = incomeByCategory[catId]  ?: Money.ZERO,
                            expense      = expenseByCategory[catId] ?: Money.ZERO
                        )
                    }.sortedWith(compareBy({ it.groupCode ?: "" }, { it.categoryCode }))
                }
            }.onSuccess { rows ->
                _uiState.value = _uiState.value.copy(
                    demonstrativoRows = rows, isLoading = false, errorMessage = null
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    // ── Exports ───────────────────────────────────────────────────────────────

    fun exportLivroDiarioPdf() = exportFile("PDF") { dir ->
        ReportsExporter.livroDiarioPdf(_uiState.value.livroDiarioFilter, _uiState.value.livroDiarioEntries, dir)
    }

    fun exportLivroDiarioExcel() = exportFile("Excel") { dir ->
        ReportsExporter.livroDiarioExcel(_uiState.value.livroDiarioFilter, _uiState.value.livroDiarioEntries, dir)
    }

    fun exportBalancetePdf() = exportFile("PDF") { dir ->
        ReportsExporter.balancetePdf(_uiState.value.balanceteFilter, _uiState.value.balanceteRows, dir)
    }

    fun exportBalanceteExcel() = exportFile("Excel") { dir ->
        ReportsExporter.balanceteExcel(_uiState.value.balanceteFilter, _uiState.value.balanceteRows, dir)
    }

    fun exportDemonstrativoPdf() = exportFile("PDF") { dir ->
        ReportsExporter.demonstrativoPdf(_uiState.value.demonstrativoFilter, _uiState.value.demonstrativoRows, dir)
    }

    fun exportDemonstrativoExcel() = exportFile("Excel") { dir ->
        ReportsExporter.demonstrativoExcel(_uiState.value.demonstrativoFilter, _uiState.value.demonstrativoRows, dir)
    }

    fun clearExportMessage() {
        _uiState.value = _uiState.value.copy(exportMessage = null)
    }

    private fun exportFile(type: String, block: (File) -> File) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(System.getProperty("user.home"), "Documents/SisgFin")
                    block(dir)
                }
            }.onSuccess { file ->
                _uiState.value = _uiState.value.copy(exportMessage = "$type salvo: ${file.absolutePath}")
                runCatching { Desktop.getDesktop().open(file) }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(exportMessage = "Erro ao exportar $type: ${e.message}")
            }
        }
    }
}
