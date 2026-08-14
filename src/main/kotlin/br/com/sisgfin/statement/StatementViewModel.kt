package br.com.sisgfin.statement

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.CostCenter
import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.financial.categories.ExpenseCategory
import br.com.sisgfin.financial.categories.ExpenseCategoryRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.projects.Project
import br.com.sisgfin.financial.projects.ProjectRepository
import br.com.sisgfin.financial.transactions.CounterpartyMap
import br.com.sisgfin.financial.transactions.CounterpartyResolver
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
import java.time.LocalDate

data class StatementUiState(
    val accounts: List<FinancialAccount> = emptyList(),
    val costCenters: List<CostCenter> = emptyList(),
    val categories: List<ExpenseCategory> = emptyList(),
    val projects: List<Project> = emptyList(),
    val filter: StatementFilter = StatementFilter(),
    val openingBalance: Money = Money.ZERO,
    val entries: List<StatementEntry> = emptyList(),
    val counterpartyMap: CounterpartyMap = CounterpartyMap.EMPTY,
    val isLoading: Boolean = false,
    val exportMessage: String? = null,
    val errorMessage: String? = null
)

class StatementViewModel(
    private val accountRepository: FinancialAccountRepository,
    private val transactionRepository: TransactionRepository,
    private val costCenterRepository: CostCenterRepository,
    private val categoryRepository: ExpenseCategoryRepository,
    private val counterpartyResolver: CounterpartyResolver,
    private val projectRepository: ProjectRepository? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(StatementUiState(isLoading = true))
    val uiState: StateFlow<StatementUiState> = _uiState.asStateFlow()

    init {
        loadReferenceData()
    }

    private fun loadReferenceData() {
        scope.launch {
            val (accounts, costCenters, categories) = withContext(Dispatchers.IO) {
                Triple(
                    accountRepository.findAll().filter { it.isActive },
                    costCenterRepository.findAll().filter { it.isActive },
                    categoryRepository.findAll()
                )
            }
            val projects = withContext(Dispatchers.IO) {
                projectRepository?.findAllActive() ?: emptyList()
            }
            val defaultFilter = StatementFilter(
                accountId = accounts.firstOrNull()?.id,
                from = LocalDate.now().withDayOfMonth(1),
                to = LocalDate.now()
            )
            _uiState.value = _uiState.value.copy(
                accounts = accounts,
                costCenters = costCenters,
                categories = categories,
                projects = projects,
                filter = defaultFilter,
                isLoading = false
            )
            loadStatement(defaultFilter, accounts)
        }
    }

    fun applyFilter(filter: StatementFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        loadStatement(filter, _uiState.value.accounts)
    }

    private fun loadStatement(filter: StatementFilter, accounts: List<FinancialAccount>) {
        val accountId = filter.accountId ?: return
        val account = accounts.find { it.id == accountId } ?: return

        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    val opening = if (filter.from != null) {
                        transactionRepository.openingBalance(account.initialBalance, accountId, filter.from)
                    } else {
                        account.initialBalance
                    }

                    val raw = transactionRepository.findStatementEntries(
                        accountId = accountId,
                        from = filter.from,
                        to = filter.to,
                        type = filter.type,
                        costCenterId = filter.costCenterId,
                        categoryId = filter.categoryId,
                        projectId = filter.projectId
                    )

                    val counterpartyMap = counterpartyResolver.resolve(raw)
                    var running = opening
                    val entries = raw.map { tx ->
                        val signed = signedAmount(tx)
                        running = running + signed
                        StatementEntry(tx, signed, running)
                    }
                    Triple(opening, entries, counterpartyMap)
                }
            }.onSuccess { (opening, entries, counterpartyMap) ->
                _uiState.value = _uiState.value.copy(
                    openingBalance = opening,
                    entries = entries,
                    counterpartyMap = counterpartyMap,
                    isLoading = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun exportExcel() {
        val state = _uiState.value
        val accountId = state.filter.accountId ?: return
        val account = state.accounts.find { it.id == accountId } ?: return

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val outDir = File(System.getProperty("user.home"), "Documents/SisgFin")
                    StatementExporter.exportToExcel(account, state.filter, state.openingBalance, state.entries, outDir, state.counterpartyMap)
                }
            }.onSuccess { file ->
                _uiState.value = _uiState.value.copy(exportMessage = "Excel salvo: ${file.absolutePath}")
                runCatching { Desktop.getDesktop().open(file) }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(exportMessage = "Erro ao exportar Excel: ${e.message}")
            }
        }
    }

    fun exportPdf() {
        val state = _uiState.value
        val accountId = state.filter.accountId ?: return
        val account = state.accounts.find { it.id == accountId } ?: return

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val outDir = File(System.getProperty("user.home"), "Documents/SisgFin")
                    StatementExporter.exportToPdf(account, state.filter, state.openingBalance, state.entries, outDir)
                }
            }.onSuccess { file ->
                _uiState.value = _uiState.value.copy(exportMessage = "PDF salvo: ${file.absolutePath}")
                runCatching { Desktop.getDesktop().open(file) }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(exportMessage = "Erro ao exportar PDF: ${e.message}")
            }
        }
    }

    fun clearExportMessage() {
        _uiState.value = _uiState.value.copy(exportMessage = null)
    }
}
