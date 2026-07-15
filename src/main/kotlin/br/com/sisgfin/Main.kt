package br.com.sisgfin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import br.com.sisgfin.api.API_PORT
import br.com.sisgfin.api.createKtorServer
import br.com.sisgfin.cashflow.CashFlowService
import br.com.sisgfin.di.appModules
import br.com.sisgfin.employees.PayrollEngine
import br.com.sisgfin.recurrence.RecurrenceEngine
import br.com.sisgfin.CostCenterService
import br.com.sisgfin.financial.categories.ExpenseCategoryService
import br.com.sisgfin.financial.transactions.TransactionRepository
import br.com.sisgfin.financial.transactions.TransactionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin
import java.time.YearMonth
import java.util.prefs.Preferences

private sealed class StartupState {
    object Loading : StartupState()
    data class NeedsDbConfig(val savedConfig: DbConfig?, val errorMessage: String?) : StartupState()
    object Ready : StartupState()
}

private val prefs: Preferences = Preferences.userRoot().node("sisgfin/ui")

fun main() = application {
    var startupState by remember { mutableStateOf<StartupState>(StartupState.Loading) }
    var koinReady    by remember { mutableStateOf(false) }
    val isDark = prefs.getBoolean("darkTheme", true)

    // Janela de configuração/carregamento (visível até o banco estar pronto)
    if (startupState !is StartupState.Ready || !koinReady) {
        Window(
            onCloseRequest = ::exitApplication,
            title     = "SisgFin",
            icon      = painterResource("icon.png"),
            resizable = false,
            state     = rememberWindowState(
                width    = 560.dp,
                height   = 640.dp,
                position = WindowPosition.Aligned(Alignment.Center)
            )
        ) {
            SisgFinTheme(isDark = isDark) {
                when (val state = startupState) {
                    is StartupState.Loading -> {
                        DbLoadingScreen()
                        LaunchedEffect(Unit) {
                            val config = withContext(Dispatchers.IO) { DbConfigStore.load() }
                            if (config == null) {
                                startupState = StartupState.NeedsDbConfig(null, null)
                                return@LaunchedEffect
                            }
                            val result = withContext(Dispatchers.IO) { DatabaseFactory.tryInit(config) }
                            startupState = if (result.isSuccess) {
                                StartupState.Ready
                            } else {
                                StartupState.NeedsDbConfig(config, result.exceptionOrNull()?.message)
                            }
                        }
                    }

                    is StartupState.NeedsDbConfig -> {
                        DbConfigScreen(
                            savedConfig  = state.savedConfig,
                            errorMessage = state.errorMessage,
                            onConnected  = { startupState = StartupState.Ready }
                        )
                    }

                    is StartupState.Ready -> {
                        DbLoadingScreen("Iniciando sistema...")
                        LaunchedEffect(Unit) {
                            startKoin { modules(appModules) }
                            koinReady = true
                            launchBackgroundEngines()
                        }
                    }
                }
            }
        }
    }

    // Janela principal — só aparece quando Koin está pronto
    if (koinReady) {
        val windowState = rememberWindowState(
            width    = 1280.dp,
            height   = 720.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = "SisgFin - Finance Workstation",
            icon  = painterResource("icon.png"),
            state = windowState
        ) {
            val nav = remember { getKoin().get<NavigationState>() }
            var showAbout by remember { mutableStateOf(false) }

            SisgFinTheme(isDark = isDark) {
                MenuBar {
                    Menu("Arquivo") {
                        Item("Nova Transação",       onClick = { nav.navigateTo(Screen.Transactions) })
                        Item("Importar Extrato OFX",           onClick = { nav.navigateTo(Screen.OfxImport) })
                        Item("Importar Folha de Pagamento",   onClick = { nav.navigateTo(Screen.PayrollImport) })
                        Separator()
                        Item("Sair", onClick = { exitApplication() })
                    }
                    Menu("Financeiro") {
                        Item("Dashboard",        onClick = { nav.navigateTo(Screen.Dashboard) })
                        Item("Movimentações",    onClick = { nav.navigateTo(Screen.Transactions) })
                        Item("Contas a Receber", onClick = { nav.navigateTo(Screen.Receivables) })
                        Item("Fluxo de Caixa",  onClick = { nav.navigateTo(Screen.CashFlow) })
                        Item("Painel de Saldos", onClick = { nav.navigateTo(Screen.Balances) })
                        Item("Extrato",          onClick = { nav.navigateTo(Screen.Statement) })
                        Item("Contas e Caixas",  onClick = { nav.navigateTo(Screen.Accounts) })
                        Separator()
                        Item("Orçamento",        onClick = { nav.navigateTo(Screen.Budget) })
                        Item("Contratos",        onClick = { nav.navigateTo(Screen.Contracts) })
                        Item("Recorrências",     onClick = { nav.navigateTo(Screen.Recurring) })
                        Separator()
                        Item("Clientes",         onClick = { nav.navigateTo(Screen.Clients) })
                        Item("Fornecedores",     onClick = { nav.navigateTo(Screen.Suppliers) })
                    }
                    Menu("Relatórios") {
                        Item("Relatórios Financeiros", onClick = { nav.navigateTo(Screen.Reports) })
                    }
                    Menu("Ajuda") {
                        Item("Sobre o SisgFin", onClick = { showAbout = true })
                    }
                }

                App()

                if (showAbout) {
                    DialogWindow(
                        onCloseRequest = { showAbout = false },
                        title     = "Sobre o SisgFin",
                        resizable = false,
                        state     = rememberDialogState(width = 400.dp, height = 280.dp)
                    ) {
                        SisgFinTheme(isDark = isDark) {
                            AboutDialog(onDismiss = { showAbout = false })
                        }
                    }
                }
            }
        }
    }
}

private fun launchBackgroundEngines() {
    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            val payrollEngine = getKoin().get<PayrollEngine>()
            val now = YearMonth.now()
            payrollEngine.generateForMonth(now)
            payrollEngine.generateForMonth(now.plusMonths(1))
        }
    }

    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            getKoin().get<RecurrenceEngine>().generateAhead(monthsAhead = 2)
        }
    }

    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            createKtorServer(
                authService           = getKoin().get(),
                transactionService    = getKoin().get<TransactionService>(),
                accountService        = getKoin().get(),
                supplierService       = getKoin().get(),
                categoryService       = getKoin().get<ExpenseCategoryService>(),
                costCenterService     = getKoin().get<CostCenterService>(),
                cashFlowService       = getKoin().get<CashFlowService>(),
                transactionRepository = getKoin().get<TransactionRepository>(),
                accountRepository     = getKoin().get(),
                userRepository        = getKoin().get(),
                sessionManager        = getKoin().get()
            ).start(wait = false)
        }.onSuccess {
            println("SisgFin REST API disponível em http://localhost:$API_PORT/api")
            println("Swagger UI: http://localhost:$API_PORT/swagger")
        }.onFailure { e ->
            println("Falha ao iniciar API REST: ${e.message}")
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = WsBackground) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource("icon.png"),
                contentDescription = "SisgFin",
                modifier = Modifier.size(80.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text       = "SisgFin",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = WsAccent
            )
            Text(
                text  = "Sistema de Gestão Financeira para OSCs",
                style = MaterialTheme.typography.bodyLarge,
                color = WsTextPrimary
            )
            Text(
                text  = "Versão 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = WsTextSecondary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = WsBorder)
            Text(
                text      = "Conformidade TCESP / AUDESP",
                style     = MaterialTheme.typography.bodySmall,
                color     = WsTextDisabled,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = onDismiss) { Text("Fechar") }
        }
    }
}
