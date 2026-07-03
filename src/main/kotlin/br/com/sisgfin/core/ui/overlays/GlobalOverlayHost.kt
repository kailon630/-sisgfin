package br.com.sisgfin.core.ui.overlays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf

data class OverlayEntry(
    val id: String,
    val content: @Composable () -> Unit
)

class OverlayRegistry {
    private val _entries = mutableStateListOf<OverlayEntry>()
    val entries: List<OverlayEntry> get() = _entries

    fun show(id: String, content: @Composable () -> Unit) {
        _entries.removeAll { it.id == id }
        _entries.add(OverlayEntry(id, content))
    }

    fun dismiss(id: String) {
        _entries.removeAll { it.id == id }
    }

    fun clear() = _entries.clear()
}

val LocalOverlayRegistry = compositionLocalOf<OverlayRegistry?> { null }

@Composable
fun GlobalOverlayHost(
    registry: OverlayRegistry,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalOverlayRegistry provides registry) {
        content()
        registry.entries.forEach { entry ->
            entry.content()
        }
    }
}
