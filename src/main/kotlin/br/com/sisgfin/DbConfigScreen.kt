package br.com.sisgfin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class ConnectionStatus {
    object Idle : ConnectionStatus()
    object Testing : ConnectionStatus()
    data class Success(val message: String) : ConnectionStatus()
    data class Failure(val message: String) : ConnectionStatus()
}

@Composable
fun DbConfigScreen(
    savedConfig: DbConfig?,
    errorMessage: String?,
    onConnected: () -> Unit
) {
    val initial = savedConfig ?: DbConfig()
    var host     by remember { mutableStateOf(initial.host) }
    var port     by remember { mutableStateOf(initial.port.toString()) }
    var database by remember { mutableStateOf(initial.database) }
    var user     by remember { mutableStateOf(initial.user) }
    var password by remember { mutableStateOf(initial.password) }

    var status by remember {
        mutableStateOf<ConnectionStatus>(
            if (errorMessage != null) ConnectionStatus.Failure(errorMessage)
            else ConnectionStatus.Idle
        )
    }

    val scope = rememberCoroutineScope()

    fun buildConfig() = DbConfig(
        host     = host.trim(),
        port     = port.trim().toIntOrNull() ?: 5432,
        database = database.trim(),
        user     = user.trim(),
        password = password
    )

    fun testConnection(andConnect: Boolean) {
        status = ConnectionStatus.Testing
        scope.launch {
            val config = buildConfig()
            val result = withContext(Dispatchers.IO) { DbConfigStore.testConnection(config) }
            result.fold(
                onSuccess = {
                    if (andConnect) {
                        withContext(Dispatchers.IO) {
                            DbConfigStore.save(config)
                            DatabaseFactory.tryInit(config)
                        }.fold(
                            onSuccess = { onConnected() },
                            onFailure = { e -> status = ConnectionStatus.Failure(e.message ?: "Erro desconhecido") }
                        )
                    } else {
                        status = ConnectionStatus.Success("Conexão bem-sucedida!")
                    }
                },
                onFailure = { e ->
                    val msg = e.message?.substringAfter("Connection to ")?.substringAfter("] ")
                        ?: e.message ?: "Falha na conexão"
                    status = ConnectionStatus.Failure(msg)
                }
            )
        }
    }

    val isTesting = status is ConnectionStatus.Testing
    val canSubmit = host.isNotBlank() && port.isNotBlank() && database.isNotBlank() && user.isNotBlank()

    Box(
        modifier = Modifier.fillMaxSize().background(WsBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Header
            Image(
                painter = painterResource("icon.png"),
                contentDescription = "SisgFin",
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "SisgFin",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = WsTextPrimary
            )
            Text(
                "Configuração do Banco de Dados",
                style = MaterialTheme.typography.bodyMedium,
                color = WsTextSecondary
            )

            Spacer(Modifier.height(24.dp))

            // Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, WsBorder, RoundedCornerShape(10.dp))
                    .background(WsSurface)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Host + Port
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DbField(
                        label = "Servidor",
                        value = host,
                        onValueChange = { host = it },
                        modifier = Modifier.weight(1f),
                        enabled = !isTesting
                    )
                    DbField(
                        label = "Porta",
                        value = port,
                        onValueChange = { port = it },
                        modifier = Modifier.width(100.dp),
                        keyboardType = KeyboardType.Number,
                        enabled = !isTesting
                    )
                }

                DbField(
                    label = "Banco de dados",
                    value = database,
                    onValueChange = { database = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting
                )

                DbField(
                    label = "Usuário",
                    value = user,
                    onValueChange = { user = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting
                )

                DbField(
                    label = "Senha",
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    isPassword = true,
                    enabled = !isTesting
                )

                // Status
                StatusRow(status)

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { testConnection(andConnect = false) },
                        enabled = canSubmit && !isTesting,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WsBorderLight),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WsTextSecondary)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = WsTextSecondary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Testar conexão", fontSize = 13.sp)
                    }

                    Button(
                        onClick = { testConnection(andConnect = true) },
                        enabled = canSubmit && !isTesting,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WsAccent,
                            contentColor   = androidx.compose.ui.graphics.Color.White
                        )
                    ) {
                        Text("Conectar", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Certifique-se que o PostgreSQL está em execução e o banco foi criado.",
                style = MaterialTheme.typography.bodyMedium,
                color = WsTextDisabled
            )
        }
    }
}

@Composable
private fun DbField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = modifier.height(56.dp),
        enabled = enabled,
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else
            androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = WsAccent,
            unfocusedBorderColor = WsBorder,
            focusedLabelColor    = WsAccent,
            unfocusedLabelColor  = WsTextSecondary,
            focusedTextColor     = WsTextPrimary,
            unfocusedTextColor   = WsTextPrimary,
            cursorColor          = WsAccent,
            disabledBorderColor  = WsBorder,
            disabledTextColor    = WsTextDisabled
        ),
        shape = RoundedCornerShape(6.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
    )
}

@Composable
private fun StatusRow(status: ConnectionStatus) {
    when (status) {
        is ConnectionStatus.Idle -> Unit
        is ConnectionStatus.Testing -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WsAccent)
            Text("Conectando...", style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
        }
        is ConnectionStatus.Success -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = WsSuccess, modifier = Modifier.size(18.dp))
            Text(status.message, style = MaterialTheme.typography.bodyMedium, color = WsSuccess)
        }
        is ConnectionStatus.Failure -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(WsDanger.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Error, null, tint = WsDanger, modifier = Modifier.size(18.dp))
            Text(
                status.message,
                style = MaterialTheme.typography.bodyMedium,
                color = WsDanger,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DbLoadingScreen(message: String = "Verificando banco de dados...") {
    Box(
        modifier = Modifier.fillMaxSize().background(WsBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = WsAccent, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = WsTextSecondary)
        }
    }
}
