package br.com.sisgfin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.WsAccent
import br.com.sisgfin.WsFilterChip
import br.com.sisgfin.WsTextSecondary
import br.com.sisgfin.users.UserManagementViewModel
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun UserManagementScreen(
    viewModel: UserManagementViewModel,
    onShowRightPanel: (@Composable () -> Unit) -> Unit,
    onCloseRightPanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Gerenciamento de Usuários", style = MaterialTheme.typography.headlineMedium)
                Text("Controle de acesso, roles e auditoria de sistema", style = MaterialTheme.typography.bodyMedium)
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WsButton(
                    text = "Novo Usuário",
                    icon = Icons.Default.PersonAdd,
                    onClick = { 
                        viewModel.selectUser(User(name = "", username = "", email = "", passwordHash = ""))
                        onShowRightPanel { UserDetailsPanel(viewModel, onCloseRightPanel) }
                    }
                )
                WsIconButton(Icons.Default.Refresh, onClick = { viewModel.loadUsers() })
            }
        }

        // Tabela
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, WsBorder, RoundedCornerShape(8.dp))
                .background(WsSurface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(WsElevated).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableHeaderCell("NOME", Modifier.weight(2f))
                    TableHeaderCell("USERNAME", Modifier.weight(1.5f))
                    TableHeaderCell("ROLE", Modifier.weight(1f))
                    TableHeaderCell("STATUS", Modifier.weight(0.8f), TextAlign.Center)
                    TableHeaderCell("ÚLTIMO LOGIN", Modifier.weight(1.5f), TextAlign.End)
                }
                HorizontalDivider(color = WsBorder)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.users) { user ->
                        UserRow(
                            user = user,
                            isSelected = uiState.selectedUser?.id == user.id,
                            onSingleClick = { 
                                viewModel.selectUser(user)
                                onShowRightPanel { UserDetailsPanel(viewModel, onCloseRightPanel) }
                            },
                            onDoubleClick = {
                                // Double click simulates the old popup logic
                                // (Implementation could be added to UIState if persistent dialog is needed)
                            }
                        )
                        HorizontalDivider(color = WsBorder.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserRow(user: User, isSelected: Boolean, onSingleClick: () -> Unit, onDoubleClick: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val bg = when {
        isSelected -> WsAccent.copy(alpha = 0.1f)
        isHovered -> WsElevated
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier.fillMaxWidth().background(bg).combinedClickable(
            onClick = onSingleClick,
            onDoubleClick = onDoubleClick
        ).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(user.name, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyLarge)
        Text(user.username, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium)
        
        Box(modifier = Modifier.weight(1f)) {
            val color = if (user.role == UserRole.ADMIN) WsAccent else WsTextSecondary
            Text(user.role.name, color = color, style = MaterialTheme.typography.labelMedium)
        }

        Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
            val statusColor = if (user.isActive) WsSuccess else WsTextDisabled
            Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(4.dp)))
        }

        Text(
            text = user.lastLoginAt?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) ?: "Nunca",
            modifier = Modifier.weight(1.5f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun UserDetailsPanel(viewModel: UserManagementViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.selectedUser ?: return

    var name by remember { mutableStateOf(user.name) }
    var username by remember { mutableStateOf(user.username) }
    var email by remember { mutableStateOf(user.email) }
    var role by remember { mutableStateOf(user.role) }
    var isActive by remember { mutableStateOf(user.isActive) }
    
    var showPasswordReset by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        val isWide = maxWidth > 500.dp

        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (user.id == 0) "Novo Usuário" else "Perfil", style = MaterialTheme.typography.titleLarge)
                    Text("Gerenciamento de conta", style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null, tint = WsTextSecondary)
                }
            }
            
            Spacer(Modifier.height(32.dp))

            if (uiState.errorMessage != null) {
                Text(uiState.errorMessage!!, color = WsDanger, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.weight(1f)) {
                WsTextField("NOME COMPLETO", name) { name = it }
                
                if (isWide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        WsTextField("USERNAME", username, Modifier.weight(1f)) { if (user.id == 0) username = it }
                        WsTextField("E-MAIL", email, Modifier.weight(1f)) { email = it }
                    }
                } else {
                    WsTextField("USERNAME", username) { if (user.id == 0) username = it }
                    WsTextField("E-MAIL", email) { email = it }
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PAPEL (ROLE)", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UserRole.values().forEach { r ->
                            WsFilterChip(
                                selected = role == r,
                                onClick = { role = r },
                                label = { Text(r.name) }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor   = WsAccent,
                            checkmarkColor = Color.White,
                            uncheckedColor = WsTextSecondary
                        )
                    )
                    Text("Usuário Ativo", style = MaterialTheme.typography.bodyLarge)
                }

                if (user.id != 0) {
                    HorizontalDivider(color = WsBorder)
                    Text("Segurança", style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp), color = WsTextPrimary)
                    
                    if (!showPasswordReset) {
                        WsButton("Redefinir Senha", onClick = { showPasswordReset = true })
                    } else {
                        WsTextField("NOVA SENHA", newPassword) { newPassword = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WsButton("Confirmar", onClick = {
                                if (newPassword.isNotBlank()) {
                                    viewModel.resetPassword(user.id, newPassword)
                                    showPasswordReset = false
                                }
                            })
                            WsButton("Cancelar", variant = WsButtonVariant.TERTIARY, onClick = { showPasswordReset = false })
                        }
                    }
                } else {
                    WsTextField("SENHA INICIAL", newPassword) { newPassword = it }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            // Auditoria Simples
            if (uiState.auditLogs.isNotEmpty()) {
                Text("Histórico Recente", style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp), color = WsTextPrimary)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(150.dp)) {
                    items(uiState.auditLogs) { log ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("${log.action} em ${log.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}", style = MaterialTheme.typography.bodyMedium, color = WsTextPrimary)
                            if (log.newValue != null) Text("Valor: ${log.newValue}", style = MaterialTheme.typography.labelMedium, color = WsTextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            WsButton(
                text = "Salvar Usuário",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val userToSave = user.copy(
                        name = name,
                        username = username,
                        email = email,
                        role = role,
                        isActive = isActive,
                        passwordHash = if (user.id == 0) org.mindrot.jbcrypt.BCrypt.hashpw(newPassword, org.mindrot.jbcrypt.BCrypt.gensalt()) else user.passwordHash
                    )
                    viewModel.saveUser(userToSave)
                }
            )
        }
    }
}
