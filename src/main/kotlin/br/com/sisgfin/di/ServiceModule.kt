package br.com.sisgfin.di

import br.com.sisgfin.*
import br.com.sisgfin.cashflow.CashFlowService
import br.com.sisgfin.employees.PayrollEngine
import br.com.sisgfin.financial.categories.ExpenseCategoryService
import br.com.sisgfin.budget.BudgetItemService
import br.com.sisgfin.ofx.OfxImportService
import br.com.sisgfin.ofx.OfxParser
import br.com.sisgfin.contracts.ContractService
import br.com.sisgfin.payroll.PayrollImportService
import br.com.sisgfin.recurrence.RecurrenceEngine
import br.com.sisgfin.recurrence.RecurrenceTemplateService
import org.koin.dsl.module

val serviceModule = module {
    single { SessionManager(get()) }
    single { AuthService(get(), get()) }
    single { UserManagementService(get(), get(), get()) }
    single { EmployeeService(get(), get()) }
    single { SupplierService(get(), get(), get()) }
    single { FinancialAccountService(get(), get(), get(), get()) }
    single { CostCenterService(get(), get(), get(), get()) }
    single { ExpenseCategoryService(get(), get(), get(), get()) }
    single { BudgetItemService(get(), get(), get(), get()) }
    single { CashFlowService(get(), get(), get()) }
    single { PayrollEngine(get(), get(), get(), get()) }
    single { br.com.sisgfin.financial.ledger.LedgerService() }
    single { br.com.sisgfin.financial.transactions.timeline.TransactionTimelineRepository() }
    single {
        br.com.sisgfin.financial.transactions.TransactionService(
            get(), // TransactionRepository
            get(), // FinancialAccountRepository
            get(), // SupplierRepository
            get(), // CostCenterRepository
            get(), // AuditRepository
            get(), // TransactionTimelineRepository
            get(), // SessionManager
            get()  // LedgerService
        )
    }
    single { OfxParser() }
    single {
        OfxImportService(
            get(), // OfxParser
            get(), // TransactionService
            get(), // OfxImportRepository
            get()  // FinancialAccountRepository
        )
    }
    single {
        RecurrenceEngine(
            get(), // RecurrenceTemplateRepository
            get(), // TransactionRepository
            get()  // TransactionService
        )
    }
    single {
        ContractService(
            get(), // ContractRepository
            get(), // TransactionRepository
            get()  // SessionManager
        )
    }
    single {
        PayrollImportService(
            get(), // EmployeeRepository
            get()  // TransactionService
        )
    }
    single {
        RecurrenceTemplateService(
            get(), // RecurrenceTemplateRepository
            get(), // TransactionRepository
            get(), // RecurrenceEngine
            get()  // SessionManager
        )
    }
}
