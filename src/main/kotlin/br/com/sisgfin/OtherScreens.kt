package br.com.sisgfin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccountsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Contas Bancárias", style = MaterialTheme.typography.headlineMedium)
        Text("Gerencie suas contas e saldos", style = MaterialTheme.typography.bodyMedium)
    }
}

