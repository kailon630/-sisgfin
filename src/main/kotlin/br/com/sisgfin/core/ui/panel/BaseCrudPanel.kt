package br.com.sisgfin.core.ui.panel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.sisgfin.WsButton
import br.com.sisgfin.WsButtonVariant
import br.com.sisgfin.WsTextSecondary
import br.com.sisgfin.core.ui.loading.LoadingOverlay

@Composable
fun BaseCrudPanel(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    isLoading: Boolean = false,
    isDirty: Boolean = false,
    errorMessage: String? = null,
    saveLabel: String = "Salvar Alterações",
    onSave: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    showFooter: Boolean = onSave != null,
    modifier: Modifier = Modifier,
    toolbar: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth > 500.dp
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleLarge)
                            if (isDirty) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "• alterações não salvas",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        toolbar?.invoke(this)
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar painel", tint = WsTextSecondary)
                        }
                    }
                }

                errorMessage?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            msg,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(Modifier.height(if (isWide) 32.dp else 24.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    content = content
                )

                if (showFooter && onSave != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        onCancel?.let { cancel ->
                            WsButton(
                                text = "Cancelar",
                                variant = WsButtonVariant.SECONDARY,
                                modifier = Modifier.weight(1f),
                                onClick = cancel
                            )
                        }
                        WsButton(
                            text = saveLabel,
                            modifier = Modifier.weight(if (onCancel != null) 1f else 1f).fillMaxWidth(),
                            onClick = onSave
                        )
                    }
                }
            }
            LoadingOverlay(visible = isLoading)
        }
    }
}
