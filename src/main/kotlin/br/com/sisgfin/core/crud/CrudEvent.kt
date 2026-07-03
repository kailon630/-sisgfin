package br.com.sisgfin.core.crud

import br.com.sisgfin.core.errors.AppError

sealed interface CrudEvent {
    data class ShowSnackbar(val message: String, val isError: Boolean = false) : CrudEvent
    data class ShowError(val error: AppError) : CrudEvent
    data object NavigateBack : CrudEvent
    data class ConfirmDialog(
        val title: String,
        val message: String,
        val onConfirm: () -> Unit
    ) : CrudEvent
    data class OperationSuccess(val message: String) : CrudEvent
}
