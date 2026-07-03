package br.com.sisgfin.ofx

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.financial.transactions.TransactionService
import br.com.sisgfin.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class OfxImportViewModel(
    private val ofxParser: OfxParser,
    private val ofxImportService: OfxImportService,
    private val ofxImportRepository: OfxImportRepository,
    private val accountRepository: FinancialAccountRepository,
    private val sessionManager: SessionManager,
    private val transactionService: TransactionService
) : BaseViewModel() {

    sealed class Step {
        data object SelectFile : Step()
        data class Preview(
            val file: File,
            val statement: OfxStatement,
            val duplicateCount: Int
        ) : Step()
        /**
         * Etapa de conciliação: exibida quando a importação detecta pares candidatos.
         * [handledIndices] mapeia índice do candidato → true (vinculado) / false (ignorado).
         */
        data class Reconcile(
            val result: OfxImportResult,
            val candidates: List<ConciliationCandidate>,
            val handledIndices: Map<Int, Boolean> = emptyMap()
        ) : Step()
        data class Done(val result: OfxImportResult) : Step()
    }

    private val _step = MutableStateFlow<Step>(Step.SelectFile)
    val step: StateFlow<Step> = _step.asStateFlow()

    private val _accounts = MutableStateFlow<List<FinancialAccount>>(emptyList())
    val accounts: StateFlow<List<FinancialAccount>> = _accounts.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<Int?>(null)
    val selectedAccountId: StateFlow<Int?> = _selectedAccountId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _importHistory = MutableStateFlow<List<OfxImport>>(emptyList())
    val importHistory: StateFlow<List<OfxImport>> = _importHistory.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadAccounts()
        loadHistory()
    }

    fun loadAccounts() {
        viewModelScope.launch {
            val accs = withContext(Dispatchers.IO) { accountRepository.findAll().filter { it.isActive } }
            _accounts.value = accs
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            _importHistory.value = withContext(Dispatchers.IO) { ofxImportRepository.findAll() }
        }
    }

    fun selectFile() {
        viewModelScope.launch(Dispatchers.IO) {
            // FileDialog bloqueia a thread IO até o usuário fechar — comportamento correto
            val dialog = FileDialog(null as Frame?, "Selecionar arquivo OFX", FileDialog.LOAD)
            dialog.file = "*.ofx"
            dialog.isVisible = true
            val dir  = dialog.directory ?: return@launch
            val name = dialog.file      ?: return@launch
            parseFile(File(dir, name))
        }
    }

    fun selectAccount(id: Int?) {
        _selectedAccountId.value = id
        val preview = _step.value as? Step.Preview ?: return
        if (id == null) return
        // Recalcula duplicatas ao trocar de conta
        viewModelScope.launch {
            val fitIds = preview.statement.transactions.map { it.fitId }
            val count  = withContext(Dispatchers.IO) { ofxImportRepository.countDuplicates(id, fitIds) }
            _step.value = preview.copy(duplicateCount = count)
        }
    }

    fun executeImport() {
        val preview   = _step.value as? Step.Preview ?: return
        val accountId = _selectedAccountId.value ?: return
        val userId    = sessionManager.currentUser.value?.id

        viewModelScope.launch {
            _isLoading.value = true
            val result = withContext(Dispatchers.IO) {
                ofxImportService.import(preview.file, accountId, userId)
            }
            _step.value = if (result.hasCandidates) {
                Step.Reconcile(result, result.candidates)
            } else {
                Step.Done(result)
            }
            _isLoading.value = false
            loadHistory()
        }
    }

    fun linkCandidate(index: Int, candidate: ConciliationCandidate) {
        viewModelScope.launch {
            val reconcile = _step.value as? Step.Reconcile ?: return@launch
            _isLoading.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    transactionService.reconcile(
                        manualTxId = candidate.manualTx.id,
                        ofxTxId   = candidate.ofxTxId,
                        ofxFitId  = candidate.ofxTx.fitId
                    )
                }
            }
            val updated = reconcile.handledIndices + (index to true)
            _step.value = reconcile.copy(handledIndices = updated)
            _isLoading.value = false
        }
    }

    fun ignoreCandidate(index: Int) {
        val reconcile = _step.value as? Step.Reconcile ?: return
        val updated = reconcile.handledIndices + (index to false)
        _step.value = reconcile.copy(handledIndices = updated)
    }

    fun finishReconciliation() {
        val reconcile = _step.value as? Step.Reconcile ?: return
        _step.value = Step.Done(reconcile.result)
    }

    fun reset() {
        _step.value       = Step.SelectFile
        _errorMessage.value = null
    }

    private suspend fun parseFile(file: File) {
        withContext(Dispatchers.Main) {
            _isLoading.value    = true
            _errorMessage.value = null
        }

        val parseResult = withContext(Dispatchers.IO) { runCatching { ofxParser.parse(file) } }

        parseResult.onFailure { e ->
            withContext(Dispatchers.Main) {
                _errorMessage.value = "Não foi possível ler o arquivo: ${e.message}"
                _isLoading.value    = false
            }
            return
        }

        val statement = parseResult.getOrThrow()

        // Auto-seleciona conta pelo ACCTID se ainda não houver seleção
        if (_selectedAccountId.value == null) {
            val digitsOfx = statement.acctId.replace(Regex("[^0-9]"), "")
            val match = _accounts.value.firstOrNull { acc ->
                val digitsAcc = (acc.accountNumber ?: "").replace(Regex("[^0-9]"), "")
                digitsAcc.isNotEmpty() &&
                    (digitsOfx.contains(digitsAcc) || digitsAcc.contains(digitsOfx))
            }
            withContext(Dispatchers.Main) { _selectedAccountId.value = match?.id }
        }

        val accountId  = _selectedAccountId.value
        val fitIds     = statement.transactions.map { it.fitId }
        val dupCount   = if (accountId != null) {
            withContext(Dispatchers.IO) { ofxImportRepository.countDuplicates(accountId, fitIds) }
        } else 0

        withContext(Dispatchers.Main) {
            _step.value      = Step.Preview(file, statement, dupCount)
            _isLoading.value = false
        }
    }
}
