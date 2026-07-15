# SisgFin — Retrato Atual do Projeto
> Atualizado em 2026-07-14. Use este arquivo como contexto inicial em novos chats para evitar re-exploração do projeto.

---

## 1. Visão Geral

**SisgFin** é um sistema desktop de gestão financeira para organizações do terceiro setor.

| Item | Valor |
|------|-------|
| **Cliente** | Associação Terapêutica Cannabis Medicinal Flor da Vida |
| **Finalidade** | Substituir planilha `Controle Finan.xlsm` — gestão de convênio público (501.145-0) com conformidade TCESP/AUDESP |
| **Versão** | 1.0.5 (packageVersion no build) |
| **Raiz do projeto** | `/home/kailon/IdeaProjects/SisgFin/` |
| **Banco de dados** | PostgreSQL (via JDBC + Flyway + Exposed ORM) |
| **Build** | Gradle (Kotlin DSL) |

---

## 2. Stack Tecnológica

```
Kotlin 2.0.21
Compose Desktop 1.7.0 + Material3
Exposed ORM 0.56.0 (core, dao, jdbc, java-time)
PostgreSQL 42.7.3 (driver)
Flyway 9.22.3 (migrações)
Koin 4.0.0 (DI — koin-core + koin-compose)
Apache POI 5.3.0 (Excel export)
Apache PDFBox 3.0.3 (PDF export)
Ktor 3.0.3 (REST API embutida — Netty, JWT, Swagger)
BCrypt 0.4 (hashing de senha)
Kotlinx Coroutines 1.9.0
Kotlinx Serialization 1.7.3
JUnit 5.10.2 (testes)
```

---

## 3. Hierarquia de Arquivos (src/main)

```
src/main/
├── kotlin/br/com/sisgfin/
│   ├── Main.kt                        ← entry point; boot sequence + background engines
│   ├── App.kt                         ← composable raiz pós-login
│   ├── MainLayout.kt                  ← shell: Sidebar + TopToolbar + roteamento por Screen
│   ├── Screen.kt                      ← sealed class com todas as telas
│   ├── NavigationState.kt             ← estado de navegação (Koin singleton)
│   ├── Sidebar.kt                     ← sidebar colapsável com grupos e tooltips
│   ├── DashboardScreen.kt             ← dashboard com KPIs reais
│   ├── FinancialAccountsScreen.kt     ← CRUD contas financeiras
│   ├── SupplierManagementScreen.kt    ← CRUD fornecedores
│   ├── EmployeesScreen.kt             ← CRUD funcionários
│   ├── ProjectsManagementScreen.kt    ← CRUD centros de custo (nome legado)
│   ├── UserManagementScreen.kt        ← CRUD usuários
│   ├── LoginScreen.kt                 ← tela de login
│   ├── DbConfigScreen.kt              ← tela de configuração de BD
│   ├── OtherScreens.kt
│   ├── DesktopComponents.kt
│   ├── WsDesignSystem.kt              ← tokens de cor, tipografia, espaçamento
│   ├── WsControls.kt                  ← componentes reutilizáveis (WsTextField, WsDateField…)
│   ├── WsButtonVariants.kt
│   ├── Colors.kt
│   ├── Theme.kt                       ← SisgFinTheme (isDark)
│   │
│   ├── FinancialModels.kt             ← Supplier, FinancialAccount, CostCenter
│   ├── FinancialRepositories.kt       ← FinancialAccountRepository, CostCenterRepository…
│   ├── FinancialServices.kt           ← FinancialAccountService, CostCenterService, SupplierService…
│   ├── Tables.kt                      ← tabelas Exposed: Users, Suppliers, FinancialAccounts, CostCenters (=projects), Employees, Accounts, Transactions (legado)
│   ├── AccountRepository.kt           ← repo legado (tabela accounts)
│   ├── LegacyTransactionRepository.kt ← repo legado (tabela transactions)
│   ├── Employee.kt / EmployeeRepository.kt / EmployeeService.kt
│   ├── User.kt / UserRepository.kt
│   ├── AuthService.kt / SessionManager.kt
│   ├── DatabaseFactory.kt             ← init DB + Flyway + seed
│   ├── DbConfig.kt / DbConfigStore    ← config de conexão persistida em Preferences
│   │
│   ├── accounts/
│   │   └── FinancialAccountViewModel.kt
│   │
│   ├── api/                           ← REST API embutida (Ktor, porta 8080)
│   │   ├── KtorServer.kt
│   │   ├── Dtos.kt
│   │   └── routes/
│   │       ├── AccountRoutes.kt
│   │       ├── AuthRoutes.kt
│   │       ├── CashFlowRoutes.kt
│   │       ├── ReferenceRoutes.kt
│   │       ├── StatementRoutes.kt
│   │       ├── SupplierRoutes.kt
│   │       └── TransactionRoutes.kt
│   │
│   ├── balances/
│   │   ├── BalancePanelViewModel.kt
│   │   └── BalancesScreen.kt          ← painel de saldos (3 tiles + tabela por conta)
│   │
│   ├── budget/
│   │   ├── BudgetItem.kt              ← BudgetItem, BudgetItemSummary, BudgetBalance
│   │   ├── BudgetItemRepository.kt
│   │   ├── BudgetItemService.kt
│   │   ├── BudgetItemsTable.kt
│   │   ├── BudgetScreen.kt
│   │   └── BudgetViewModel.kt
│   │
│   ├── cashflow/
│   │   ├── CashFlowModels.kt          ← DailyCashFlowEntry, CashFlowUiState, SimulationEntry
│   │   ├── CashFlowScreen.kt          ← tabela colorida por saúde + painel simulação
│   │   ├── CashFlowService.kt         ← project() + projectWithSimulation()
│   │   └── CashFlowViewModel.kt
│   │
│   ├── clients/
│   │   ├── ClientsScreen.kt
│   │   └── ClientsViewModel.kt
│   │
│   ├── contracts/
│   │   ├── Contract.kt
│   │   ├── ContractRepository.kt
│   │   ├── ContractService.kt
│   │   ├── ContractsScreen.kt
│   │   ├── ContractsTable.kt
│   │   ├── ContractStatus.kt          ← VIGENTE, ENCERRADO, SUSPENSO, CANCELADO
│   │   └── ContractViewModel.kt
│   │
│   ├── core/
│   │   ├── crud/
│   │   │   ├── BaseCrudViewModel.kt   ← genérico com load/save/toggleActive/search
│   │   │   ├── CrudAction.kt
│   │   │   ├── CrudEvent.kt
│   │   │   ├── CrudOperations.kt
│   │   │   ├── CrudResult.kt
│   │   │   ├── CrudState.kt
│   │   │   └── CrudUiState.kt
│   │   ├── domain/
│   │   │   ├── AuditedCrudService.kt
│   │   │   ├── Identifiable.kt
│   │   │   └── MutableEntityRepository.kt
│   │   ├── errors/
│   │   │   ├── AppError.kt            ← sealed: Operational, Validation, NotFound, Unauthorized, Unexpected
│   │   │   ├── AppLogger.kt
│   │   │   └── ErrorClassifier.kt
│   │   ├── result/
│   │   │   └── Result.kt              ← Success, Error, Validation
│   │   ├── ui/
│   │   │   ├── dialogs/ConfirmDialog.kt
│   │   │   ├── focus/FocusRequesters.kt
│   │   │   ├── keyboard/KeyboardShortcuts.kt
│   │   │   ├── loading/LoadingOverlay.kt
│   │   │   ├── navigation/PanelNavigation.kt
│   │   │   ├── notifications/CrudEventEffects.kt
│   │   │   ├── overlays/GlobalOverlayHost.kt
│   │   │   └── panel/BaseCrudPanel.kt
│   │   └── validation/
│   │       └── DocumentValidator.kt   ← validação CPF/CNPJ (dígitos verificadores)
│   │
│   ├── dashboard/
│   │   └── DashboardViewModel.kt      ← KPIs: saldo, receita/despesa mês, vencidos, a vencer 7d
│   │
│   ├── di/
│   │   ├── AppModules.kt              ← lista dos módulos Koin
│   │   ├── DatabaseModule.kt
│   │   ├── RepositoryModule.kt
│   │   ├── ServiceModule.kt
│   │   └── ViewModelModule.kt
│   │
│   ├── employees/
│   │   ├── EmployeeViewModel.kt
│   │   └── PayrollEngine.kt           ← geração automática de lançamentos mensais por funcionário
│   │
│   ├── financial/
│   │   ├── categories/
│   │   │   ├── CategoriesScreen.kt
│   │   │   ├── ExpenseCategory.kt     ← code, name, groupCode, isIncome
│   │   │   ├── ExpenseCategoriesTable.kt
│   │   │   ├── ExpenseCategoryRepository.kt
│   │   │   ├── ExpenseCategoryService.kt
│   │   │   └── ExpenseCategoryViewModel.kt
│   │   ├── ledger/
│   │   │   └── LedgerService.kt
│   │   ├── money/
│   │   │   ├── Money.kt               ← wrapper BigDecimal imutável
│   │   │   ├── MoneyExtensions.kt
│   │   │   ├── MoneyFormatter.kt
│   │   │   └── RoundingPolicy.kt
│   │   └── transactions/
│   │       ├── Transaction.kt         ← entidade principal do motor transacional
│   │       ├── FinancialTransactionsTable.kt
│   │       ├── TransactionListFilter.kt
│   │       ├── TransactionRepository.kt
│   │       ├── TransactionService.kt
│   │       ├── TransactionsScreen.kt
│   │       ├── TransactionStatus.kt   ← DRAFT, PENDING, PAID, OVERDUE, PARTIAL, CANCELED, SCHEDULED
│   │       ├── TransactionStatusStyle.kt
│   │       ├── TransactionType.kt     ← INCOME, EXPENSE, TRANSFER, ADJUSTMENT, REVERSAL
│   │       ├── TransactionValidator.kt
│   │       ├── TransactionsViewModel.kt
│   │       ├── TransactionDetailsPanel.kt
│   │       ├── timeline/
│   │       │   ├── TimelineEventType.kt
│   │       │   ├── TransactionTimelineEvent.kt
│   │       │   ├── TransactionTimelineRepository.kt
│   │       │   └── TransactionTimelineTable.kt
│   │       └── workflow/
│   │           ├── OverdueEngine.kt           ← PENDING→OVERDUE automático
│   │           ├── TransactionStateMachine.kt ← única fonte de verdade de transições
│   │           ├── TransactionTimeline.kt
│   │           └── TransactionTimelineRepository.kt
│   │
│   ├── payroll/
│   │   ├── PayrollImportModels.kt         ← PayrollRawEntry, PayrollEntry, PayrollImportResult, PayrollImportBatch
│   │   ├── PayrollImportScreen.kt         ← wizard 4 etapas: SelectFile→Preview→Processando→Resultado
│   │   ├── PayrollImportService.kt        ← import() + confirm() + coordenação com PayrollEngine
│   │   ├── PayrollImportUiState.kt        ← estados do wizard
│   │   ├── PayrollImportViewModel.kt      ← CoroutineScope; parseFile, toggleEntry, confirm
│   │   └── PayrollXlsxParser.kt          ← lê .xlsx SCI Ambiente Contábil ÚNICO; extrai matrícula/CPF/adiantamento/líquido
│   │
│   ├── ofx/
│   │   ├── OfxImport.kt
│   │   ├── OfxImportRepository.kt
│   │   ├── OfxImportScreen.kt         ← wizard 4 etapas: SelectFile→Preview→Reconcile→Done
│   │   ├── OfxImportService.kt
│   │   ├── OfxImportsTable.kt
│   │   ├── OfxImportViewModel.kt
│   │   ├── OfxModels.kt               ← OfxStatement, OfxTransaction, OfxTrnType, OfxImportResult
│   │   └── OfxParser.kt               ← parser SGML OFX v102, encoding windows-1252
│   │
│   ├── presentation/
│   │   ├── state/
│   │   │   ├── DashboardUiState.kt
│   │   │   ├── EmployeeUiState.kt
│   │   │   └── LoginUiState.kt
│   │   └── viewmodel/
│   │       ├── BaseViewModel.kt
│   │       └── LoginViewModel.kt
│   │
│   ├── projects/
│   │   └── ProjectViewModel.kt  (alias: CostCenterViewModel)
│   │
│   ├── receivables/
│   │   ├── ReceivablesScreen.kt
│   │   └── ReceivablesViewModel.kt
│   │
│   ├── recurrence/
│   │   ├── RecurrenceDateCalculator.kt
│   │   ├── RecurrenceEngine.kt        ← generateAhead(monthsAhead) + generateForTemplate(id)
│   │   ├── RecurrenceInterval.kt      ← SEMANAL, QUINZENAL, MENSAL, BIMESTRAL, TRIMESTRAL, SEMESTRAL, ANUAL
│   │   ├── RecurrenceTemplate.kt
│   │   ├── RecurrenceTemplateRepository.kt
│   │   ├── RecurrenceTemplateService.kt
│   │   ├── RecurrenceTemplatesTable.kt
│   │   ├── RecurringScreen.kt
│   │   └── RecurringViewModel.kt
│   │
│   ├── reports/
│   │   ├── ReportsExporter.kt         ← PDF (PDFBox) + Excel (POI): Livro Diário, Balancete, Demonstrativo, Comprovante
│   │   ├── ReportsModels.kt
│   │   ├── ReportsScreen.kt           ← 3 abas: Livro Diário / Balancete / Demonstrativo
│   │   └── ReportsViewModel.kt
│   │
│   ├── statement/
│   │   ├── StatementExporter.kt       ← PDF + Excel do extrato
│   │   ├── StatementModels.kt
│   │   ├── StatementScreen.kt
│   │   └── StatementViewModel.kt
│   │
│   ├── suppliers/
│   │   ├── EntityType.kt              ← FORNECEDOR, CLIENTE, AMBOS
│   │   └── SupplierViewModel.kt
│   │
│   └── users/
│       └── UserManagementViewModel.kt
│
└── resources/
    ├── icon.png / icon.ico
    ├── openapi/documentation.yaml
    └── db/migration/
        ├── V1__init.sql               ← users, accounts, transactions (legado)
        ├── V2__employees.sql
        ├── V3__user_management_and_audit.sql
        ├── V4__base_financial_registrations.sql  ← suppliers, financial_accounts, projects
        ├── V5__money_precision.sql
        ├── V6__transaction_engine_foundation.sql ← financial_transactions + índices
        ├── V7__transaction_workflow.sql
        ├── V8__transaction_installment_and_document.sql
        ├── V9__expense_categories.sql
        ├── V10__seed_expense_categories.sql      ← ~100 categorias TCESP
        ├── V11__supplier_document_unique.sql
        ├── V12__rename_user_role_operador.sql
        ├── V13__investment_account.sql
        ├── V14__budget_items.sql
        ├── V15__employee_payment_days.sql        ← paymentDays, employmentType, employee_id em fin_tx
        ├── V16__transaction_ofx_fitid.sql
        ├── V17__ofx_imports.sql
        ├── V18__transaction_reconciled_with.sql
        ├── V19__recurrence_templates.sql
        ├── V20__transaction_recurrence_fk.sql
        ├── V21__contracts.sql
        ├── V22__transaction_contract_fk.sql
        ├── V23__supplier_entity_type.sql
        └── V24__employee_supplier_fk.sql    ← supplier_id nullable em employees
```

---

## 4. Arquitetura em Camadas

```
┌─────────────────────────────────────────────────────────┐
│  UI (Compose Desktop)                                   │
│  *Screen.kt  BaseCrudPanel  WsDesignSystem              │
├─────────────────────────────────────────────────────────┤
│  ViewModel                                              │
│  BaseCrudViewModel<T>  (StateFlow, SharedFlow, Koin)    │
├─────────────────────────────────────────────────────────┤
│  Service                                                │
│  AuditedCrudService  TransactionService  PayrollEngine  │
│  RecurrenceEngine  OfxImportService  CashFlowService    │
├─────────────────────────────────────────────────────────┤
│  Repository                                             │
│  Exposed ORM (DSL/transactions)  PostgreSQL             │
├─────────────────────────────────────────────────────────┤
│  REST API (paralela, Ktor porta 8080)                   │
│  JWT auth  /api/transactions  /api/accounts  /swagger   │
└─────────────────────────────────────────────────────────┘
```

**Padrão DI:** Koin 4 com `single { }` para repositories/services e `factory { }` para ViewModels.

**Resultado padronizado:** `core/result/Result.kt` — `Success<T>`, `Error(AppError)`, `Validation`.

**Erro padronizado:** `core/errors/AppError.kt` — `Operational`, `Validation`, `NotFound`, `Unauthorized`, `Unexpected`.

---

## 5. Entidades de Domínio Principais

### Transaction (motor principal — tabela `financial_transactions`)
```kotlin
data class Transaction(
    id, type: TransactionType, status: TransactionStatus,
    description, amount: Money, issueDate, dueDate,
    paymentDate?, paidAmount?: Money,
    accountId, supplierId?, costCenterId?,
    categoryId?, documentType?, documentNumber?,
    installmentCurrent?, installmentTotal?,
    parentTransactionId?,   // parcelamento e estorno
    recurrenceTemplateId?,  // vínculo com recorrência
    contractId?,            // vínculo com contrato
    ofxFitId?,              // importação OFX
    reconciledWithFitId?,   // conciliação manual
    employeeId?,            // gerado pelo PayrollEngine
    ledgerEntryId?, createdBy?, isActive
)
```

**TransactionStatus:** `DRAFT → PENDING → PAID | OVERDUE → PAID | PARTIAL → PAID | CANCELED | SCHEDULED`

**TransactionType:** `INCOME | EXPENSE | TRANSFER | ADJUSTMENT | REVERSAL`

### Supplier (tabela `suppliers`)
```kotlin
data class Supplier(
    id, document: String (CPF/CNPJ, UNIQUE),
    name, tradeName?, email?, phone?, pixKey?,
    bank?, agency?, account?,
    entityType: EntityType,  // FORNECEDOR | CLIENTE | AMBOS
    isActive, createdBy?
)
```

### FinancialAccount (tabela `financial_accounts`)
```kotlin
data class FinancialAccount(
    id, name, bankName?, agency?, accountNumber?,
    accountType: FinancialAccountType,  // BANK | CASH | SAVINGS | INVESTMENT
    initialBalance: Money,
    investmentBroker?,
    isActive
)
```

### CostCenter — Centro de Custo (tabela `projects` — nome legado mantido no banco)
```kotlin
data class CostCenter(
    id, code, name, description?,
    startDate?, endDate?,
    isActive
) {
    val isEncerrado: Boolean  // !isActive OU endDate já passou
}
```

### ExpenseCategory — Natureza Financeira (tabela `expense_categories`)
```kotlin
data class ExpenseCategory(
    id, code, name, description?,
    groupCode?, groupName?,
    isIncome: Boolean,
    isActive
)
```

### BudgetItem — Rubrica Orçamentária (tabela `budget_items`)
```kotlin
data class BudgetItem(
    id, costCenterId, categoryId, year,
    monthlyAmount: Money, annualAmount: Money,
    notes?, isActive
)
// triângulo: CostCenter × ExpenseCategory × Ano
```

### Contract (tabela `contracts`)
```kotlin
data class Contract(
    id, number, description,
    contractorId,  // FK suppliers
    type: TransactionType,
    totalValue: Money, startDate, endDate?,
    status: ContractStatus,  // VIGENTE | ENCERRADO | SUSPENSO | CANCELADO
    recurrenceTemplateId?
)
```

### RecurrenceTemplate (tabela `recurrence_templates`)
```kotlin
data class RecurrenceTemplate(
    id, description, amount: Money, type: TransactionType,
    interval: RecurrenceInterval,  // SEMANAL..ANUAL
    dayOfMonth, accountId, supplierId?, categoryId?,
    costCenterId?, documentType?,
    startsAt, endsAt?,
    contractId?, isActive
)
```

### Employee (tabela `employees`)
```kotlin
// campos relevantes para auto-geração:
paymentDay: Int        // dia principal de pagamento
paymentDays: String?   // ex: "5,20" — se preenchido, PayrollEngine gera lançamentos
employmentType: String? // CLT | PJ | ESTAGIO | OUTROS
```

---

## 6. Telas / Navegação

Controle de tela em `NavigationState.currentScreen: Screen`.

| Screen | Arquivo | Descrição |
|--------|---------|-----------|
| `Login` | LoginScreen.kt | autenticação |
| `Dashboard` | DashboardScreen.kt | KPIs: saldo, receita/despesa mês, vencidos, a vencer 7d; mini fluxo de caixa |
| `Transactions` | financial/transactions/TransactionsScreen.kt | lançamentos: + Despesa / + Receita / transferência / estorno / conciliação; grupos temporais |
| `Balances` | balances/BalancesScreen.kt | 3 tiles totalizadores + tabela por conta |
| `Statement` | statement/StatementScreen.kt | extrato cronológico com filtros; export XLSX + PDF |
| `Accounts` | FinancialAccountsScreen.kt | CRUD contas bancárias/caixas/poupança/aplicação |
| `Budget` | budget/BudgetScreen.kt | rubricas orçamentárias (CC × Categoria × Ano); saldo em tempo real |
| `Reports` | reports/ReportsScreen.kt | 3 abas: Livro Diário / Balancete / Demonstrativo; export PDF + Excel |
| `CashFlow` | cashflow/CashFlowScreen.kt | fluxo projetado + simulação de impacto |
| `OfxImport` | ofx/OfxImportScreen.kt | wizard OFX 4 etapas: arquivo→preview→conciliação→resultado |
| `Recurring` | recurrence/RecurringScreen.kt | templates de recorrência |
| `Contracts` | contracts/ContractsScreen.kt | contratos com barra de consumo |
| `Clients` | clients/ClientsScreen.kt | fornecedores com entityType=CLIENTE |
| `Receivables` | receivables/ReceivablesScreen.kt | contas a receber com aging (4 tiles: A vencer / 1-30d / 31-60d / 61+d) |
| `PayrollImport` | payroll/PayrollImportScreen.kt | wizard importação folha XLSX (SCI Ambiente Contábil ÚNICO) |
| `Suppliers` | SupplierManagementScreen.kt | CRUD fornecedores |
| `CostCenters` | ProjectsManagementScreen.kt | CRUD centros de custo |
| `Categories` | financial/categories/CategoriesScreen.kt | CRUD naturezas financeiras |
| `Employees` | EmployeesScreen.kt | CRUD funcionários; coluna "PRÓX. PGTO" |
| `UserManagement` | UserManagementScreen.kt | CRUD usuários (ADMIN/OPERADOR) |

**Menu bar principal:**
- Arquivo → Nova Transação, Importar OFX, Importar Folha de Pagamento, Sair
- Financeiro → Dashboard, Movimentações, Contas a Receber, Fluxo de Caixa, Painel de Saldos, Extrato, Contas e Caixas, Orçamento, Contratos, Recorrências, Clientes, Fornecedores
- Relatórios → Relatórios Financeiros

---

## 7. Motor de Negócios — Regras Chave

### TransactionStateMachine (`workflow/TransactionStateMachine.kt`)
```
DRAFT → PENDING, CANCELED, SCHEDULED
SCHEDULED → PENDING, CANCELED
PENDING → PAID, OVERDUE, PARTIAL, CANCELED
OVERDUE → PAID, PARTIAL, CANCELED
PARTIAL → PAID, CANCELED
PAID → (terminal)
CANCELED → (terminal)
```

### Regras de Negócio Implementadas (RN)

| RN | Onde | Descrição |
|----|------|-----------|
| RN-01 | V11 + SupplierService | UNIQUE em suppliers.document |
| RN-02 | TransactionService | bloqueia fornecedor inativo em lançamento |
| RN-03 | DocumentValidator | validação CPF/CNPJ dígitos verificadores |
| RN-04 | FinancialAccountService | saldo = inicial + ΣPAID entradas − ΣPAID saídas + ADJUSTMENT |
| RN-05 | FinancialAccountService | bloqueia inativação de conta com saldo ≠ 0 |
| RN-06 | FinancialAccountType | tipo INVESTMENT + investmentBroker; rendimentos como ADJUSTMENT |
| RN-07 | CostCenterService | bloqueia delete de CC com transações vinculadas |
| RN-08 | TransactionValidator | aviso (não bloqueia) quando despesa fora do período do CC |
| RN-09 | V10 | seed ~100 categorias TCESP |
| RN-10 | ExpenseCategoryService | bloqueia inativação de categoria com transações vinculadas |
| RN-11 | AuditedCrudService | toda escrita registra userId + timestamp |
| RN-12 | SessionManager + Permission | ADMIN acesso total; OPERADOR não confirma pagamento |
| RN-13 | TransactionStateMachine | estados terminais PAID e CANCELED |
| RN-14 | TransactionService.reverseTransaction() | cria REVERSAL com parentTransactionId |
| RN-15 | OverdueEngine | job automático PENDING→OVERDUE em listAll() |
| RN-16 | TransactionValidator | paymentDate >= issueDate |
| RN-17/18/19 | TransactionService | parcelamento: gera filhos mensais; última absorve arredondamento; cancel cascateia |
| RN-20/21 | TransactionService.createTransfer() | par TRANSFER atômico; cancel cascateia par |
| RN-22 | reverseTransaction() | justificativa obrigatória |
| RN-23 | reverseTransaction() | apenas PAID pode ser estornado; bloqueia duplo estorno |
| RN-24 | BudgetItemService | sumRealized = ΣPAID filtrado por CC × Categoria × Ano |
| RN-25 | BudgetBalance | available = annualAmount − realized |
| RN-26 | TransactionDetailsPanel | banner de saldo de rubrica em tempo real |
| RN-27 | TransactionDetailsPanel | aviso de estouro de orçamento (não bloqueia) |
| RN-28 | BudgetItemService | rubrica de CC encerrado é somente leitura |
| RN-29 | ReportsExporter | Livro Diário padrão TCESP: "PAGO A, [CREDOR] CF [DOC]" |
| RN-30 | ReportsExporter | Balancete por rubrica com export PDF + Excel |
| RN-31 | ReportsExporter.receiptPdf() | comprovante individual PAID em PDF |
| RN-32 | audit_logs | imutável via V3 |
| RN-33 | TransactionDetailsPanel | timeline de eventos da transação |

---

## 8. Engines de Background (iniciadas em Main.kt ao boot)

| Engine | Classe | O que faz |
|--------|--------|-----------|
| PayrollEngine | `employees/PayrollEngine.kt` | Gera lançamentos mensais para funcionários com `paymentDays`; roda para mês atual e próximo |
| RecurrenceEngine | `recurrence/RecurrenceEngine.kt` | Gera lançamentos futuros para templates ativos; `generateAhead(2)` |
| Ktor REST API | `api/KtorServer.kt` | Sobe server na porta 8080 com JWT; Swagger em `/swagger` |

---

## 9. Importação OFX

- **Parser:** `ofx/OfxParser.kt` — SGML OFX v102, encoding windows-1252; extrai FITID, TRNTYPE, DTPOSTED, TRNAMT, MEMO, CHECKNUM
- **Deduplicação:** índice único `(account_id, ofx_fitid) WHERE ofx_fitid IS NOT NULL`
- **Fluxo:** Parse → validação ACCTID → por transação: existsByFitId? skip : create(PAID) → log em `ofx_imports`
- **Conciliação:** após importação, detecta PENDING com mesmo valor ±3 dias para conciliação manual; `reconcile()` marca manual como PAID e desativa o OFX

---

## 10. Importação de Folha de Pagamento (XLSX)

> Arquivo de referência: `docs/Espelho e resumo da folha.xlsx` (junho/2026 — 80 funcionários). Gerado pelo SCI Ambiente Contábil ÚNICO.

- **Pré-requisito:** `V24__employee_supplier_fk.sql` — `supplier_id INTEGER NULL` em `employees`; liga funcionário ao seu cadastro de fornecedor para emissão do lançamento
- **Parser:** `payroll/PayrollXlsxParser.kt` — detecta início de bloco por matrícula (col A) + CPF/Função na linha seguinte; extrai adiantamento (código 903, col BH) e líquido (linha "Líquido", col BH); trata férias (dois blocos Líquido = soma), anomalia (> salário base × 3 = aviso + zera), CPF ausente
- **Serviço:** `payroll/PayrollImportService.kt` — `import()` faz parse + lookup CPF → Employee → Supplier; `confirm()` cancela PayrollEngine para o mês e cria lançamentos via `TransactionService.createFromPayrollImport()` (status PENDING, timeline PAYROLL_IMPORT)
- **Coordenação:** `confirm()` chama `TransactionService.cancelPendingPayrollForMonth()` antes de criar — evita duplicatas com os gerados pelo PayrollEngine
- **Wizard:** `payroll/PayrollImportScreen.kt` — 4 etapas: SelectFile (conta + categoria + mês) → Preview (tabela 80 func., checkboxes, badges de aviso/não-encontrado) → Processando (spinner) → Resultado (cards criados/não-localizados/avisos)
- **Testes:** `PayrollXlsxParserTest.kt` — 6 testes: 3 com arquivo real (80 funcionários, Adriana, Fernanda Grandchamp) + 3 sintéticos (anomalia, dois-Líquidos, sem CPF)

---

## 11. REST API (Ktor)

Base: `http://localhost:8080/api`  
Swagger: `http://localhost:8080/swagger`  
Auth: Bearer JWT (obtido em `POST /api/auth/login`)

| Rota | Autenticação |
|------|-------------|
| `POST /api/auth/login` | pública |
| `GET  /api/auth/me` | JWT |
| `GET/POST/PUT/DELETE /api/transactions/*` | JWT |
| `GET/POST/PUT /api/accounts/*` | JWT |
| `GET/POST/PUT /api/suppliers/*` | JWT |
| `GET /api/reference/categories` | JWT |
| `GET /api/reference/costcenters` | JWT |
| `GET /api/cashflow` | JWT |
| `GET /api/statement` | JWT |

---

## 12. Design System (`WsDesignSystem.kt`)

Tokens principais:
- `WsBackground`, `WsSurface`, `WsAccent`, `WsBorder`
- `WsTextPrimary`, `WsTextSecondary (#A0A7B1)`, `WsTextDisabled`
- `WsDanger`, `WsWarning (#E6A817)`, `WsSuccess`, `WsInfo (#58A6FF)`
- `WsMoneyStyle` / `WsMoneyStyleLarge` — monospace, tracking negativo

Componentes reutilizáveis em `WsControls.kt`:
- `WsTextField`, `WsDateField` (máscara dd/MM/yyyy), `WsSelectField`, `WsMoneyField`
- `WsButton`, `WsOutlinedButton`, variantes de `WsButtonVariants.kt`
- `BaseCrudPanel` (painel lateral genérico), `BaseCrudPanel`
- `MoneyText(amount, large, color)` para valores monetários

---

## 13. Testes

```
src/test/kotlin/br/com/sisgfin/
├── core/validation/DocumentValidatorTest.kt     ← 15 testes CPF/CNPJ
├── financial/money/MoneyTest.kt
├── financial/transactions/
│   ├── InstallmentCalculatorTest.kt
│   ├── TransactionValidatorTest.kt
│   └── TransferAndReversalTest.kt
├── financial/transactions/workflow/
│   └── TransactionWorkflowTest.kt
├── ofx/OfxParserTest.kt                        ← 12 testes + arquivo OFX real jan/2026
├── payroll/PayrollXlsxParserTest.kt            ← 6 testes (3 com arquivo real 80 func. + 3 sintéticos)
└── recurrence/RecurrenceEngineTest.kt
```

---

## 14. Estado Atual por Fase

| Fase | Status | Descrição |
|------|--------|-----------|
| Foundation | ✅ | CRUD base, Koin, DB, auth, seed |
| Cadastros | ✅ | Suppliers, Accounts, CostCenters, Employees, Categories, Users |
| Motor transacional | ✅ | StateMachine, parcelamento, estorno, transferência, controle de acesso |
| Saldos e Extrato | ✅ | BalancesScreen, StatementScreen, export PDF+Excel |
| Orçamento | ✅ | BudgetItem, RN-24 a RN-28 |
| Relatórios | ✅ | Livro Diário, Balancete, Demonstrativo, Comprovante (RN-29 a RN-31) |
| Dashboard real | ✅ | KPIs reais com DashboardViewModel |
| Fluxo de Caixa | ✅ | CashFlowScreen + projeção + simulação de impacto |
| Folha automática | ✅ | PayrollEngine + campo paymentDays |
| Importação OFX | ✅ | wizard completo + conciliação manual |
| Recorrência | ✅ | RecurrenceEngine + RecurringScreen + RecurrenceDateCalculator + testes |
| Contratos | ✅ | ContractService + ContractsScreen + barra de consumo |
| Clientes/Recebíveis | ✅ | EntityType/V23 + ClientsScreen + ReceivablesScreen com aging 4 tiles |
| Importação Folha XLSX | ✅ | PayrollXlsxParser + PayrollImportService + wizard 4 etapas; V24 FK employee→supplier |
| Backup automático | ❌ | não implementado |
| Instalador MSI/DEB | ❌ | packaging configurado, mas não gerado |

---

## 15. Terminologia de Domínio

| Termo no Código | Termo para o Cliente | Tabela no Banco |
|-----------------|---------------------|-----------------|
| `CostCenter` | Centro de Custo | `projects` (preservado) |
| `ExpenseCategory` | Natureza Financeira | `expense_categories` |
| `BudgetItem` | Rubrica Orçamentária | `budget_items` |
| `Supplier` | Fornecedor ou Cliente | `suppliers` |
| `FinancialAccount` | Conta / Caixa | `financial_accounts` |
| `Transaction` | Lançamento | `financial_transactions` |

> **Atenção:** a coluna `project_id` em `financial_transactions` corresponde ao `costCenterId` no código Kotlin. O banco preserva o nome antigo.

---

## 16. Documentos de Referência (pasta `docs/`)

| Arquivo | Conteúdo |
|---------|----------|
| `REQUISITOS_SISGFIN.md` | RN-01 a RN-33 completos |
| `ANALISE_PLANILHA_CONTROLE_FINAN.md` | análise da planilha original |
| `AUDITORIA_TECNICA_COMPLETA_SISGFIN.md` | auditoria de aderência técnica |
| `AUDITORIA_REQUISITOS_VS_IMPLEMENTACAO.md` | mapa RN × implementação |
| `KANBAN.md` | backlog completo com status por card |
| `GUIA_USUARIO.md` | manual do operador |
| `FASE_2.5B_RELATORIO_ARQUITETURAL.md` | relatório arquitetural fase 2.5B |
| `FASE_3A.1_RELATORIO_TRANSACIONAL.md` | relatório do motor transacional |
| `FASE_3A.2_RELATORIO_WORKFLOW.md` | relatório do workflow de estados |
| `01–03 2026 Extrato65205011450.ofx` | extratos OFX reais para teste |

---

## 17. Arquivos de Configuração

| Arquivo | Propósito |
|---------|-----------|
| `build.gradle.kts` | dependências e packaging (MSI/DEB) |
| `settings.gradle.kts` | repositórios Maven |
| `gradle.properties` | propriedades do build |
| `docker-compose.yml` | PostgreSQL local para desenvolvimento |
| `run.sh` | script de inicialização rápida |
| `diagnostico_botoes.md` | análise de UX dos botões |

---

*Fim do retrato — atualizar este arquivo ao concluir novas fases.*
