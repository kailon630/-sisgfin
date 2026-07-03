package br.com.sisgfin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.sisgfin.presentation.state.LoginUiState
import br.com.sisgfin.presentation.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (User) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WsBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(360.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Image(
                painter = painterResource("icon.png"),
                contentDescription = "SisgFin",
                modifier = Modifier.size(72.dp),
                contentScale = ContentScale.Fit
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                text = "SisgFin Workstation",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Entre com suas credenciais para continuar.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(40.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WsTextField(
                    label = "NOME DE USUÁRIO",
                    value = username,
                    onValueChange = { username = it }
                )

                WsTextField(
                    label = "SENHA OPERACIONAL",
                    value = password,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    onValueChange = { password = it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState is LoginUiState.Error) {
                Text(
                    text = (uiState as LoginUiState.Error).message,
                    color = WsDanger,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            WsButton(
                text = "Acessar Workstation",
                modifier = Modifier.fillMaxWidth().height(44.dp),
                onClick = { viewModel.login(username, password, onLoginSuccess) }
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "SisgFin v1.1.0 • Finance Intelligence",
                style = MaterialTheme.typography.labelMedium,
                color = WsTextDisabled
            )
        }
    }
}
