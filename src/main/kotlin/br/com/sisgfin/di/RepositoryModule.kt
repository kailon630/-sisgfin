package br.com.sisgfin.di

import br.com.sisgfin.*
import br.com.sisgfin.financial.categories.ExpenseCategoryRepository
import br.com.sisgfin.budget.BudgetItemRepository
import br.com.sisgfin.ofx.OfxImportRepository
import br.com.sisgfin.contracts.ContractRepository
import br.com.sisgfin.recurrence.RecurrenceTemplateRepository
import org.koin.dsl.module

val repositoryModule = module {
    single { UserRepository() }
    single { AuditRepository() }
    single { AccountRepository() }
    single { LegacyTransactionRepository() }
    single { br.com.sisgfin.financial.transactions.TransactionRepository() }
    single { EmployeeRepository() }
    single { SupplierRepository() }
    single { FinancialAccountRepository() }
    single { CostCenterRepository() }
    single { ExpenseCategoryRepository() }
    single { BudgetItemRepository() }
    single { OfxImportRepository() }
    single { RecurrenceTemplateRepository() }
    single { ContractRepository() }
}
