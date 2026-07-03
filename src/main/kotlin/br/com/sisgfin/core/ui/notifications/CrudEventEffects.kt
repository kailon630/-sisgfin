package br.com.sisgfin.core.ui.notifications

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import br.com.sisgfin.core.crud.CrudEvent
import br.com.sisgfin.core.crud.BaseCrudViewModel
import br.com.sisgfin.core.domain.Identifiable
import kotlinx.coroutines.flow.collectLatest

@Composable
fun <T : Identifiable> CrudEventEffects(
    viewModel: BaseCrudViewModel<T>,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    snackbarHost: @Composable () -> Unit = { SnackbarHost(snackbarHostState) }
) {
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is CrudEvent.ShowSnackbar -> snackbarHostState.showSnackbar(
                    message = event.message,
                    withDismissAction = event.isError
                )
                is CrudEvent.ShowError -> snackbarHostState.showSnackbar(
                    message = event.error.userMessage,
                    withDismissAction = true
                )
                is CrudEvent.OperationSuccess -> snackbarHostState.showSnackbar(event.message)
                is CrudEvent.ConfirmDialog,
                CrudEvent.NavigateBack -> Unit
            }
        }
    }
    snackbarHost()
}
