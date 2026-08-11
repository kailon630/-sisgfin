# R8 — Credor no Livro Diário e vínculo Funcionário↔Fornecedor

**Data:** 2026-08-11  
**Escopo:** varredura read-only — nenhum arquivo alterado  
**Motivação:** V24 criou `employees.supplier_id`; V26 removeu a coluna. RN-29 exige `"PAGO A, [CREDOR] CF [DOC]"`. Lançamentos de folha têm `employee_id` preenchido e `supplier_id` NULL. Levantamento para determinar se o credor sai corretamente.

---

## PARTE 1 — O CounterpartyResolver

### 1.1 Arquivo inteiro

`src/main/kotlin/br/com/sisgfin/financial/transactions/CounterpartyResolver.kt`

```kotlin
package br.com.sisgfin.financial.transactions

import br.com.sisgfin.EmployeeRepository
import br.com.sisgfin.SupplierRepository

/**
 * F5 — Batch resolution of supplier/employee names for a transaction list.
 * Single DB round-trip per entity type; callers never make N+1 lookups.
 */
class CounterpartyMap(
    private val suppliers: Map<Int, String>,
    private val employees: Map<Int, String>
) {
    fun nameFor(tx: Transaction): String? =
        tx.supplierId?.let { suppliers[it] }
            ?: tx.employeeId?.let { employees[it] }

    companion object {
        val EMPTY = CounterpartyMap(emptyMap(), emptyMap())
    }
}

class CounterpartyResolver(
    private val supplierRepository: SupplierRepository,
    private val employeeRepository: EmployeeRepository
) {
    fun resolve(transactions: List<Transaction>): CounterpartyMap {
        val supplierIds = transactions.mapNotNull { it.supplierId }.toSet()
        val employeeIds = transactions.mapNotNull { it.employeeId }.toSet()
        val suppliers = if (supplierIds.isNotEmpty())
            supplierRepository.findAll().associate { it.id to it.name }
        else emptyMap()
        val employees = if (employeeIds.isNotEmpty())
            employeeRepository.getAll().associate { it.id to it.name }
        else emptyMap()
        return CounterpartyMap(suppliers, employees)
    }
}
```

### 1.2 Garantias travadas por teste (CounterpartyResolverTest.kt — 7 testes)

`src/test/kotlin/br/com/sisgfin/payables/CounterpartyResolverTest.kt`

| Teste | Garantia |
|---|---|
| `nameFor retorna nome do fornecedor quando supplierId presente` | `supplierId=5` → `"Fornecedor ABC"` |
| `nameFor retorna nome do funcionario quando apenas employeeId presente` | `employeeId=3`, `supplierId=null` → `"João Silva"` |
| `nameFor prefere supplierId quando ambos presentes` | ambos preenchidos → fornecedor vence |
| `nameFor retorna null quando ids ausentes` | ambos `null` → `null` |
| `nameFor retorna null quando id nao encontrado no mapa` | `supplierId=99` não no mapa → `null` |
| `EMPTY retorna null para qualquer transacao` | `CounterpartyMap.EMPTY.nameFor(...)` → `null` |
| `mapa com multiplos fornecedores resolve corretamente cada id` | 3 fornecedores; id fora do mapa → `null` |

### 1.3 Pontos de uso em produção

| Arquivo:linha | Contexto |
|---|---|
| `di/ServiceModule.kt:19` | `single { CounterpartyResolver(get(), get()) }` — registro DI |
| `payables/PayablesViewModel.kt:68` | campo `counterpartyResolver: CounterpartyResolver` no construtor |
| `payables/PayablesViewModel.kt:83` | `counterpartyResolver.resolve(all)` — chamado em `load()` |
| `financial/transactions/TransactionListComponents.kt:94` | `counterpartyName = counterparties.nameFor(item)` — path plano |
| `financial/transactions/TransactionListComponents.kt:144` | `counterpartyName = counterparties.nameFor(item)` — path agrupado |

`CounterpartyResolver` não aparece em `ReportsViewModel`, `ReportsExporter`, `StatementExporter`, `StatementViewModel`, `TransactionsViewModel` nem `TransactionsScreen`.

### 1.4 Como o resolver recebe dados

Recebe mapa pré-carregado montado por `resolve()` com single round-trip por tipo. Quem monta: `PayablesViewModel.load()` (`payables/PayablesViewModel.kt:83`):

```kotlin
Triple(all, counterpartyResolver.resolve(all), buildSummary(all))
```

Consulta `supplierRepository.findAll()` + `employeeRepository.getAll()` — todos os registros, sem pré-filtragem por IDs referenciados.

### 1.5 Introdução no git

```
58a0e45 fix: correções críticas P0-1 a P0-5, módulo pagamentos e 191 testes
```

Único commit contendo o arquivo.

---

## PARTE 2 — Livro Diário (RN-29) — PERGUNTA CENTRAL

### 2.1 Funções que geram o Livro Diário

Ambas em `src/main/kotlin/br/com/sisgfin/reports/ReportsExporter.kt`.

**Excel** — `livroDiarioExcel()` (linhas 30–99). Coluna HISTÓRICO (TCESP) via `entry.tcespDesc` (linha 79):
```kotlin
row.createCell(2).setCellValue(entry.tcespDesc)
```

**PDF** — `livroDiarioPdf()` (linhas 103–191). Mesma fonte (linha 167):
```kotlin
val desc = entry.tcespDesc.take(55)
textAt(cs, desc, cols[2], y, fontReg, 7f)
```

Ambas recebem `LivroDiarioEntry.tcespDesc` pré-computado. Não constroem a string internamente.

### 2.2 Expressão literal que produz o texto do CREDOR

`src/main/kotlin/br/com/sisgfin/reports/ReportsModels.kt:61-75`:

```kotlin
fun buildTcespDesc(tx: Transaction, supplierName: String?): String {
    val prefix = when (tx.type) {
        TransactionType.INCOME, TransactionType.REVERSAL -> "RECEBIDO DE,"
        else -> "PAGO A,"
    }
    val creditor = (supplierName ?: tx.description).uppercase()
    val docPart = when {
        tx.documentType != null && tx.documentNumber != null ->
            " CF ${tx.documentType.uppercase()} ${tx.documentNumber}"
        tx.documentType != null -> " CF ${tx.documentType.uppercase()}"
        tx.documentNumber != null -> " CF DOC ${tx.documentNumber}"
        else -> ""
    }
    return "$prefix $creditor$docPart"
}
```

Expressão do credor: linha 66 — `val creditor = (supplierName ?: tx.description).uppercase()`.

Chamada em `ReportsViewModel.applyLivroDiarioFilter()` (`reports/ReportsViewModel.kt:65,71`):
```kotlin
val supplierName = tx.supplierId?.let { supplierMap[it]?.name }
// ...
tcespDesc = buildTcespDesc(tx, supplierName)
```

`supplierName` é calculado exclusivamente de `tx.supplierId`.

### 2.3 Essa expressão usa CounterpartyResolver?

**NÃO.**

Prova: `ReportsViewModel.kt` não importa nem instancia `CounterpartyResolver`. A resolução usa diretamente `tx.supplierId?.let { supplierMap[it]?.name }` (linha 65). `employeeId` não é consultado em nenhum ponto de `ReportsViewModel` ou `buildTcespDesc`. `ReportsViewModel` não recebe `EmployeeRepository` no construtor (`reports/ReportsViewModel.kt:23-31`).

### 2.4 Valor concreto para employee_id=42, supplier_id=NULL (PayrollEngine)

Percurso passo a passo:

**1.** `ReportsViewModel.applyLivroDiarioFilter()` linha 65:
```kotlin
val supplierName = tx.supplierId?.let { supplierMap[it]?.name }
```
`tx.supplierId = null` → curto-circuita → `supplierName = null`

**2.** `buildTcespDesc(tx, null)` linha 66:
```kotlin
val creditor = (null ?: tx.description).uppercase()
```
`PayrollEngine` grava `description = "Pagamento ${employee.name} — $monthLabel"` (`PayrollEngine.kt:53`).  
Para funcionário "KAILON", mês "Agosto/2026": `creditor = "PAGAMENTO KAILON — AGOSTO/2026"`

**3.** `docPart`: `tx.documentType = null`, `tx.documentNumber = null` (PayrollEngine não preenche) → cai no `else` → `docPart = ""`

**4.** Resultado final impresso na linha do Livro Diário:
```
PAGO A, PAGAMENTO KAILON — AGOSTO/2026
```

O nome do funcionário aparece via fallback `tx.description` — não via resolução de `employeeId`. A parte `CF [DOC]` está ausente.

### 2.5 Diferença para PayrollImportService

`PayrollImportService.confirm()` também não preenche `documentType` nem `documentNumber`. Mecanismo idêntico. Apenas a description difere em formato:

| Origem | Description gravada | String TCESP resultante |
|---|---|---|
| PayrollEngine | `"Pagamento KAILON — Agosto/2026"` | `"PAGO A, PAGAMENTO KAILON — AGOSTO/2026"` |
| PayrollImportService (salário) | `"Salário AGO/2026 — KAILON"` | `"PAGO A, SALÁRIO AGO/2026 — KAILON"` |
| PayrollImportService (adiantamento) | `"Adiantamento AGO/2026 — KAILON"` | `"PAGO A, ADIANTAMENTO AGO/2026 — KAILON"` |

### 2.6 Quem alimenta ReportsExporter — dataset do ReportsViewModel

`ReportsViewModel.applyLivroDiarioFilter()` (linhas 52–81):

```kotlin
val supplierMap = supplierRepository.findAll().associateBy { it.id }
val accountMap  = accountRepository.findAll().associateBy { it.id }
val txs = transactionRepository.findAllPaid(
    from      = filter.from,
    to        = filter.to,
    accountId = filter.accountId
)
txs.map { tx ->
    val supplierName = tx.supplierId?.let { supplierMap[it]?.name }
    val accountName  = accountMap[tx.accountId]?.name ?: "#${tx.accountId}"
    LivroDiarioEntry(
        transaction  = tx,
        supplierName = supplierName,
        accountName  = accountName,
        tcespDesc    = buildTcespDesc(tx, supplierName)
    )
}
```

`employees` **não é carregado**. Construtor de `ReportsViewModel` (linhas 23–31) não recebe `EmployeeRepository`. Não existe chamada a repositório de funcionários em nenhuma função do `ReportsViewModel`.

---

## PARTE 3 — Os outros relatórios que nomeiam credor

### 3.1 Balancete (ReportsExporter)

Colunas: CENTRO DE CUSTO, CATEGORIA, DOT. MENSAL, DOT. ANUAL, REALIZADO, SALDO, % UTIL.  
**Sem coluna de credor. Não se aplica.**

### 3.2 Demonstrativo (ReportsExporter)

Colunas: CÓD. AUDESP, CATEGORIA, GRUPO, RECEITA, DESPESA, SALDO.  
**Sem coluna de credor. Não se aplica.**

### 3.3 Comprovante individual — receiptPdf() (RN-31)

Expressão de credor — `ReportsExporter.kt:667`:
```kotlin
field("FORNECEDOR / CREDOR", supplierName ?: "—", margin, y, mid - 6f)
```

`supplierName` vem de `TransactionsViewModel.exportReceipt()` linha 291:
```kotlin
val supplierName = tx.supplierId?.let { sups.find { s -> s.id == it }?.name }
```

**Usa CounterpartyResolver? NÃO.** Lookup direto por `supplierId` em `_suppliers.value`.  
Para lançamento de folha (`supplier_id=NULL`): `supplierName = null` → impresso `"—"`.

### 3.4 Extrato — StatementExporter.kt (PDF e Excel)

**Excel** — `StatementExporter.kt:99`:
```kotlin
row.createCell(4).setCellValue("") // supplier name placeholder (not loaded here)
```

**PDF** — sem coluna de fornecedor. Colunas: DATA, DESCRIÇÃO, TIPO, DOC, DÉBITO, CRÉDITO, SALDO (`StatementExporter.kt:204`).

**Usa CounterpartyResolver? NÃO.**  
Para lançamento de folha: `""` (célula vazia) no Excel; ausente no PDF.

### 3.5 Tela de Extrato — StatementScreen.kt / StatementViewModel.kt

`StatementViewModel` não carrega suppliers nem employees — construtor sem `SupplierRepository` ou `EmployeeRepository` (`StatementViewModel.kt:39-44`). `StatementScreen` não exibe coluna de contraparte.  
**Usa CounterpartyResolver? NÃO. Não se aplica.**

### 3.6 Tela de Movimentações

**PayablesScreen.kt** (linha 171):
```kotlin
counterparties = uiState.counterparties,
```
`uiState.counterparties` vem de `CounterpartyResolver.resolve(all)` em `PayablesViewModel.load()`.  
**SIM — usa CounterpartyResolver.**  
Para lançamento de folha com `employeeId=42`, `supplierId=null`: retorna `employees[42]` = nome do funcionário.

**TransactionsScreen.kt** (linha 203–214): chama `TransactionListView` sem passar `counterparties`:
```kotlin
TransactionListView(
    items            = uiState.items,
    selectedId       = uiState.selectedItem?.id,
    grouped          = listFilter is TransactionListFilter.ActionRequired,
    onRowClick       = { openPanel(it) },
    ...
)
```
`counterparties` usa default `CounterpartyMap.EMPTY`. **NÃO usa CounterpartyResolver.**  
Para lançamento de folha: `nameFor(item) = null` → sem contraparte exibida.

### 3.7 Painel de detalhes — TransactionDetailsPanel.kt

Expressão de credor na seção Resumo — linha 95 + linha 175:
```kotlin
val supplierName = suppliers.find { it.id == item.supplierId }?.name
// ...
supplierName?.let { SummaryRow("Fornecedor", it) }
```

**Usa CounterpartyResolver? NÃO.** Lookup por `supplierId` em `_suppliers.value`.  
Para lançamento de folha (`supplierId=null`): `supplierName = null` → linha "Fornecedor" **não aparece** no Resumo.

### Tabela final

| Local | Expressão de credor | Usa CounterpartyResolver? | Valor para lançamento de folha |
|---|---|---|---|
| Livro Diário (Excel + PDF) | `(supplierName ?: tx.description).uppercase()` — `ReportsModels.kt:66` | **NÃO** | `"PAGO A, PAGAMENTO KAILON — AGOSTO/2026"` (description uppercase, sem CF) |
| Comprovante `receiptPdf()` | `supplierName ?: "—"` — `ReportsExporter.kt:667` | **NÃO** | `"—"` |
| Extrato Excel | `""` hardcoded — `StatementExporter.kt:99` | **NÃO** | `""` (célula vazia) |
| Extrato PDF | ausente (sem coluna) | **NÃO** | ausente |
| Tela Extrato | sem coluna | **NÃO** | N/A |
| Tela Movimentações (Pagamentos) | `counterparties.nameFor(item)` — `TransactionListComponents.kt:94,144` | **SIM** | nome do funcionário (ex: `"KAILON"`) |
| Tela Movimentações (Lançamentos) | `counterparties.nameFor(item)` com `EMPTY` — `TransactionsScreen.kt:203` | **NÃO** | `null` (sem exibição) |
| Painel de detalhes | `supplierName?.let { SummaryRow(...) }` — `TransactionDetailsPanel.kt:175` | **NÃO** | linha ausente |

---

## PARTE 4 — Estado do vínculo Funcionário↔Fornecedor

### 4.1 V24 e V26 na íntegra

**V24** — `V24__employee_supplier_fk.sql`:
```sql
-- Fase 8-A: Vincula funcionário ao seu cadastro de fornecedor/credor
-- Necessário para que o módulo de importação de folha saiba qual supplierId
-- usar ao criar os lançamentos de contas a pagar de cada funcionário.
-- Nullable: funcionários existentes não são afetados; vínculo é opcional.
ALTER TABLE employees ADD COLUMN supplier_id INTEGER REFERENCES suppliers(id);
```

**V26** — `V26__drop_employee_supplier_fk.sql`:
```sql
-- Remove a FK supplier_id de employees.
-- Funcionários PJ não precisam mais de cadastro duplicado em Fornecedores;
-- o vínculo era exigido apenas pelo PayrollImportService, que agora usa employeeId diretamente.
ALTER TABLE employees DROP COLUMN supplier_id;
```

### 4.2 Employee.kt completo

`src/main/kotlin/br/com/sisgfin/Employee.kt`:

```kotlin
data class Employee(
    override val id: Int = 0,
    val name: String,
    val document: String,
    val phone: String,
    val email: String,
    val role: String,
    val salary: Money,
    val paymentDay: Int,
    val paymentDays: String? = null,
    val employmentType: String? = null,
    val bankCode: String? = null,
    val agencyNumber: String? = null,
    val agencyDv: String? = null,
    val accountNumber: String? = null,
    val accountDv: String? = null,
    val accountType: String? = "CS",
    val active: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now()
) : Identifiable { ... }
```

**Não existe campo `supplierId`** em `Employee.kt`. Removido junto com a V26.

### 4.3 Forma de ligar Employee a Supplier hoje

**NÃO EXISTE VÍNCULO.** Não há join, view, tabela de junção nem campo compartilhado entre `employees` e `suppliers` no schema atual. `Employee.kt` não tem `supplierId`. A V26 removeu a FK. Nenhuma query no código cruza as duas tabelas por CPF.

### 4.4 Validação impedindo CPF duplicado em employees e suppliers

**NÃO EXISTE.** Nenhum `CHECK` em migração SQL, nenhuma validação em `TransactionService`, `EmployeeService` ou `SupplierRepository` impedindo o mesmo CPF nas duas tabelas.

---

## PARTE 5 — Como a folha resolve o credor hoje

### 5.1 PayrollImportService.import() e confirm() na íntegra

`src/main/kotlin/br/com/sisgfin/payroll/PayrollImportService.kt`

```kotlin
fun import(
    file: File,
    accountId: Int,
    categoryId: Int,
    costCenterId: Int?,
    referenceMonth: YearMonth,
    userId: Int
): PayrollImportResult {
    val (rawEntries, parserWarnings) = parser.parse(file)
    val allWarnings = parserWarnings.toMutableList()

    val entries = rawEntries.map { raw ->
        val employee = employeeRepository.findByCpf(raw.cpf)
        val employeeFound = employee != null
        val (adiantamentoDueDate, liquidoDueDate) = calculateDates(employee, referenceMonth)
        val warning: String? = if (employee == null)
            "CPF ${raw.cpf.formatCpf()} não localizado nos funcionários cadastrados"
        else null
        PayrollEntry(
            raw = raw,
            employeeId = employee?.id,
            adiantamentoDueDate = adiantamentoDueDate,
            liquidoDueDate = liquidoDueDate,
            employeeFound = employeeFound,
            warningMessage = warning
        )
    }
    allWarnings.addAll(
        entries.filter { !it.employeeFound }
            .map { "Não localizado: ${it.nome} (CPF: ${it.raw.cpf.formatCpf()})" }
    )
    return PayrollImportResult(
        entries = entries,
        notFoundCount = entries.count { !it.employeeFound },
        warnings = allWarnings,
        referenceMonth = referenceMonth
    )
}

fun confirm(
    result: PayrollImportResult,
    accountId: Int,
    categoryId: Int,
    costCenterId: Int?,
    userId: Int
): Int {
    val monthLabel = result.referenceMonth.format(monthFmt).uppercase()
    var created = 0

    result.entries.filter { it.employeeFound }.forEach { entry ->
        transactionService.cancelPendingPayrollForMonth(entry.employeeId!!, result.referenceMonth)
        val now = LocalDateTime.now()

        if (!entry.adiantamento.isZero()) {
            transactionService.createFromPayrollImport(
                Transaction(
                    type = TransactionType.EXPENSE,
                    status = TransactionStatus.PENDING,
                    description = "Adiantamento $monthLabel — ${entry.nome}",
                    amount = entry.adiantamento,
                    issueDate = now,
                    dueDate = entry.adiantamentoDueDate.atStartOfDay(),
                    accountId = accountId,
                    costCenterId = costCenterId,
                    categoryId = categoryId,
                    employeeId = entry.employeeId,
                    createdBy = userId
                )
            )
            created++
        }

        transactionService.createFromPayrollImport(
            Transaction(
                type = TransactionType.EXPENSE,
                status = TransactionStatus.PENDING,
                description = "Salário $monthLabel — ${entry.nome}",
                amount = entry.liquido,
                issueDate = now,
                dueDate = entry.liquidoDueDate.atStartOfDay(),
                accountId = accountId,
                costCenterId = costCenterId,
                categoryId = categoryId,
                employeeId = entry.employeeId,
                createdBy = userId
            )
        )
        created++
    }
    return created
}
```

### 5.2 Lookup CPF → Employee → Supplier após V26

O lookup CPF→Employee **existe** — `import()` linha 39: `employeeRepository.findByCpf(raw.cpf)`.

O passo Employee→Supplier **não existe**. A V26 removeu `employees.supplier_id`. O `confirm()` grava somente `employeeId`. Não há pesquisa em `suppliers`, não há `supplierId` no `Transaction` criado.

O caminho descrito no RETRATO_PROJETO ("lookup CPF → Employee → Supplier") era o fluxo da era V24. Após V26: lookup CPF → Employee → `employeeId` direto. Supplier saiu do fluxo completamente.

### 5.3 TransactionService.createFromPayrollImport() — campos preenchidos e NULL

`TransactionService.kt:518-529`:
```kotlin
fun createFromPayrollImport(tx: Transaction): Int {
    val userId = sessionManager.currentUser.value?.id ?: tx.createdBy
    val now = LocalDateTime.now()
    val prepared = tx.copy(id = 0, createdBy = userId, createdAt = now, updatedAt = now)
    validateAccount(prepared.accountId)
    val id = repository.insert(prepared)
    addTimeline(id, TimelineEventType.PAYROLL_IMPORT,
        "Importado via folha de pagamento — funcionário #${tx.employeeId}",
        prepared.amount, null, prepared.status)
    audit("TRANSACTION_CREATED", id, auditDetail(prepared.status, null, prepared.amount))
    return id
}
```

| Campo | Estado | Prova |
|---|---|---|
| `employee_id` | **PREENCHIDO** | `employeeId = entry.employeeId` — `PayrollImportService.kt:105,124` |
| `supplier_id` | **NULL** | não passado; default `null` em `Transaction.kt:23` |
| `category_id` | **PREENCHIDO** | `categoryId = categoryId` — `PayrollImportService.kt:104,123` |
| `project_id` | **NULL** | não passado; default `null` |
| `document_type` | **NULL** | não passado; default `null` |
| `document_number` | **NULL** | não passado; default `null` |

### 5.4 PayrollEngine — trecho que monta a Transaction

`PayrollEngine.kt:49-59` (`generateForMonth`) e `:91-101` (`generateForEmployee`):

```kotlin
transactionService.create(
    Transaction(
        type        = TransactionType.EXPENSE,
        status      = TransactionStatus.PENDING,
        description = "Pagamento ${employee.name} — $monthLabel",
        amount      = employee.salary,
        issueDate   = LocalDateTime.now(),
        dueDate     = dueDate.atStartOfDay(),
        accountId   = defaultAccountId,
        employeeId  = employee.id
    )
)
```

| Campo | Estado |
|---|---|
| `employee_id` | **PREENCHIDO** (`employee.id`) |
| `supplier_id` | **NULL** (não passado) |
| `category_id` | **NULL** (não passado) |
| `project_id` | **NULL** (não passado) |
| `document_type` | **NULL** (não passado) |
| `document_number` | **NULL** (não passado) |

### 5.5 CF [DOC] para lançamentos de folha

`documentType=null` e `documentNumber=null` em ambos os caminhos.

Em `buildTcespDesc` (`ReportsModels.kt:67-73`):
```kotlin
val docPart = when {
    tx.documentType != null && tx.documentNumber != null -> ...
    tx.documentType != null -> ...
    tx.documentNumber != null -> ...
    else -> ""
}
```

Cai no `else` → `docPart = ""`. A parte `CF [DOC]` da RN-29 está ausente para todos os lançamentos de folha.

---

## PARTE 6 — Lançamento manual para funcionário

### 6.1 Seletor de FUNCIONÁRIO no painel de lançamento

```
rg -n "employeeId" src/main/kotlin/br/com/sisgfin/financial/transactions/TransactionDetailsPanel.kt
```
**Zero ocorrências.**

**NÃO EXISTE** seletor de funcionário em `TransactionDetailsPanel.kt`.

### 6.2 Todos os pontos que escrevem employee_id

**Caminhos automáticos (engines/importadores):**

| Arquivo:linha | Contexto |
|---|---|
| `employees/PayrollEngine.kt:58` | `employeeId = employee.id` em `generateForMonth()` |
| `employees/PayrollEngine.kt:100` | `employeeId = employee.id` em `generateForEmployee()` |
| `payroll/PayrollImportService.kt:50` | `employeeId = employee?.id` em `import()` (preview) |
| `payroll/PayrollImportService.kt:105` | `employeeId = entry.employeeId` em `confirm()` — adiantamento |
| `payroll/PayrollImportService.kt:124` | `employeeId = entry.employeeId` em `confirm()` — salário |

**Caminhos de UI:**

| Arquivo:linha | Contexto |
|---|---|
| `api/routes/TransactionRoutes.kt:219` | `employeeId = employeeId` — rota REST; preenchido apenas se o DTO incluir o campo |

Nenhum campo de UI (`TransactionDetailsPanel.kt`, `OfxImportScreen.kt`, `RecurringScreen.kt`) escreve `employeeId`.

---

## PARTE 7 — Dados bancários duplicados

### 7.1 Campos bancários em employees (V25) e em suppliers

**Employees** — `V25__employee_banking_fields.sql`:
```sql
ALTER TABLE employees ADD COLUMN bank_code      VARCHAR(3)  NULL;
ALTER TABLE employees ADD COLUMN agency_number  VARCHAR(10) NULL;
ALTER TABLE employees ADD COLUMN agency_dv      VARCHAR(2)  NULL;
ALTER TABLE employees ADD COLUMN account_number VARCHAR(20) NULL;
ALTER TABLE employees ADD COLUMN account_dv     VARCHAR(2)  NULL;
ALTER TABLE employees ADD COLUMN account_type   VARCHAR(2)  NULL DEFAULT 'CS';
```
Kotlin: `Employee.bankCode`, `agencyNumber`, `agencyDv`, `accountNumber`, `accountDv`, `accountType`, `formattedAgency` (computed), `formattedAccount` (computed).

**Suppliers** — `FinancialModels.kt` (data class `Supplier`):
```kotlin
val pixKey: String? = null,
val bank: String? = null,
val agency: String? = null,
val account: String? = null,
```
Sem DV separado, sem código COMPE, sem `account_type`.

### 7.2 O que PayrollBankExporter (remessa BB) lê

`PayrollImportViewModel.kt:263-267`:
```kotlin
remessaEntries += RemessaEntry(
    ...
    agency  = emp.formattedAgency ?: "",
    account = emp.formattedAccount ?: "",
    ...
)
```

`formattedAgency` e `formattedAccount` são computed properties de `Employee` (`Employee.kt:28-36`).  
**Lê exclusivamente `employees` (dados da V25). Não acessa `suppliers`.**

### 7.3 Divergência se o pagamento fosse via fornecedor

Se o operador criasse o lançamento com `supplierId` preenchido, o `PayrollBankExporter` não o incluiria — ele gera remessa a partir de `PayrollImportResult.entries` (origem XLSX + lookup CPF em `employees`). Não há caminho de remessa para lançamentos com `supplierId`.

| Atributo | employees | suppliers |
|---|---|---|
| Código banco (COMPE) | `bank_code VARCHAR(3)` | ausente |
| Agência com DV separado | `agency_number + agency_dv` | `agency` (string única) |
| Conta com DV separado | `account_number + account_dv` | `account` (string única) |
| Tipo de conta | `account_type DEFAULT 'CS'` | ausente |
| Pix | ausente | `pix_key` |

---

## PARTE 8 — Teste de realidade nos dados

**BANCO INDISPONÍVEL.** Contêiner `sisgfin-db` está parado.

*Referência: dados da base local foram reportados em `docs/relatorios/R1_CONTRAPARTE_LANCAMENTO.md` (4 transações ativas, todas `employee_id=1`, `supplier_id=NULL`).*

---

## PARTE 9 — Auditoria numerada existente

```
docs/relatorios/
  DELTA_RETRATO.md      R1_CONTRAPARTE_LANCAMENTO.md
  F2_CONTAS_A_PAGAR.md  R2_DADOS_BANCARIOS.md
  F2_FIX_P0.md          R3_SALDO_E_ESTORNO.md
  P0_4_LIQUIDACAO.md    R4_ENGINES_CONCORRENCIA.md
  P0_5_ENCARGOS_NO_SALDO.md  R5_IA_MOVIMENTACOES.md
                        R6_CLASSIFICACAO.md
                        R7_ORCAMENTO_E_OPERACAO.md
```

Achados numerados em documentos existentes relevantes para este relatório:

**`docs/relatorios/DELTA_RETRATO.md`:**
- `C2` — atualização de campos em transações PAID/PENDING (7 casos)
- `R7` — REVERSAL tratado como receita no Demonstrativo (não como redução de despesa)

**`docs/specs/SPEC_OPERACAO_CONSULTA.md:208`:**
- `R7.3` — sem validação de funcionário inativo nem data de desligamento para `employeeId`

**`docs/relatorios/R1_CONTRAPARTE_LANCAMENTO.md` — Achados seção 4:**

| # | Severidade | Achado |
|---|---|---|
| 1 | ALTO | Painel de detalhes não exibe beneficiário quando só há `employeeId` |
| 2 | MÉDIO | Formulário só edita `supplierId`; sem seletor de funcionário |
| 3 | BAIXO | Migração V24 (`employees.supplier_id`) removida na V26; código atual não depende dela |
| 4 | MÉDIO | RN-02 não cobre lançamentos de folha (só `employeeId`) |

---

## RESUMO DOS ACHADOS

1. **Livro Diário (RN-29) — credor em lançamentos de folha não é o nome canônico do funcionário via `employeeId`.** Para `supplier_id=NULL`, `buildTcespDesc` usa `tx.description.uppercase()` como fallback. O nome aparece indiretamente (description já contém o nome), mas `CounterpartyResolver` não é consultado e o mapeamento `employeeId→nome` não é realizado. `ReportsViewModel` não recebe `EmployeeRepository`.

2. **A parte `CF [DOC]` da RN-29 está ausente para todos os lançamentos de folha.** `documentType` e `documentNumber` são `null` nos dois caminhos (PayrollEngine e PayrollImportService). A string gerada é `"PAGO A, [DESCRIPTION]"` sem parte documental.

3. **`CounterpartyResolver` é usado somente em `PayablesScreen` (Contas a Pagar).** Não alcança: Livro Diário, Extrato, Demonstrativo, Comprovante, tela de Lançamentos, painel de detalhes.

4. **Comprovante individual (`receiptPdf`)** — campo "FORNECEDOR / CREDOR" exibe `"—"` para lançamentos de folha (`supplierId=null`, sem resolução de `employeeId`).

5. **Extrato Excel** — coluna FORNECEDOR contém string vazia hardcoded; comentário no código indica placeholder. Extrato PDF não tem a coluna.

6. **O vínculo `Employee→Supplier` foi criado na V24 e removido na V26.** `Employee.kt` não tem campo `supplierId`. Não existe forma de ligar funcionário a fornecedor no schema atual. Dados bancários da remessa leem exclusivamente `employees` (V25); `Supplier` tem campos bancários estruturalmente diferentes (sem COMPE, sem DV separado).

7. **`TransactionsScreen` (tela Lançamentos)** não passa `counterparties` ao `TransactionListView`, usando `CounterpartyMap.EMPTY`; nenhuma contraparte é exibida na lista independente do tipo de vínculo.

8. **Não existe validação impedindo o mesmo CPF em `employees` e `suppliers` simultaneamente.**
