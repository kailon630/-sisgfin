package br.com.sisgfin.users

import br.com.sisgfin.*
import br.com.sisgfin.core.crud.CrudAction
import br.com.sisgfin.core.crud.CrudEvent
import br.com.sisgfin.core.crud.CrudState
import br.com.sisgfin.core.crud.CrudUiState
import br.com.sisgfin.core.errors.AppLogger
import br.com.sisgfin.core.errors.ErrorClassifier
import br.com.sisgfin.core.result.Result
import br.com.sisgfin.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UserManagementUiState(
    val users: List<User> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val selectedUser: User? = null,
    val isLoading: Boolean = false,
    val isPanelOpen: Boolean = false,
    val isDirty: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        fun from(state: CrudState<User>, auditLogs: List<AuditLog>): UserManagementUiState =
            UserManagementUiState(
                users = state.displayItems,
                auditLogs = auditLogs,
                selectedUser = state.selectedItem,
                isLoading = state.isLoading,
                isPanelOpen = state.isPanelOpen,
                isDirty = state.isDirty,
                errorMessage = state.errorMessage
            )
    }
}

class UserManagementViewModel(
    private val service: UserManagementService,
    private val auditRepository: AuditRepository
) : BaseViewModel() {

    private val _crudState = MutableStateFlow(CrudState<User>())
    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    private val _uiState = MutableStateFlow(UserManagementUiState())
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CrudEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CrudEvent> = _events.asSharedFlow()

    init {
        onAction(CrudAction.Refresh)
    }

    fun onAction(action: CrudAction<User>) {
        when (action) {
            is CrudAction.SelectItem -> selectUserInternal(action.item)
            CrudAction.Refresh -> loadUsersInternal()
            is CrudAction.Save -> saveUserInternal(action.item)
            is CrudAction.Search -> updateState { it.copy(searchQuery = action.query) }
            CrudAction.ClearError -> updateState { it.copy(error = null) }
            is CrudAction.SetDirty -> updateState { it.copy(isDirty = action.isDirty) }
            else -> Unit
        }
    }

    fun loadUsers() = onAction(CrudAction.Refresh)

    fun selectUser(user: User?) = onAction(CrudAction.SelectItem(user))

    fun saveUser(user: User) = onAction(CrudAction.Save(user))

    fun setDirty(dirty: Boolean) = onAction(CrudAction.SetDirty(dirty))

    fun resetPassword(userId: Int, newPasswordRaw: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { service.resetPassword(userId, newPasswordRaw) }
                    .fold(
                        onSuccess = { Result.Success(Unit) },
                        onFailure = { Result.Error(ErrorClassifier.classify(it)) }
                    )
            }
            when (result) {
                is Result.Success -> {
                    selectUserInternal(null)
                    _events.emit(CrudEvent.OperationSuccess("Senha redefinida."))
                }
                is Result.Error -> emitError(result.error)
                is Result.Validation -> emitError(result.errorOrNull()!!)
            }
        }
    }

    private fun loadUsersInternal() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { service.listAll() }
                    .fold(
                        onSuccess = { Result.Success(it) },
                        onFailure = { Result.Error(ErrorClassifier.classify(it)) }
                    )
            }
            when (result) {
                is Result.Success -> updateState { it.copy(isLoading = false, items = result.data) }
                is Result.Error -> emitError(result.error, loading = false)
                is Result.Validation -> emitError(result.errorOrNull()!!, loading = false)
            }
            syncUiState()
        }
    }

    private fun selectUserInternal(user: User?) {
        viewModelScope.launch {
            updateState { it.copy(selectedItem = user, isPanelOpen = user != null) }
            _auditLogs.value = emptyList()
            if (user != null) {
                runCatching {
                    withContext(Dispatchers.IO) { auditRepository.findByEntity("USER", user.id) }
                }.onSuccess { _auditLogs.value = it }
            }
            syncUiState()
        }
    }

    private fun saveUserInternal(user: User) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (user.id == 0) service.createUser(user) else service.updateUser(user)
                }.fold(
                    onSuccess = { Result.Success(Unit) },
                    onFailure = { Result.Error(ErrorClassifier.classify(it)) }
                )
            }
            when (result) {
                is Result.Success -> {
                    updateState { it.copy(isLoading = false, isDirty = false) }
                    loadUsersInternal()
                    selectUserInternal(null)
                    _events.emit(CrudEvent.OperationSuccess("Usuário salvo."))
                }
                is Result.Error -> emitError(result.error, loading = false)
                is Result.Validation -> emitError(result.errorOrNull()!!, loading = false)
            }
        }
    }

    private fun emitError(error: br.com.sisgfin.core.errors.AppError, loading: Boolean? = null) {
        AppLogger.error(error)
        updateState {
            it.copy(
                isLoading = loading ?: it.isLoading,
                error = error
            )
        }
        viewModelScope.launch {
            _events.emit(CrudEvent.ShowError(error))
        }
        syncUiState()
    }

    private fun updateState(block: (CrudState<User>) -> CrudState<User>) {
        _crudState.value = block(_crudState.value)
    }

    private fun syncUiState() {
        _uiState.value = UserManagementUiState.from(_crudState.value, _auditLogs.value)
    }
}
