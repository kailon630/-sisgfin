package br.com.sisgfin.financial.transactions

import br.com.sisgfin.FinancialAccount
import br.com.sisgfin.FinancialAccountRepository
import br.com.sisgfin.CostCenter
import br.com.sisgfin.CostCenterRepository
import br.com.sisgfin.SessionManager
import br.com.sisgfin.Supplier
import br.com.sisgfin.SupplierRepository
import br.com.sisgfin.budget.BudgetBalance
import br.com.sisgfin.budget.BudgetItemRepository
import br.com.sisgfin.core.crud.BaseCrudViewModel
import br.com.sisgfin.core.errors.ErrorClassifier
import br.com.sisgfin.core.result.Result
import br.com.sisgfin.financial.categories.ExpenseCategory
import br.com.sisgfin.financial.categories.ExpenseCategoryRepository
import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.timeline.TransactionTimelineEvent
import br.com.sisgfin.financial.transactions.workflow.TransactionStateMachine
import br.com.sisgfin.contracts.Contract
import br.com.sisgfin.contracts.ContractRepository
import br.com.sisgfin.contracts.ContractService
import br.com.sisgfin.recurrence.RecurrenceInterval
import br.com.sisgfin.recurrence.RecurrenceTemplate
import br.com.sisgfin.recurrence.RecurrenceTemplateService
import br.com.sisgfin.reports.ReportsExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class TransactionsViewModel(
    private val service: TransactionService,
    private val accountRepository: FinancialAccountRepository,
    private val supplierRepository: SupplierRepository,
    private val costCenterRepository: CostCenterRepository,
    private val categoryRepository: ExpenseCategoryRepository,
    private val sessionManager: SessionManager,
    private val budgetRepository: BudgetItemRepository,
    private val recurrenceTemplateService: RecurrenceTemplateService? = null,
    private val contractService: ContractService? = null
) : BaseCrudViewModel<Transaction>(
    operations = service,
    emptyFactory = {
        Transaction(
            type = TransactionType.EXPENSE,
            status = TransactionStatus.PENDING,
            description = "",
            amount = Money.fromString("0.01"),
            issueDate = LocalDateTime.now(),
            dueDate = LocalDateTime.now().plusDays(7),
            accountId = 0
        )
    },
    itemFilter = { item, query ->
        val q = query.lowercase()
        item.description.lowercase().contains(q) ||
            item.type.displayName.lowercase().contains(q) ||
            item.status.displayName.lowercase().contains(q)
    }
) {
    private val _accounts = MutableStateFlow<List<FinancialAccount>>(emptyList())
    val accounts: StateFlow<List<FinancialAccount>> = _accounts.asStateFlow()

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers: StateFlow<List<Supplier>> = _suppliers.asStateFlow()

    private val _costCenters = MutableStateFlow<List<CostCenter>>(emptyList())
    val costCenters: StateFlow<List<CostCenter>> = _costCenters.asStateFlow()

    private val _categories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val categories: StateFlow<List<ExpenseCategory>> = _categories.asStateFlow()

    private val _listFilter = MutableStateFlow<TransactionListFilter>(TransactionListFilter.ActionRequired)
    val listFilter: StateFlow<TransactionListFilter> = _listFilter.asStateFlow()

    private val _timeline = MutableStateFlow<List<TransactionTimelineEvent>>(emptyList())
    val timeline: StateFlow<List<TransactionTimelineEvent>> = _timeline.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    // Fase 7-B: contratos ativos para o seletor no painel
    private val _contracts = MutableStateFlow<List<Contract>>(emptyList())
    val contracts: StateFlow<List<Contract>> = _contracts.asStateFlow()

    private val _contractWouldExceed = MutableStateFlow(false)
    val contractWouldExceed: StateFlow<Boolean> = _contractWouldExceed.asStateFlow()

    // RN-25/26: saldo de rubrica consultado em tempo real pelo formulário
    private val _budgetBalance = MutableStateFlow<BudgetBalance?>(null)
    val budgetBalance: StateFlow<BudgetBalance?> = _budgetBalance.asStateFlow()

    init {
        // Sincroniza o serviço com o filtro padrão antes que BaseCrudViewModel execute o primeiro load()
        service.applyListFilter(TransactionListFilter.ActionRequired)
        loadReferenceData()
    }

    fun openNewExpense() = openWithItem(emptyTransaction(TransactionType.EXPENSE))
    fun openNewIncome()  = openWithItem(emptyTransaction(TransactionType.INCOME))

    private fun emptyTransaction(type: TransactionType) = Transaction(
        type        = type,
        status      = TransactionStatus.PENDING,
        description = "",
        amount      = Money.fromString("0.01"),
        issueDate   = LocalDateTime.now(),
        dueDate     = LocalDateTime.now().plusDays(7),
        accountId   = 0
    )

    fun loadReferenceData() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Triple(
                        accountRepository.findAll().filter { it.isActive },
                        supplierRepository.findAll().filter { it.isActive },
                        costCenterRepository.findAll().filter { it.isActive }
                    )
                }
            }.onSuccess { (accs, sups, centers) ->
                _accounts.value = accs
                _suppliers.value = sups
                _costCenters.value = centers
            }
            runCatching {
                withContext(Dispatchers.IO) { categoryRepository.findAll() }
            }.onSuccess { _categories.value = it }
            runCatching {
                withContext(Dispatchers.IO) { contractService?.findActive() ?: emptyList() }
            }.onSuccess { _contracts.value = it }
        }
    }

    // Fase 7-B: verifica se o valor excederia o total do contrato
    fun checkContractExceed(contractId: Int?, amount: Money) {
        if (contractId == null || contractService == null) {
            _contractWouldExceed.value = false
            return
        }
        viewModelScope.launch {
            _contractWouldExceed.value = withContext(Dispatchers.IO) {
                contractService.wouldExceedTotal(contractId, amount)
            }
        }
    }

    // RN-26: chamado pelo painel sempre que projeto ou categoria mudam
    fun queryBudgetBalance(costCenterId: Int?, categoryId: Int?) {
        if (costCenterId == null || categoryId == null || costCenterId == 0 || categoryId == 0) {
            _budgetBalance.value = null
            return
        }
        viewModelScope.launch {
            _budgetBalance.value = withContext(Dispatchers.IO) {
                budgetRepository.getAvailableBalance(costCenterId, categoryId, LocalDate.now().year)
            }
        }
    }

    // ── Transferência (RN-20/21) ──────────────────────────────────────────────

    private val _transferDialogVisible = MutableStateFlow(false)
    val transferDialogVisible: StateFlow<Boolean> = _transferDialogVisible.asStateFlow()

    fun openTransferDialog()  { _transferDialogVisible.value = true }
    fun closeTransferDialog() { _transferDialogVisible.value = false }

    fun createTransfer(
        sourceAccountId: Int,
        destinationAccountId: Int,
        amount: Money,
        date: LocalDateTime,
        description: String,
        notes: String? = null
    ) {
        viewModelScope.launch {
            _operationError.value = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    service.createTransfer(
                        sourceAccountId, destinationAccountId, amount, date, description, notes
                    )
                }.fold(
                    onSuccess = { Result.Success(Unit) },
                    onFailure = { Result.Error(ErrorClassifier.classify(it)) }
                )
            }
            when (result) {
                is Result.Success -> { _transferDialogVisible.value = false; load() }
                is Result.Error -> _operationError.value = result.error.userMessage
                is Result.Validation -> _operationError.value = result.errorOrNull()?.userMessage
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    fun selectTransaction(item: Transaction?) {
        select(item)
        item?.let { loadTimeline(it.id) } ?: run { _timeline.value = emptyList() }
    }

    fun loadTimeline(transactionId: Int) {
        viewModelScope.launch {
            _timeline.value = withContext(Dispatchers.IO) { service.getTimeline(transactionId) }
        }
    }

    fun applyQuickFilter(filter: TransactionListFilter) {
        _listFilter.value = filter
        service.applyListFilter(filter)
        load()
    }

    fun applySearchToService(query: String) {
        service.applySearch(query)
        if (query.isNotBlank()) _listFilter.value = TransactionListFilter.All
        search(query)
    }

    fun clearFilters() {
        _listFilter.value = TransactionListFilter.All
        service.clearFilters()
        load()
    }

    fun recordPayment(id: Int, paymentDate: LocalDateTime, paidAmount: Money) {
        runOperation { service.recordPayment(id, paymentDate, paidAmount) }
    }

    fun markAsPaidFull(id: Int, paymentDate: LocalDateTime = LocalDateTime.now()) {
        viewModelScope.launch {
            val tx = withContext(Dispatchers.IO) { service.listAll().find { it.id == id } }
                ?: uiState.value.items.find { it.id == id }
            if (tx != null) recordPayment(id, paymentDate, tx.amount)
        }
    }

    fun cancelTransaction(id: Int) {
        runOperation { service.cancel(id) }
    }

    fun duplicateTransaction(id: Int) {
        runOperation { service.duplicate(id) }
    }

    // RN-31: comprovante PDF para lançamentos PAID
    fun exportReceipt(id: Int) {
        viewModelScope.launch {
            _operationError.value = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val tx = service.listAll().find { it.id == id }
                        ?: uiState.value.items.find { it.id == id }
                        ?: return@withContext null
                    val accs  = _accounts.value
                    val sups  = _suppliers.value
                    val cats  = _categories.value
                    val ccs   = _costCenters.value

                    val supplierName  = tx.supplierId?.let { sups.find { s -> s.id == it }?.name }
                    val accountName   = accs.find { it.id == tx.accountId }?.name ?: "#${tx.accountId}"
                    val cat           = cats.find { it.id == tx.categoryId }
                    val costCenter    = ccs.find { it.id == tx.costCenterId }

                    val dir = File(System.getProperty("user.home"), "Documents/SisgFin")
                    ReportsExporter.receiptPdf(
                        tx            = tx,
                        supplierName  = supplierName,
                        accountName   = accountName,
                        categoryCode  = cat?.code,
                        categoryName  = cat?.name,
                        costCenterName = costCenter?.name,
                        outputDir     = dir
                    )
                }
            }.onSuccess { file ->
                if (file != null) runCatching { Desktop.getDesktop().open(file) }
            }.onFailure { e ->
                _operationError.value = "Erro ao gerar comprovante: ${e.message}"
            }
        }
    }

    // RN-14/22/23
    fun reverseTransaction(id: Int, justification: String) {
        runOperation { service.reverseTransaction(id, justification) }
    }

    // RN-12: ações filtradas pelo perfil do usuário
    fun getAvailableActions(status: TransactionStatus, type: TransactionType): Set<TransactionAction> {
        val canConfirm = service.canConfirmPayment()
        return buildSet {
            if (TransactionStateMachine.allowsPayment(status) && canConfirm) add(TransactionAction.MarkPaid)
            if (TransactionStateMachine.allowsCancel(status)) add(TransactionAction.Cancel)
            if (TransactionStateMachine.allowsDuplicate(status)) add(TransactionAction.Duplicate)
            if (!TransactionStateMachine.isTerminal(status)) add(TransactionAction.Edit)
            // Estorno: somente PAID, não-REVERSAL, e perfil ADMIN
            if (status == TransactionStatus.PAID && type != TransactionType.REVERSAL && canConfirm) {
                add(TransactionAction.Reverse)
            }
        }
    }

    fun saveWithRecurrence(
        item: Transaction,
        recurringInterval: RecurrenceInterval?,
        recurringDayOfMonth: Int?
    ) {
        viewModelScope.launch {
            _operationError.value = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    service.save(item)
                    if (recurringInterval != null && recurringDayOfMonth != null && recurrenceTemplateService != null) {
                        recurrenceTemplateService.save(
                            RecurrenceTemplate(
                                description  = item.description,
                                amount       = item.amount,
                                type         = item.type,
                                interval     = recurringInterval,
                                dayOfMonth   = recurringDayOfMonth,
                                accountId    = item.accountId,
                                supplierId   = item.supplierId,
                                categoryId   = item.categoryId,
                                costCenterId = item.costCenterId,
                                documentType = item.documentType,
                                notes        = item.notes,
                                startsAt     = item.dueDate,
                                contractId   = item.contractId
                            )
                        )
                    }
                }.fold(
                    onSuccess = { Result.Success(Unit) },
                    onFailure = { Result.Error(ErrorClassifier.classify(it)) }
                )
            }
            when (result) {
                is Result.Success -> {
                    load()
                    select(null)
                }
                is Result.Error -> _operationError.value = result.error.userMessage
                is Result.Validation -> _operationError.value = result.errorOrNull()?.userMessage
            }
        }
    }

    private fun runOperation(block: suspend () -> Any?) {
        viewModelScope.launch {
            _operationError.value = null
            val result = withContext(Dispatchers.IO) {
                runCatching { block() }
                    .fold(
                        onSuccess = { Result.Success(Unit) },
                        onFailure = { Result.Error(ErrorClassifier.classify(it)) }
                    )
            }
            when (result) {
                is Result.Success -> {
                    load()
                    uiState.value.selectedItem?.id?.let { loadTimeline(it) }
                }
                is Result.Error -> _operationError.value = result.error.userMessage
                is Result.Validation -> _operationError.value = result.errorOrNull()?.userMessage
            }
        }
    }
}

enum class TransactionAction {
    MarkPaid, Cancel, Duplicate, Edit, OpenDetails, Reverse
}
