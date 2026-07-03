package br.com.sisgfin.core.crud

sealed interface CrudAction<out T> {
    data class SelectItem<T>(val item: T?) : CrudAction<T>
    data object OpenPanel : CrudAction<Nothing>
    data object ClosePanel : CrudAction<Nothing>
    data class OpenDialog<T>(val item: T? = null) : CrudAction<T>
    data object CloseDialog : CrudAction<Nothing>
    data class Save<T>(val item: T) : CrudAction<T>
    data class Delete<T>(val id: Int) : CrudAction<T>
    data class ToggleActive(val id: Int) : CrudAction<Nothing>
    data class Search(val query: String) : CrudAction<Nothing>
    data object Refresh : CrudAction<Nothing>
    data class SetDirty(val isDirty: Boolean) : CrudAction<Nothing>
    data object ClearError : CrudAction<Nothing>
}
