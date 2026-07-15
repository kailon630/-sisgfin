package br.com.sisgfin.payroll

import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.Employee
import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.EmployeeService
import br.com.sisgfin.EmploymentType
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.financial.categories.ExpenseCategoryRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.YearMonth

class PayrollImportViewModel(
    private val payrollImportService: PayrollImportService,
    private val accountRepository: FinancialAccountRepository,
    private val categoryRepository: ExpenseCategoryRepository,
    private val costCenterRepository: CostCenterRepository,
    private val sessionManager: SessionManager,
    private val employeeService: EmployeeService,
    private val employeeRepository: EmployeeRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(PayrollImportUiState())
    val uiState: StateFlow<PayrollImportUiState> = _uiState.asStateFlow()

    init {
        loadFormData()
    }

    private fun loadFormData() {
        viewModelScope.launch {
            val accounts = withContext(Dispatchers.IO) {
                accountRepository.findAll().filter { it.isActive }
            }
            val categories = withContext(Dispatchers.IO) {
                categoryRepository.findAllExpenses().filter { it.isActive }
            }
            val costCenters = withContext(Dispatchers.IO) {
                costCenterRepository.findAll()
            }
            _uiState.update { it.copy(accounts = accounts, categories = categories, costCenters = costCenters) }
        }
    }

    // Abre FileDialog e armazena o arquivo selecionado sem parsear ainda
    fun selectFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val dialog = FileDialog(null as Frame?, "Selecionar folha de pagamento XLSX", FileDialog.LOAD)
            dialog.file = "*.xlsx"
            dialog.isVisible = true
            val dir  = dialog.directory ?: return@launch
            val name = dialog.file      ?: return@launch
            val file = File(dir, name)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(selectedFile = file, error = null) }
            }
        }
    }

    // Chamado pelo botão "Avançar" — parseia e enriquece as entradas
    fun advance() {
        val state = _uiState.value
        val file        = state.selectedFile         ?: return
        val accountId   = state.selectedAccountId    ?: return
        val categoryId  = state.selectedCategoryId   ?: return
        val userId      = sessionManager.currentUser.value?.id ?: 0

        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(step = PayrollImportStep.PARSING, isLoading = true, error = null) }
            }

            val parseResult = withContext(Dispatchers.IO) {
                runCatching {
                    payrollImportService.import(
                        file           = file,
                        accountId      = accountId,
                        categoryId     = categoryId,
                        costCenterId   = state.selectedCostCenterId,
                        referenceMonth = state.referenceMonth,
                        userId         = userId
                    )
                }
            }

            withContext(Dispatchers.Main) {
                parseResult
                    .onSuccess { result ->
                        _uiState.update { it.copy(
                            step            = PayrollImportStep.PREVIEW,
                            result          = result,
                            selectedIndices = result.entries.indices.toSet(),
                            isLoading       = false
                        ) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(
                            step      = PayrollImportStep.SELECT_FILE,
                            isLoading = false,
                            error     = "Não foi possível ler o arquivo: ${e.message}"
                        ) }
                    }
            }
        }
    }

    fun toggleEntry(index: Int) {
        _uiState.update { state ->
            val updated = if (index in state.selectedIndices)
                state.selectedIndices - index
            else
                state.selectedIndices + index
            state.copy(selectedIndices = updated)
        }
    }

    fun selectAll() {
        val result = _uiState.value.result ?: return
        _uiState.update { it.copy(selectedIndices = result.entries.indices.toSet()) }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedIndices = emptySet()) }
    }

    fun confirm() {
        val state      = _uiState.value
        val result     = state.result            ?: return
        val accountId  = state.selectedAccountId ?: return
        val categoryId = state.selectedCategoryId ?: return
        val userId     = sessionManager.currentUser.value?.id ?: 0

        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(step = PayrollImportStep.CONFIRMING, isLoading = true) }
            }

            val confirmResult = withContext(Dispatchers.IO) {
                runCatching {
                    // Filtra para processar apenas as entradas selecionadas pelo usuário
                    val filteredResult = result.copy(
                        entries = result.entries.filterIndexed { i, _ -> i in state.selectedIndices }
                    )
                    payrollImportService.confirm(
                        result       = filteredResult,
                        accountId    = accountId,
                        categoryId   = categoryId,
                        costCenterId = state.selectedCostCenterId,
                        userId       = userId
                    )
                }
            }

            withContext(Dispatchers.Main) {
                confirmResult
                    .onSuccess { txCount ->
                        _uiState.update { it.copy(
                            step      = PayrollImportStep.DONE,
                            isLoading = false,
                            summary   = PayrollImportSummary(
                                transactionsCreated = txCount,
                                employeesProcessed  = result.entries
                                    .filterIndexed { i, _ -> i in state.selectedIndices }
                                    .count { it.employeeFound },
                                notFoundCount  = result.notFoundCount,
                                warningCount   = result.warnings.size,
                                notFoundEntries = result.entries.filter { !it.employeeFound }
                            )
                        ) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(
                            step      = PayrollImportStep.PREVIEW,
                            isLoading = false,
                            error     = "Erro ao confirmar importação: ${e.message}"
                        ) }
                    }
            }
        }
    }

    fun setAccount(id: Int?)      { _uiState.update { it.copy(selectedAccountId   = id) } }
    fun setCategory(id: Int?)     { _uiState.update { it.copy(selectedCategoryId  = id) } }
    fun setCostCenter(id: Int?)   { _uiState.update { it.copy(selectedCostCenterId = id) } }
    fun setReferenceMonth(m: YearMonth) { _uiState.update { it.copy(referenceMonth = m) } }

    fun back() {
        _uiState.update { it.copy(step = PayrollImportStep.SELECT_FILE, error = null) }
    }

    fun openRegisterDialog(entry: PayrollEntry) {
        _uiState.update { it.copy(registeringEntry = entry) }
    }

    fun closeRegisterDialog() {
        _uiState.update { it.copy(registeringEntry = null) }
    }

    fun registerEmployee(name: String, cpf: String, role: String, salary: Money, employmentType: EmploymentType?) {
        viewModelScope.launch {
            val employee = Employee(
                name           = name.trim(),
                document       = cpf,
                phone          = "",
                email          = "",
                role           = role.trim(),
                salary         = salary,
                paymentDay     = 1,
                employmentType = employmentType?.label
            )
            withContext(Dispatchers.IO) { employeeService.save(employee) }
            _uiState.update { it.copy(
                registeringEntry = null,
                registeredCpfs   = it.registeredCpfs + cpf
            ) }
        }
    }

    fun exportRemessa(tipo: TipoRemessa) {
        val state = _uiState.value
        val result = state.result ?: return
        val referenceMonth = state.referenceMonth

        viewModelScope.launch {
            _uiState.update { it.copy(exportLoading = true, missingBankData = emptyList()) }

            val (entries, missing) = withContext(Dispatchers.IO) {
                val confirmedEntries = result.entries
                    .filterIndexed { i, _ -> i in state.selectedIndices }
                    .filter { it.employeeFound }

                val filtered = if (tipo == TipoRemessa.ADIANTAMENTO)
                    confirmedEntries.filter { !it.adiantamento.isZero() }
                else confirmedEntries

                val allEmployees = employeeRepository.getAll().associateBy { it.id }

                val remessaEntries = mutableListOf<RemessaEntry>()
                val missingNames = mutableListOf<String>()

                filtered.forEach { entry ->
                    val emp = entry.employeeId?.let { allEmployees[it] }
                    if (emp != null && emp.hasBankingData) {
                        remessaEntries += RemessaEntry(
                            cpf          = emp.document,
                            agency       = emp.formattedAgency ?: "",
                            account      = emp.formattedAccount ?: "",
                            value        = if (tipo == TipoRemessa.ADIANTAMENTO) entry.adiantamento else entry.liquido,
                            employeeName = emp.name
                        )
                    } else {
                        missingNames += entry.nome
                    }
                }
                Pair(remessaEntries, missingNames)
            }

            _uiState.update { it.copy(exportLoading = false, missingBankData = missing) }

            if (entries.isNotEmpty()) {
                val outputDir = File(System.getProperty("user.home"), "Documents/SisgFin")
                runCatching {
                    val file = PayrollBankExporter.export(referenceMonth, tipo, entries, outputDir)
                    withContext(Dispatchers.IO) { Desktop.getDesktop().open(file) }
                }
            }
        }
    }

    fun reset() {
        _uiState.update { state ->
            PayrollImportUiState(
                accounts     = state.accounts,
                categories   = state.categories,
                costCenters  = state.costCenters,
                referenceMonth = YearMonth.now()
            )
        }
    }
}
