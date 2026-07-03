package br.com.sisgfin.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Estado compartilhado para painel contextual direito (workstation layout).
 */
class PanelNavigationState(
    val content: MutableState<(@Composable () -> Unit)?>,
    val onClose: () -> Unit
) {
    fun show(panel: @Composable () -> Unit) {
        content.value = panel
    }

    fun close() {
        content.value = null
        onClose()
    }
}

@Composable
fun rememberPanelNavigation(
    panelContent: MutableState<(@Composable () -> Unit)?>,
    onCloseRightPanel: () -> Unit
): PanelNavigationState = remember(panelContent, onCloseRightPanel) {
    PanelNavigationState(panelContent) { panelContent.value = null; onCloseRightPanel() }
}
