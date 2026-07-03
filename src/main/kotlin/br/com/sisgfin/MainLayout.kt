package br.com.sisgfin

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import br.com.sisgfin.accounts.FinancialAccountViewModel
import br.com.sisgfin.balances.BalancePanelViewModel
import br.com.sisgfin.balances.BalancesScreen
import br.com.sisgfin.budget.BudgetScreen
import br.com.sisgfin.budget.BudgetViewModel
import br.com.sisgfin.cashflow.CashFlowScreen
import br.com.sisgfin.cashflow.CashFlowViewModel
import br.com.sisgfin.reports.ReportsScreen
import br.com.sisgfin.reports.ReportsViewModel
import br.com.sisgfin.statement.StatementScreen
import br.com.sisgfin.statement.StatementViewModel
import br.com.sisgfin.dashboard.DashboardViewModel
import br.com.sisgfin.employees.EmployeeViewModel
import br.com.sisgfin.projects.CostCenterViewModel
import br.com.sisgfin.financial.categories.CategoriesScreen
import br.com.sisgfin.financial.categories.ExpenseCategoryViewModel
import br.com.sisgfin.financial.transactions.TransactionsScreen
import br.com.sisgfin.financial.transactions.TransactionsViewModel
import br.com.sisgfin.ofx.OfxImportScreen
import br.com.sisgfin.ofx.OfxImportViewModel
import br.com.sisgfin.clients.ClientsScreen
import br.com.sisgfin.clients.ClientsViewModel
import br.com.sisgfin.contracts.ContractViewModel
import br.com.sisgfin.contracts.ContractsScreen
import br.com.sisgfin.receivables.ReceivablesScreen
import br.com.sisgfin.receivables.ReceivablesViewModel
import br.com.sisgfin.recurrence.RecurringScreen
import br.com.sisgfin.recurrence.RecurringViewModel
import br.com.sisgfin.suppliers.SupplierViewModel
import br.com.sisgfin.users.UserManagementViewModel
import br.com.sisgfin.core.ui.keyboard.KeyboardShortcuts
import org.jetbrains.skiko.Cursor

import androidx.compose.ui.input.key.*
import org.koin.compose.koinInject

@Composable
fun MainLayout(
    navigationState: NavigationState,
    sessionManager: SessionManager,
    dashboardViewModel: DashboardViewModel,
    employeeViewModel: EmployeeViewModel,
    userManagementViewModel: UserManagementViewModel,
    supplierViewModel: SupplierViewModel,
    financialAccountViewModel: FinancialAccountViewModel,
    costCenterViewModel: CostCenterViewModel,
    transactionsViewModel: TransactionsViewModel,
    balancePanelViewModel: BalancePanelViewModel,
    statementViewModel: StatementViewModel,
    budgetViewModel: BudgetViewModel,
    reportsViewModel: ReportsViewModel,
    cashFlowViewModel: CashFlowViewModel,
    recurringViewModel: RecurringViewModel,
    contractViewModel: ContractViewModel,
    clientsViewModel: ClientsViewModel,
    receivablesViewModel: ReceivablesViewModel,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    // State for the Right Contextual Panel
    var rightPanelContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    // panelWidth is initialized to 0 and set to 50% of the content area on first open
    var panelWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val onShowRightPanel: (@Composable () -> Unit) -> Unit = { rightPanelContent = it }
    val onCloseRightPanel: () -> Unit = { rightPanelContent = null }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(WsBackground)
            .onKeyEvent {
                if (KeyboardShortcuts.isEscape(it) && rightPanelContent != null) {
                    rightPanelContent = null
                    true
                } else false
            }
    ) {
        // 1. Sidebar (Persistent)
        Sidebar(
            currentScreen = navigationState.currentScreen,
            sessionManager = sessionManager,
            onNavigate = {
                navigationState.navigateTo(it)
                rightPanelContent = null
            },
            onLogout = {
                sessionManager.logout()
                navigationState.navigateTo(Screen.Login)
            },
            isDarkTheme   = isDarkTheme,
            onToggleTheme = onToggleTheme
        )

        Column(modifier = Modifier.fillMaxSize().weight(1f)) {
            // 2. Top Toolbar — item 6: passa o nome da tela atual como breadcrumb
            val screenTitle = when (navigationState.currentScreen) {
                is Screen.Dashboard      -> "Painel Financeiro"
                is Screen.CashFlow       -> "Fluxo de Caixa"
                is Screen.Transactions   -> "Movimentações"
                is Screen.Balances       -> "Painel de Saldos"
                is Screen.Statement      -> "Extrato"
                is Screen.Accounts       -> "Contas e Caixas"
                is Screen.Budget         -> "Orçamento"
                is Screen.Suppliers      -> "Fornecedores"
                is Screen.CostCenters    -> "Centros de Custo"
                is Screen.Categories     -> "Plano de Contas"
                is Screen.Employees      -> "Funcionários"
                is Screen.UserManagement -> "Usuários"
                is Screen.Reports        -> "Relatórios"
                is Screen.Settings       -> "Configurações"
                is Screen.OfxImport     -> "Importar OFX"
                is Screen.Recurring     -> "Recorrências"
                is Screen.Contracts     -> "Contratos"
                is Screen.Clients       -> "Clientes"
                is Screen.Receivables   -> "Contas a Receber"
                else                     -> ""
            }
            TopToolbar(
                username    = sessionManager.currentUser.collectAsState().value?.username ?: "Usuário",
                screenTitle = screenTitle
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val contentAreaWidth: Dp = maxWidth
            // Initialize panel to 50% of content area on first open; reset when panel closes
            // Item 4: painel abre em 400dp fixo (mais compacto que 50% da tela)
            LaunchedEffect(rightPanelContent != null) {
                if (rightPanelContent != null && panelWidth < 250.dp) {
                    panelWidth = 400.dp.coerceAtMost(contentAreaWidth * 0.6f)
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // 3. Main Content Area
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    AnimatedContent(
                        targetState = navigationState.currentScreen,
                        transitionSpec = { 
                            (fadeIn() + slideInHorizontally { it / 20 }) togetherWith 
                            (fadeOut() + slideOutHorizontally { -it / 20 })
                        }
                    ) { screen ->
                        when (screen) {
                            is Screen.Dashboard -> DashboardScreen(dashboardViewModel)
                            is Screen.Balances -> BalancesScreen(balancePanelViewModel)
                            is Screen.Statement -> StatementScreen(statementViewModel)
                            is Screen.Accounts -> FinancialAccountsScreen(
                                financialAccountViewModel,
                                onShowRightPanel = { rightPanelContent = it },
                                onCloseRightPanel = { rightPanelContent = null }
                            )
                            is Screen.Transactions -> TransactionsScreen(
                                transactionsViewModel,
                                onShowRightPanel = { rightPanelContent = it },
                                onCloseRightPanel = { rightPanelContent = null }
                            )
                            is Screen.Employees -> EmployeesScreen(
                                employeeViewModel,
                                onShowRightPanel = { rightPanelContent = it },
                                onCloseRightPanel = { rightPanelContent = null }
                            )
                            is Screen.UserManagement -> UserManagementScreen(
                                userManagementViewModel,
                                onShowRightPanel = { rightPanelContent = it },
                                onCloseRightPanel = { rightPanelContent = null }
                            )
                            is Screen.Suppliers -> SupplierManagementScreen(
                                supplierViewModel,
                                onShowRightPanel = { rightPanelContent = it },
                                onCloseRightPanel = { rightPanelContent = null }
                            )
                            is Screen.CostCenters -> CostCentersScreen(
                                costCenterViewModel,
                                onShowRightPanel = { rightPanelContent = it },
                                onCloseRightPanel = { rightPanelContent = null }
                            )
                            is Screen.Categories -> CategoriesScreen(
                                viewModel = koinInject<ExpenseCategoryViewModel>(),
                                onShowRightPanel = onShowRightPanel,
                                onCloseRightPanel = onCloseRightPanel
                            )
                            is Screen.Budget -> BudgetScreen(
                                budgetViewModel,
                                onShowRightPanel = onShowRightPanel,
                                onCloseRightPanel = onCloseRightPanel
                            )
                            is Screen.Reports -> ReportsScreen(reportsViewModel)
                            is Screen.CashFlow -> CashFlowScreen(cashFlowViewModel)
                            is Screen.OfxImport -> OfxImportScreen(
                                viewModel = koinInject<OfxImportViewModel>(),
                                onNavigateToStatement = { navigationState.navigateTo(Screen.Statement) }
                            )
                            is Screen.Recurring -> RecurringScreen(
                                viewModel = recurringViewModel,
                                onShowRightPanel = { rightPanelContent = it },
                                onCloseRightPanel = { rightPanelContent = null }
                            )
                            is Screen.Contracts -> ContractsScreen(
                                viewModel = contractViewModel,
                                onShowRightPanel = { rightPanelContent = it },
                                onCloseRightPanel = { rightPanelContent = null }
                            )
                            is Screen.Clients -> ClientsScreen(
                                viewModel = clientsViewModel,
                                onShowRightPanel = { rightPanelContent = it },
                                onCloseRightPanel = { rightPanelContent = null }
                            )
                            is Screen.Receivables -> ReceivablesScreen(receivablesViewModel)
                            else -> DashboardScreen(dashboardViewModel)
                        }
                    }
                }

                // 4. Right Contextual Panel
                AnimatedVisibility(
                    visible = rightPanelContent != null,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        // Draggable Resizer
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(5.dp)
                                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val delta = dragAmount.x
                                        val deltaDp = with(density) { delta.toDp() }
                                        val newWidth = panelWidth - deltaDp
                                        if (newWidth > 250.dp && newWidth < contentAreaWidth * 0.85f) {
                                            panelWidth = newWidth
                                        }
                                    }
                                }
                                .background(Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            VerticalDivider(color = WsBorder, thickness = 1.dp)
                        }

                        Box(
                            modifier = Modifier
                                .width(panelWidth)
                                .fillMaxHeight()
                                .background(WsSurface)
                        ) {
                            rightPanelContent?.invoke()
                        }
                    }
                }
            }
            } // BoxWithConstraints
        }
    }
}
