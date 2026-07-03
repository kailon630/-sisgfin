package br.com.sisgfin.di

import br.com.sisgfin.accounts.FinancialAccountViewModel
import br.com.sisgfin.balances.BalancePanelViewModel
import br.com.sisgfin.budget.BudgetViewModel
import br.com.sisgfin.cashflow.CashFlowViewModel
import br.com.sisgfin.dashboard.DashboardViewModel
import br.com.sisgfin.employees.EmployeeViewModel
import br.com.sisgfin.financial.categories.ExpenseCategoryViewModel
import br.com.sisgfin.financial.transactions.TransactionsViewModel
import br.com.sisgfin.ofx.OfxImportViewModel
import br.com.sisgfin.presentation.viewmodel.LoginViewModel
import br.com.sisgfin.projects.CostCenterViewModel
import br.com.sisgfin.clients.ClientsViewModel
import br.com.sisgfin.contracts.ContractViewModel
import br.com.sisgfin.receivables.ReceivablesViewModel
import br.com.sisgfin.recurrence.RecurringViewModel
import br.com.sisgfin.reports.ReportsViewModel
import br.com.sisgfin.statement.StatementViewModel
import br.com.sisgfin.suppliers.SupplierViewModel
import br.com.sisgfin.users.UserManagementViewModel
import org.koin.dsl.module

val viewModelModule = module {
    factory { LoginViewModel(get()) }
    factory { DashboardViewModel(get(), get(), get(), get()) }
    factory { EmployeeViewModel(get(), get()) }
    factory { UserManagementViewModel(get(), get()) }
    factory { SupplierViewModel(get()) }
    factory { FinancialAccountViewModel(get()) }
    factory { CostCenterViewModel(get()) }
    factory { ExpenseCategoryViewModel(get()) }
    factory { TransactionsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { ContractViewModel(get(), get()) }
    factory { RecurringViewModel(get(), get(), get(), get()) }
    factory { BalancePanelViewModel(get(), get(), get()) }
    factory { StatementViewModel(get(), get(), get(), get()) }
    factory { BudgetViewModel(get(), get(), get()) }
    factory { ReportsViewModel(get(), get(), get(), get(), get(), get()) }
    factory { CashFlowViewModel(get(), get(), get()) }
    factory { OfxImportViewModel(get(), get(), get(), get(), get(), get()) }
    factory { ClientsViewModel(get(), get()) }
    factory { ReceivablesViewModel(get(), get()) }
}
