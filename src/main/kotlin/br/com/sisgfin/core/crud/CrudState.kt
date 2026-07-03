package br.com.sisgfin.core.crud

import br.com.sisgfin.core.errors.AppError

/**
 * Estado persistente da UI CRUD (não inclui efeitos transitórios).
 */
data class CrudState<T>(
    val items: List<T> = emptyList(),
    val filteredItems: List<T>? = null,
    val selectedItem: T? = null,
    val isLoading: Boolean = false,
    val isPanelOpen: Boolean = false,
    val isDialogVisible: Boolean = false,
    val searchQuery: String = "",
    val isDirty: Boolean = false,
    val error: AppError? = null
) {
    val displayItems: List<T> get() = filteredItems ?: items
    val errorMessage: String? get() = error?.userMessage
}
