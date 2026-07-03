package br.com.sisgfin.core.ui.keyboard

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

object KeyboardShortcuts {
    fun isEscape(event: KeyEvent): Boolean =
        event.key == Key.Escape && event.type == KeyEventType.KeyDown

    fun isSave(event: KeyEvent): Boolean =
        event.key == Key.S && event.type == KeyEventType.KeyDown &&
            (event.isCtrlPressed || event.isMetaPressed)

    fun isRefresh(event: KeyEvent): Boolean =
        event.key == Key.F5 && event.type == KeyEventType.KeyDown
}
