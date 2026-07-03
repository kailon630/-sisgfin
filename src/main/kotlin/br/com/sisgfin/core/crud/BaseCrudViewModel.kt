package br.com.sisgfin.core.crud

import br.com.sisgfin.core.domain.Identifiable
import br.com.sisgfin.core.errors.AppLogger
import br.com.sisgfin.core.result.Result
import br.com.sisgfin.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseCrudViewModel<T : Identifiable>(
    private val operations: CrudOperations<T>,
    private val emptyFactory: () -> T,
    private val itemFilter: (T, String) -> Boolean = { _, _ -> true }
) : BaseViewModel() {

    private val _crudState = MutableStateFlow(CrudState<T>())
    val crudState: StateFlow<CrudState<T>> = _crudState.asStateFlow()

    private val _uiState = MutableStateFlow(CrudUiState<T>())
    val uiState: StateFlow<CrudUiState<T>> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CrudEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CrudEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            _crudState.collect { state ->
                _uiState.value = CrudUiState.from(state)
            }
        }
        onAction(CrudAction.Refresh)
    }

    fun onAction(action: CrudAction<T>) {
        when (action) {
            is CrudAction.SelectItem -> selectInternal(action.item)
            CrudAction.OpenPanel -> updateState { it.copy(isPanelOpen = true) }
            CrudAction.ClosePanel -> updateState { it.copy(isPanelOpen = false, isDirty = false) }
            is CrudAction.OpenDialog -> openDialogInternal(action.item)
            CrudAction.CloseDialog -> updateState { it.copy(isDialogVisible = false) }
            is CrudAction.Save -> saveInternal(action.item)
            is CrudAction.ToggleActive -> toggleActiveInternal(action.id)
            is CrudAction.Search -> applySearch(action.query)
            CrudAction.Refresh -> loadInternal()
            is CrudAction.SetDirty -> updateState { it.copy(isDirty = action.isDirty) }
            CrudAction.ClearError -> updateState { it.copy(error = null) }
            is CrudAction.Delete -> toggleActiveInternal(action.id)
        }
    }

    // API legada para telas existentes
    open fun load() = onAction(CrudAction.Refresh)
    fun select(item: T?) = onAction(CrudAction.SelectItem(item))
    fun openDialog(item: T? = null) = onAction(CrudAction.OpenDialog(item))
    fun closeDialog() = onAction(CrudAction.CloseDialog)
    fun save(item: T) = onAction(CrudAction.Save(item))
    fun toggleActive(id: Int) = onAction(CrudAction.ToggleActive(id))
    fun search(query: String) = onAction(CrudAction.Search(query))
    fun clearError() = onAction(CrudAction.ClearError)

    /**
     * Prepara um item vazio para o painel lateral (botão "Novo").
     * Diferente de openDialog(): não seta isDialogVisible=true, portanto não exibe popup.
     * Propaga o estado imediatamente para evitar flicker de um frame.
     */
    fun openNew() {
        updateState { it.copy(selectedItem = emptyFactory(), isDialogVisible = false, isPanelOpen = true) }
        _uiState.value = CrudUiState.from(_crudState.value)
    }

    /**
     * Abre o painel lateral com um item pré-populado específico (sem usar emptyFactory).
     * Útil para pré-selecionar tipo (EXPENSE/INCOME) antes de o usuário ver o formulário.
     */
    protected fun openWithItem(item: T) {
        updateState { it.copy(selectedItem = item, isDialogVisible = false, isPanelOpen = true) }
        _uiState.value = CrudUiState.from(_crudState.value)
    }

    private fun selectInternal(item: T?) {
        updateState { it.copy(selectedItem = item, isPanelOpen = item != null) }
    }

    private fun openDialogInternal(item: T?) {
        updateState {
            it.copy(
                isDialogVisible = true,
                selectedItem = item ?: emptyFactory()
            )
        }
    }

    private fun applySearch(query: String) {
        updateState { state ->
            val filtered = if (query.isBlank()) null
            else state.items.filter { itemFilter(it, query) }
            state.copy(searchQuery = query, filteredItems = filtered)
        }
    }

    private fun loadInternal() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { operations.listAll() }
                    .fold(
                        onSuccess = { Result.Success(it) },
                        onFailure = { Result.Error(br.com.sisgfin.core.errors.ErrorClassifier.classify(it)) }
                    )
            }
            when (result) {
                is Result.Success -> updateState {
                    it.copy(
                        isLoading = false,
                        items = result.data,
                        filteredItems = null
                    ).let { s -> s.copy(filteredItems = filterItems(s)) }
                }
                is Result.Error -> handleError(result.error)
                is Result.Validation -> handleError(result.errorOrNull()!!)
            }
        }
    }

    private fun saveInternal(item: T) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { operations.save(item) }
                    .fold(
                        onSuccess = { Result.Success(Unit) },
                        onFailure = { Result.Error(br.com.sisgfin.core.errors.ErrorClassifier.classify(it)) }
                    )
            }
            when (result) {
                is Result.Success -> {
                    updateState { it.copy(isLoading = false, isDirty = false, isDialogVisible = false) }
                    _events.emit(CrudEvent.OperationSuccess("Registro salvo com sucesso."))
                    onSaveSuccess()
                    loadInternal()
                }
                is Result.Error -> handleError(result.error, restoreLoading = true)
                is Result.Validation -> handleError(result.errorOrNull()!!, restoreLoading = true)
            }
        }
    }

    protected open suspend fun onSaveSuccess() {}
    protected suspend fun emitEvent(event: CrudEvent) = _events.emit(event)

    private fun toggleActiveInternal(id: Int) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { operations.toggleActive(id) }
                    .fold(
                        onSuccess = { Result.Success(Unit) },
                        onFailure = { Result.Error(br.com.sisgfin.core.errors.ErrorClassifier.classify(it)) }
                    )
            }
            when (result) {
                is Result.Success -> {
                    _events.emit(CrudEvent.OperationSuccess("Status atualizado."))
                    loadInternal()
                    onAction(CrudAction.CloseDialog)
                }
                is Result.Error -> handleError(result.error)
                is Result.Validation -> handleError(result.errorOrNull()!!)
            }
        }
    }

    private fun handleError(error: br.com.sisgfin.core.errors.AppError, restoreLoading: Boolean = false) {
        AppLogger.error(error)
        updateState {
            it.copy(
                isLoading = if (restoreLoading) false else it.isLoading,
                error = error
            )
        }
        viewModelScope.launch {
            _events.emit(CrudEvent.ShowError(error))
            _events.emit(CrudEvent.ShowSnackbar(error.userMessage, isError = true))
        }
    }

    private fun filterItems(state: CrudState<T>): List<T>? {
        val q = state.searchQuery
        if (q.isBlank()) return null
        return state.items.filter { itemFilter(it, q) }
    }

    private fun updateState(block: (CrudState<T>) -> CrudState<T>) {
        _crudState.update(block)
    }
}
