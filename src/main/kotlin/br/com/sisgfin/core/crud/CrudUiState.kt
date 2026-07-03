package br.com.sisgfin.core.crud

/**
 * Projeção de [CrudState] para camada de apresentação (compatibilidade com telas existentes).
 */
data class CrudUiState<T>(
    val items: List<T> = emptyList(),
    val selectedItem: T? = null,
    val isLoading: Boolean = false,
    val isDialogVisible: Boolean = false,
    val isPanelOpen: Boolean = false,
    val searchQuery: String = "",
    val isDirty: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        fun <T> from(state: CrudState<T>): CrudUiState<T> = CrudUiState(
            items = state.displayItems,
            selectedItem = state.selectedItem,
            isLoading = state.isLoading,
            isDialogVisible = state.isDialogVisible,
            isPanelOpen = state.isPanelOpen,
            searchQuery = state.searchQuery,
            isDirty = state.isDirty,
            errorMessage = state.errorMessage
        )
    }
}
