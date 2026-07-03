package br.com.sisgfin

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.sisgfin.accounts.FinancialAccountViewModel
import br.com.sisgfin.balances.BalancePanelViewModel
import br.com.sisgfin.budget.BudgetViewModel
import br.com.sisgfin.cashflow.CashFlowViewModel
import br.com.sisgfin.clients.ClientsViewModel
import br.com.sisgfin.contracts.ContractViewModel
import br.com.sisgfin.receivables.ReceivablesViewModel
import br.com.sisgfin.recurrence.RecurringViewModel
import br.com.sisgfin.reports.ReportsViewModel
import br.com.sisgfin.statement.StatementViewModel
import br.com.sisgfin.dashboard.DashboardViewModel
import br.com.sisgfin.employees.EmployeeViewModel
import br.com.sisgfin.presentation.viewmodel.LoginViewModel
import br.com.sisgfin.financial.transactions.TransactionsViewModel
import br.com.sisgfin.projects.CostCenterViewModel
import br.com.sisgfin.suppliers.SupplierViewModel
import br.com.sisgfin.users.UserManagementViewModel
import org.koin.compose.koinInject
import java.util.prefs.Preferences

private val appPrefs: Preferences = Preferences.userRoot().node("sisgfin/ui")

@Composable
fun App() {
    var isDarkTheme by remember { mutableStateOf(appPrefs.getBoolean("darkTheme", true)) }

    val sessionManager: SessionManager = koinInject()
    val navigationState: NavigationState = koinInject()
    val loginViewModel: LoginViewModel = koinInject()
    val dashboardViewModel: DashboardViewModel = koinInject()
    val employeeViewModel: EmployeeViewModel = koinInject()
    val userManagementViewModel: UserManagementViewModel = koinInject()
    val supplierViewModel: SupplierViewModel = koinInject()
    val financialAccountViewModel: FinancialAccountViewModel = koinInject()
    val costCenterViewModel: CostCenterViewModel = koinInject()
    val transactionsViewModel: TransactionsViewModel = koinInject()
    val balancePanelViewModel: BalancePanelViewModel = koinInject()
    val statementViewModel: StatementViewModel = koinInject()
    val budgetViewModel: BudgetViewModel = koinInject()
    val reportsViewModel: ReportsViewModel = koinInject()
    val cashFlowViewModel: CashFlowViewModel = koinInject()
    val recurringViewModel: RecurringViewModel = koinInject()
    val contractViewModel: ContractViewModel = koinInject()
    val clientsViewModel: ClientsViewModel = koinInject()
    val receivablesViewModel: ReceivablesViewModel = koinInject()

    SisgFinTheme(isDark = isDarkTheme) {
        AnimatedContent(
            targetState = navigationState.currentScreen,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }
        ) { screen ->
            if (screen is Screen.Login) {
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = { user ->
                        sessionManager.login(user)
                        navigationState.navigateTo(Screen.Dashboard)
                        loginViewModel.resetState()
                    }
                )
            } else {
                MainLayout(
                    navigationState = navigationState,
                    sessionManager = sessionManager,
                    dashboardViewModel = dashboardViewModel,
                    employeeViewModel = employeeViewModel,
                    userManagementViewModel = userManagementViewModel,
                    supplierViewModel = supplierViewModel,
                    financialAccountViewModel = financialAccountViewModel,
                    costCenterViewModel = costCenterViewModel,
                    transactionsViewModel = transactionsViewModel,
                    balancePanelViewModel = balancePanelViewModel,
                    statementViewModel = statementViewModel,
                    budgetViewModel = budgetViewModel,
                    reportsViewModel = reportsViewModel,
                    cashFlowViewModel = cashFlowViewModel,
                    recurringViewModel = recurringViewModel,
                    contractViewModel = contractViewModel,
                    clientsViewModel = clientsViewModel,
                    receivablesViewModel = receivablesViewModel,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        isDarkTheme = !isDarkTheme
                        appPrefs.putBoolean("darkTheme", isDarkTheme)
                    }
                )
            }
        }
    }
}
