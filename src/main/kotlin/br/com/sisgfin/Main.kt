package br.com.sisgfin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import br.com.sisgfin.engine.EngineOrchestrator
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
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.prefs.Preferences

private sealed class StartupState {
    object Loading    : StartupState()
    data class NeedsDbConfig(val savedConfig: DbConfig?, val errorMessage: String?) : StartupState()
    object Connected  : StartupState()  // conexão OK, migrações ainda não rodaram
    data class MigrationFailed(val detail: String, val logPath: String) : StartupState()
    object Ready      : StartupState()
}

private object StartupLogger {
    private val logFile get() = File(System.getProperty("user.home"), ".sisgfin/startup.log")
    val path: String get() = logFile.absolutePath

    fun log(message: String) = append("[${LocalDateTime.now()}] $message")

    fun logException(context: String, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        append("[${LocalDateTime.now()}] $context\n$sw")
    }

    private fun append(text: String) = runCatching {
        logFile.parentFile.mkdirs()
        logFile.appendText("$text\n")
    }
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
                            StartupLogger.log("Iniciando SisgFin")
                            val config = withContext(Dispatchers.IO) { DbConfigStore.load() }
                            if (config == null) {
                                startupState = StartupState.NeedsDbConfig(null, null)
                                return@LaunchedEffect
                            }
                            StartupLogger.log("Conectando a ${config.host}:${config.port}/${config.database}")
                            val connectResult = withContext(Dispatchers.IO) { DatabaseFactory.tryConnect(config) }
                            if (connectResult.isFailure) {
                                val msg = connectResult.exceptionOrNull()?.message
                                StartupLogger.log("Falha de conexão: $msg")
                                startupState = StartupState.NeedsDbConfig(config, msg)
                                return@LaunchedEffect
                            }
                            startupState = StartupState.Connected
                        }
                    }

                    is StartupState.NeedsDbConfig -> {
                        DbConfigScreen(
                            savedConfig  = state.savedConfig,
                            errorMessage = state.errorMessage,
                            onConnected  = { startupState = StartupState.Connected }
                        )
                    }

                    is StartupState.Connected -> {
                        DbLoadingScreen("Verificando banco de dados...")
                        LaunchedEffect(Unit) {
                            StartupLogger.log("Executando migrações Flyway")
                            val migrateResult = withContext(Dispatchers.IO) { DatabaseFactory.runMigrations() }
                            if (migrateResult.isFailure) {
                                val e = migrateResult.exceptionOrNull()!!
                                StartupLogger.logException("Migração falhou — sistema não iniciou", e)
                                startupState = StartupState.MigrationFailed(
                                    detail  = e.message ?: "Erro desconhecido",
                                    logPath = StartupLogger.path
                                )
                                return@LaunchedEffect
                            }
                            StartupLogger.log("Migrações OK — sistema pronto")
                            startupState = StartupState.Ready
                        }
                    }

                    is StartupState.MigrationFailed -> {
                        MigrationFailedScreen(detail = state.detail, logPath = state.logPath, onExit = ::exitApplication)
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
    val orchestrator = getKoin().get<EngineOrchestrator>()
    val now = YearMonth.now()

    CoroutineScope(Dispatchers.IO).launch {
        orchestrator.runPayrollForMonth(now)
        orchestrator.runPayrollForMonth(now.plusMonths(1))
    }

    CoroutineScope(Dispatchers.IO).launch {
        orchestrator.runRecurrence(monthsAhead = 2)
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
                paymentRepository     = getKoin().get(),
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
private fun MigrationFailedScreen(detail: String, logPath: String, onExit: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = WsBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text       = "Migração de banco falhou",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = WsDanger,
                textAlign  = TextAlign.Center
            )
            Text(
                text      = "O banco de dados está em versão inconsistente com o sistema. O aplicativo não pode iniciar.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = WsTextSecondary,
                textAlign = TextAlign.Center
            )
            HorizontalDivider(color = WsDanger.copy(alpha = 0.4f))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text  = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = WsTextPrimary
                )
            }
            HorizontalDivider(color = WsBorder)
            Text(
                text      = "Log completo: $logPath",
                style     = MaterialTheme.typography.labelSmall,
                color     = WsTextDisabled,
                textAlign = TextAlign.Center
            )
            Text(
                text      = "Contate o suporte com o arquivo acima.",
                style     = MaterialTheme.typography.labelSmall,
                color     = WsTextDisabled,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onExit,
                colors  = ButtonDefaults.buttonColors(containerColor = WsDanger)
            ) {
                Text("Fechar aplicativo")
            }
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
