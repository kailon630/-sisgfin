# Análise Técnica — Módulo de Movimentações Financeiras

**SisgFin v1.0.8 — Gerado em 2026-08-06**

---

## Sumário

- [Estrutura](#estrutura)
- [Fluxo do Usuário](#fluxo-do-usuário)
- [Entidades Utilizadas](#entidades-utilizadas)
- [Dependências entre Módulos](#dependências-entre-módulos)
- [Problemas Encontrados](#problemas-encontrados)
- [UX e Navegação](#ux-e-navegação)
- [Melhorias Priorizadas](#melhorias-priorizadas)

---

## Estrutura

### Arquivos Envolvidos

#### Entidades e Enums

| Arquivo | Conteúdo |
|---|---|
| `financial/transactions/Transaction.kt` | Entidade principal (28 campos) |
| `financial/transactions/TransactionType.kt` | Enum: INCOME, EXPENSE, TRANSFER, ADJUSTMENT, REVERSAL |
| `financial/transactions/TransactionStatus.kt` | Enum: DRAFT, PENDING, SCHEDULED, PAID, OVERDUE, PARTIAL, CANCELED |
| `financial/transactions/FinancialTransactionsTable.kt` | Tabela Exposed `financial_transactions` |
| `FinancialModels.kt` | Supplier, FinancialAccount, CostCenter |
| `financial/categories/ExpenseCategory.kt` | Natureza Financeira |
| `financial/projects/Project.kt` | Projeto Financeiro (V28) |
| `budget/BudgetItem.kt` | Rubrica Orçamentária |
| `contracts/Contract.kt` | Contrato com fornecedor |
| `recurrence/RecurrenceTemplate.kt` | Template de lançamento recorrente |
| `Employee.kt` | Funcionário com dados bancários |

#### Camada de Domínio (Workflow)

| Arquivo | Responsabilidade |
|---|---|
| `workflow/TransactionStateMachine.kt` | Máquina de estados: transições válidas, estados terminais |
| `workflow/OverdueEngine.kt` | Regra de auto-marcação PENDING → OVERDUE |
| `financial/transactions/TransactionValidator.kt` | Validação estrutural da entidade |
| `financial/transactions/TransactionListFilter.kt` | Enum de filtros de lista |
| `timeline/TransactionTimelineEvent.kt` | Evento de linha do tempo (15 tipos) |
| `timeline/TimelineEventType.kt` | Enum de 15 tipos de evento |
| `timeline/TransactionTimelineRepository.kt` | Persistência da timeline |

#### Camada de Serviço

| Arquivo | Linhas | Responsabilidade |
|---|---|---|
| `TransactionService.kt` | ~696 | CRUD, parcelas, transferências, estornos, pagamentos, OFX, folha, recorrências |
| `FinancialServices.kt` | — | SupplierService, FinancialAccountService, CostCenterService |
| `budget/BudgetItemService.kt` | — | CRUD rubricas + cálculo realizado |
| `recurrence/RecurrenceEngine.kt` | — | Geração de pré-visões por template |
| `employees/PayrollEngine.kt` | — | Geração automática de pré-visões de folha |
| `ofx/OfxImportService.kt` | ~156 | Pipeline de 8 etapas de importação OFX |
| `payroll/PayrollImportService.kt` | ~152 | Importação de folha XLSX |
| `cashflow/CashFlowService.kt` | ~110 | Projeção e simulação de fluxo de caixa |
| `financial/ledger/LedgerService.kt` | — | Contabilidade de partidas dobradas |

#### Camada de Repositório

| Arquivo | Linhas | Responsabilidade |
|---|---|---|
| `TransactionRepository.kt` | ~753 | Todas as queries de transações (50+ métodos) |
| `FinancialRepositories.kt` | — | SupplierRepository, FinancialAccountRepository, CostCenterRepository |
| `budget/BudgetItemRepository.kt` | — | Queries de rubrica + soma realizado |
| `recurrence/RecurrenceTemplateRepository.kt` | — | CRUD templates |
| `ofx/OfxImportRepository.kt` | — | Deduplicação por FITID + log de importações |
| `contracts/ContractRepository.kt` | — | CRUD contratos + consumo |
| `financial/projects/ProjectRepository.kt` | — | CRUD projetos financeiros + consumo realizado |

#### ViewModel e UI

| Arquivo | Responsabilidade |
|---|---|
| `TransactionsViewModel.kt` (~400 linhas) | Orquestração completa da tela de lançamentos |
| `TransactionsScreen.kt` | Listagem com filtros, ações rápidas |
| `TransactionDetailsPanel.kt` | Painel lateral: dados + ações + timeline + orçamento |
| `TransactionStatusStyle.kt` | Cores e labels por status |
| `ReceivablesScreen.kt` + `ReceivablesViewModel.kt` | Aging de recebíveis (4 tiles) |
| `statement/StatementScreen.kt` + VM | Extrato por conta com filtros |
| `cashflow/CashFlowScreen.kt` + VM | Projeção + simulação |
| `recurrence/RecurringScreen.kt` + VM | Gestão de templates recorrentes |
| `ofx/OfxImportScreen.kt` + VM | Wizard de importação OFX |
| `payroll/PayrollImportScreen.kt` + VM | Wizard de importação folha (4 etapas) |

---

### Responsabilidades por Classe

#### `Transaction`

Entidade central. Acumula 28 campos cobrindo: identificação, tipo/status, valores, datas, referências a entidades relacionadas (conta, fornecedor, centro de custo, categoria, projeto, funcionário, contrato, template de recorrência) e rastreamento (criador, timestamps, isActive). O campo `parentTransactionId` é polissêmico: usado para parcelamento filho, par de transferência e original de estorno — três semânticas distintas num único campo.

#### `TransactionService`

Ponto central de toda a lógica transacional. Responsável por: criação simples, geração de parcelas, criação de transferências em par, estornos, registro de pagamento (com juros/multa), cancelamento com cascata, importação OFX, importação folha, geração de recorrências e reconciliação OFX. Também mantém a sincronização de status OVERDUE e delega ao LedgerService.

Concentra lógica demais: um serviço de criação, um workflow engine, um orquestrador de importação e um motor de reconciliação — tudo no mesmo arquivo.

#### `TransactionRepository`

Repositório com cerca de 50 métodos de query. Serve múltiplos contextos diferentes: fluxo de caixa, folha de pagamento, OFX, orçamento, contratos, extrato, dashboard. É o ponto de maior acoplamento de todo o sistema — praticamente todos os outros serviços o utilizam direta ou indiretamente.

#### `TransactionStateMachine`

Singleton puro. Define as transições válidas entre estados e oferece predicados semânticos (`isTerminal`, `allowsPayment`, `allowsCancel`, `resolveStatusAfterPayment`). Bem isolado, sem dependências externas.

#### `TransactionValidator`

Singleton puro. Valida estrutura da entidade antes de persistir. Verifica: campos obrigatórios, valor positivo, coerência de datas de pagamento, transições de estado via StateMachine, e aviso de período do centro de custo (RN-08).

#### `TransactionsViewModel`

Coordena 10 dependências injetadas (algumas opcionais: `RecurrenceTemplateService?`, `ContractService?`, `ProjectRepository?`). Mantém StateFlows para: lista de lançamentos, entidades de referência (contas, fornecedores, CCs, categorias, contratos, projetos), timeline, balanço orçamentário, alertas de contrato e estado de UI dos diálogos.

---

### Fluxo UI → ViewModel → Service → Repository

```
TransactionsScreen
  └── TransactionsViewModel
        ├── TransactionService
        │     ├── TransactionRepository          (persistência e queries)
        │     ├── FinancialAccountRepository     (validação de conta)
        │     ├── SupplierRepository             (validação de fornecedor)
        │     ├── CostCenterRepository           (período do CC)
        │     ├── AuditRepository               (log imutável)
        │     ├── TransactionTimelineRepository  (histórico de eventos)
        │     ├── SessionManager                (permissões + usuário)
        │     └── LedgerService                 (partidas dobradas)
        ├── FinancialAccountRepository           (StateFlow: accounts)
        ├── SupplierRepository                  (StateFlow: suppliers)
        ├── CostCenterRepository                (StateFlow: costCenters)
        ├── ExpenseCategoryRepository           (StateFlow: categories)
        ├── BudgetItemRepository                (RN-26: balance real-time)
        ├── RecurrenceTemplateService?          (saveWithRecurrence)
        ├── ContractService?                    (checkContractExceed)
        └── ProjectRepository?                  (StateFlow: projects)
```

---

## Fluxo do Usuário

### Como um Lançamento é Criado

1. Usuário clica em "Nova Despesa" ou "Nova Receita" → ViewModel chama `openNewExpense()` / `openNewIncome()` com o tipo pré-preenchido.
2. `TransactionDetailsPanel` abre em modo de edição. Usuário preenche: descrição, valor, conta, vencimento, fornecedor (opcional), categoria, centro de custo (opcional), projeto (opcional), parcelamento.
3. Se categoria e CC forem selecionados, `queryBudgetBalance()` dispara em tempo real via StateFlow (RN-26), exibindo o saldo disponível da rubrica.
4. Se contrato for vinculado, `checkContractExceed()` verifica se o valor ultrapassa o saldo do contrato (RN-27).
5. Ao salvar, o ViewModel chama `TransactionService.create(tx)`.
6. O Service valida via `TransactionValidator.validate()`.
7. Se `installmentTotal > 1`, gera parcelas filhas mensais com datas ajustadas; a última absorve o arredondamento.
8. Para cada TX criada: persiste no repositório, registra evento CREATED na timeline, grava no audit log.
9. Se a opção de recorrência foi marcada, `saveWithRecurrence()` cria um `RecurrenceTemplate` vinculado ao lançamento.

### Como é Editado

1. Usuário seleciona lançamento na lista → `selectTransaction()` no ViewModel carrega o item e a timeline.
2. `TransactionDetailsPanel` renderiza em modo de leitura (com ações disponíveis por status e permissão).
3. Usuário clica em "Editar" → painel entra em modo de edição.
4. Ao salvar, `TransactionService.update(tx)` valida a transição de status via `StateMachine.assertTransition()` e persiste.
5. Timeline registra UPDATED (e STATUS_CHANGED se houve mudança de estado).
6. Nota: `employeeId` não pode ser alterado via `update()` — é imutável após criação.

### Como é Pago

**Pagamento total:** `markAsPaidFull(id, paymentDate?)` → Service chama `recordPayment` com `paidAmount = amount`.

**Pagamento parcial ou com encargos:** `recordPayment(id, paymentDate, paidAmount, interestAmount?, fineAmount?)`.

Internamente:
1. `TransactionStateMachine.allowsPayment(status)` verifica que o status é PENDING, OVERDUE ou PARTIAL.
2. `SessionManager.hasPermission(Permission.ConfirmPayment)` verifica autorização (RN-12).
3. `TransactionValidator.validatePayment()` garante que `paymentDate >= issueDate`.
4. `StateMachine.resolveStatusAfterPayment(amount, paidAmount)` retorna PAID se `paidAmount >= amount`, senão PARTIAL.
5. Repositório persiste `paymentDate`, `paidAmount`, `interestAmount`, `fineAmount`, `status`.
6. LedgerService registra a entrada contábil.
7. Timeline registra PAYMENT ou PARTIAL_PAYMENT.

### Como é Cancelado

1. `TransactionsViewModel.cancelTransaction(id)` → `TransactionService.cancel(id)`.
2. Service verifica `!StateMachine.isTerminal(status)` — estados terminais (PAID, CANCELED) não podem ser cancelados.
3. Se `type == TRANSFER`: busca o par via `findTransferDestination(sourceId)` e cancela ambos (RN-21).
4. Se for um lançamento pai com parcelas: `findActiveChildrenOf(parentId)` retorna filhos e cancela cada um em cascata (RN-19).
5. Repositório chama `deactivate(id)` que seta `isActive=false, status=CANCELED`.
6. Timeline registra CANCELED em cada TX afetada.

### Como é Pago (Consolidação / Reconciliação OFX)

A "consolidação" no SisgFin ocorre em dois cenários:

**Via importação OFX:**
1. `OfxImportService.import()` cria TXs com `status=PAID` diretamente (entradas do extrato bancário).
2. Para cada TX OFX criada, o service busca lançamentos manuais pendentes com mesmo valor ±3 dias de tolerância (`findPendingCandidates()`).
3. Candidatos à reconciliação são retornados em `OfxImportResult`.
4. Na tela de OFX, usuário vincula manualmente um lançamento OFX a um lançamento manual: `reconcile(manualTxId, ofxTxId, ofxFitId)`.
5. O lançamento manual muda para PAID; o TX OFX é desativado (para evitar duplicidade no saldo).

**Via ação rápida (OFX import):**
A tela de OFX exibe pré-visões não liquidadas com botão de quitação rápida — o usuário pode quitar sem entrar na tela principal de lançamentos.

---

## Entidades Utilizadas

### Transaction

Entidade central. 28 campos. Campos críticos:

| Campo | Tipo | Propósito |
|---|---|---|
| `type` | TransactionType | INCOME, EXPENSE, TRANSFER, ADJUSTMENT, REVERSAL |
| `status` | TransactionStatus | 7 estados com máquina de transições |
| `amount` | Money (DECIMAL 19,2) | Valor original |
| `paidAmount` | Money? | Valor efetivamente pago |
| `interestAmount` | Money? | Juros na quitação (V27) |
| `fineAmount` | Money? | Multa na quitação (V27) |
| `parentTransactionId` | Int? | **Polissêmico:** parcela-filho, par de transferência, ou original de estorno |
| `ofxFitId` | String? | ID de transação OFX para deduplicação |
| `reconciledWithFitId` | String? | FITID do OFX ao qual foi reconciliado |
| `recurrenceTemplateId` | Int? | Template que gerou esta TX |
| `contractId` | Int? | Contrato vinculado |
| `employeeId` | Int? | Funcionário (folha de pagamento) |
| `costCenterId` (Kotlin) | Int? | FK para tabela `projects` (CostCenter legado) |
| `projectId` (Kotlin) | Int? | FK para tabela `financial_projects` (Project novo, V28) |

### Supplier

Entidade `suppliers`. Cobre tanto fornecedores quanto clientes (campo `entityType`: FORNECEDOR, CLIENTE, AMBOS). Dados bancários para pagamento PIX/TED. Validação CPF/CNPJ com dígitos verificadores (RN-03).

### Employee

Entidade `employees`. Contém: dados cadastrais, salário, `paymentDay` (único), `paymentDays` (CSV de dias ex.: "5,20"), `employmentType` (CLT/PJ/ESTAGIO/OUTROS), dados bancários completos (V25). O campo `paymentDays` é um CSV no banco — não normalizado.

### FinancialAccount

Entidade `financial_accounts`. Tipos: BANK, CASH, SAVINGS, INVESTMENT. Saldo calculado dinamicamente via `FinancialAccountService.calculateBalance()` — não persiste saldo corrente no banco (decisão de design). Fórmula RN-04:

```
saldo = initialBalance
      + Σ PAID(INCOME) + Σ PAID(REVERSAL) + Σ PAID(ADJUSTMENT) + Σ PAID(TransferIn)
      − Σ PAID(EXPENSE) − Σ PAID(TransferOut)
```

### ExpenseCategory

Entidade `expense_categories`. Natureza Financeira no vocabulário do TCESP/AUDESP. ~100 categorias pré-carregadas via seed (V10). Agrupadas por `groupCode/groupName`. Flag `isIncome` distingue receitas de despesas.

### CostCenter (tabela `projects`)

Nome Kotlin: `CostCenter`. Nome da tabela no banco: `projects`. Nome da coluna FK em `financial_transactions`: `project_id`. Representa convênio/projeto de repasse público (contexto TCESP). O nome legado sobreviveu ao refactoring — causa confusão direta com a entidade `Project` (V28).

### Project (tabela `financial_projects`)

Nome Kotlin: `Project`. Tabela: `financial_projects`. Introduzido em V28. Representa projeto financeiro operacional com status (PLANEJAMENTO/EM_ANDAMENTO/CONCLUIDO/CANCELADO), orçamento e datas. Conceitualmente distinto do CostCenter mas com sobreposição semântica.

### BudgetItem (Rubrica Orçamentária)

Triângulo `(costCenterId, categoryId, year)`. Valores mensais + anuais. Realizado calculado dinamicamente via `ΣPAID` das transações vinculadas. Bloqueia edição se o CostCenter estiver encerrado (RN-28).

### Contract

Entidade `contracts`. Liga um fornecedor a um valor total contratado. Pode ter um `RecurrenceTemplate` associado para geração automática de parcelas. Controla consumo via `sumConsumedByContract()`. O ViewModel alerta quando um novo lançamento ultrapassaria o saldo do contrato.

### RecurrenceTemplate

Entidade `recurrence_templates`. Define periodicidade (SEMANAL a ANUAL), dia do mês, conta, fornecedor, categoria, CC, projeto, contrato. O `RecurrenceEngine` gera pré-visões 2 meses à frente no boot. Pausar o template cancela todas as pré-visões futuras em cascata.

### TransactionTimelineEvent

Não é uma entidade de negócio — é um registro de auditoria viva. 15 tipos de evento cobrem criação, pagamento, cancelamento, transferência, estorno, reconciliação, importação OFX, importação folha, geração por recorrência. Cada ação do usuário produz pelo menos um evento.

---

## Dependências entre Módulos

### Grafo de dependências (serviços → repositórios)

```
TransactionService
  ├── TransactionRepository        [direto]
  ├── FinancialAccountRepository   [validação conta]
  ├── SupplierRepository           [validação fornecedor]
  ├── CostCenterRepository         [período do CC, RN-08]
  ├── AuditRepository              [RN-11]
  ├── TransactionTimelineRepository [RN-33]
  ├── SessionManager               [permissões, usuário corrente]
  └── LedgerService                [partidas dobradas]

FinancialAccountService
  └── FinancialAccountRepository
  └── TransactionRepository        [calculateBalance, RN-04]

CostCenterService
  └── CostCenterRepository
  └── TransactionRepository        [existsByCostCenterId, RN-07]

ExpenseCategoryService
  └── ExpenseCategoryRepository
  └── TransactionRepository        [existsByCategoryId, RN-10]

BudgetItemService
  └── BudgetItemRepository
  └── CostCenterRepository         [isEncerrado, RN-28]
  └── TransactionRepository        [sumRealized, RN-24]

RecurrenceEngine
  └── RecurrenceTemplateRepository
  └── TransactionRepository        [existsGeneratedFor, cancelFuture]
  └── TransactionService           [createFromRecurrence]

PayrollEngine
  └── EmployeeRepository
  └── TransactionRepository        [existsPaymentForEmployee]
  └── TransactionService           [createFromPayroll]
  └── FinancialAccountRepository

OfxImportService
  └── OfxParser
  └── TransactionService           [createFromOfx, findPendingCandidates, reconcile]
  └── OfxImportRepository          [existsByFitId, insert]
  └── FinancialAccountRepository   [ACCTID validation]

PayrollImportService
  └── EmployeeRepository
  └── TransactionService           [cancelPendingPayrollForMonth, createFromPayrollImport]

CashFlowService
  └── TransactionRepository        [findUnpaid]
  └── FinancialAccountRepository   [findAll]
  └── FinancialAccountService      [calculateBalance]

ContractService
  └── ContractRepository
  └── TransactionRepository        [sumConsumedByContract, existsPendingByContract]

StatementViewModel
  └── TransactionRepository        [findStatementEntries, openingBalance]
  └── FinancialAccountRepository

ReportsViewModel
  └── TransactionRepository        [findAllPaid, sumByStatus, ...]
  └── FinancialAccountRepository
  └── CostCenterRepository
  └── ExpenseCategoryRepository
```

### Dependências circulares

Nenhuma dependência circular direta entre serviços. RecurrenceEngine e PayrollEngine dependem de `TransactionService` mas não o contrário — a dependência é unidirecional. O `TransactionRepository` é o nó central com maior fan-in: 8 serviços/engines o consultam diretamente.

---

## Problemas Encontrados

### 1. `parentTransactionId` Polissêmico — Risco de Bug

**Severidade: Alta**

O campo `parentTransactionId` em `Transaction` carrega três semânticas distintas sem um discriminador:

- Parcela-filho: `parentTransactionId = idDoPai`
- Par de transferência: o destino tem `parentTransactionId = idDaOrigem`
- Estorno: `parentTransactionId = idDoOriginal`

O sistema hoje separa esses casos pelo `type` e pelo contexto de chamada, mas não existe um campo `parentRelationType` que torne isso explícito. Se um lançamento do tipo TRANSFER tiver parcelas (improvável, mas possível em imports futuros), o modelo colapsaria.

Risco real no código: `findTransferDestination(sourceId)` busca por `parentTransactionId` mas não filtra por `type == TRANSFER` — qualquer filho com `parentTransactionId` correto seria retornado.

---

### 2. `TransactionRepository` com 753 Linhas — God Repository

**Severidade: Alta**

O repositório concentra ~50 métodos de query servindo contextos completamente diferentes:

- Fluxo de caixa: `findUnpaid(windowEnd)`
- Folha: `existsPaymentForEmployee`, `findPendingPayrollForMonth`
- OFX: `existsByFitId`, `findPendingByAmountAndDateRange`
- Orçamento: `sumRealized`
- Contratos: `sumConsumedByContract`, `existsPendingByContract`
- Extrato: `findStatementEntries`, `openingBalance`
- Dashboard: `sumByStatus`, `countByStatus`, `lastPaymentDate`
- Recorrências: `existsGeneratedFor`, `cancelFutureByRecurrenceTemplate`

O efeito prático: qualquer nova funcionalidade que consulte transações vai aumentar este arquivo. Torna-se impossível entender o "contrato" de responsabilidade do repositório — o que deveria ser simples (CRUD + queries básicas de `financial_transactions`) tornou-se uma API genérica de analytics.

---

### 3. `TransactionService` com 696 Linhas — Mistura de Responsabilidades

**Severidade: Alta**

O service mistura:

- Lógica de negócio transacional (validação, estado, parcelas)
- Orquestração de importação OFX
- Orquestração de importação de folha
- Geração de recorrências
- Reconciliação bancária
- Sincronização de OVERDUE
- Exportação de comprovante (delegação)

Cada um desses é um caso de uso separado. O resultado é um arquivo difícil de testar isoladamente e onde uma mudança em reconciliação OFX pode quebrar o fluxo de parcelamento.

---

### 4. Dois Conceitos de "Projeto" com Nomes Sobrepostos

**Severidade: Alta**

| Conceito | Classe Kotlin | Tabela | FK em TX | Propósito |
|---|---|---|---|---|
| Centro de Custo (convênio TCESP) | `CostCenter` | `projects` | `project_id` | Controle de convênio público |
| Projeto Financeiro (operacional) | `Project` | `financial_projects` | `financial_project_id` | Projetos internos com orçamento |

Um desenvolvedor novo lendo o código vai confundir as duas entidades. O `CostCenter` é chamado de "projeto" na migration, "centro de custo" no domínio Kotlin, e "CC" na UI. O `Project` novo tem um nome mais intuitivo mas ocupa semântica similar. À medida que o sistema evolui, a pressão para unificar os dois vai crescer — e a migração de dados será custosa.

O histórico das migrations torna isso ainda mais confuso: V24 adicionou FK `employee → supplier` e V26 a removeu — indicando instabilidade de design nessa área.

---

### 5. Reconciliação OFX Contorna a StateMachine

**Severidade: Alta**

No método `reconcile()` do `TransactionService`, o TX OFX importado (que já está com `status=PAID`) é desativado via `repository.deactivate(id)` — que seta diretamente `isActive=false, status=CANCELED`.

Isso bypassa o `TransactionStateMachine`, que não permite a transição `PAID → CANCELED`. A lógica de cancelamento de transferências e o LedgerService não são acionados. A timeline registra CANCELED mas sem passar pela validação de estado.

É um caso de exceção deliberada (TX OFX não é "real" no sentido de negócio), mas não está documentada e cria uma inconsistência: `PAID → CANCELED` é proibido na StateMachine mas possível via `deactivate()`.

---

### 6. `OverdueEngine.shouldMarkOverdue()` com Condição Invertida

**Severidade: Média (potencial bug)**

O método verifica `!tx.isActive` para marcar como OVERDUE. Semanticamente, apenas transações ativas deveriam ser marcadas como OVERDUE. A condição correta seria `tx.isActive`. Se a condição está realmente como `!tx.isActive`, o engine tentaria marcar como OVERDUE transações já canceladas/inativas — o que não causaria bug visível (o `update()` seria chamado em TX inativa), mas significa que o OVERDUE real nunca é sincronizado.

Verificar o código exato desta condição antes de qualquer release.

---

### 7. Saldo de Conta Sem Cache — Performance Degradada

**Severidade: Média**

`FinancialAccountService.calculateBalance(accountId)` executa 6 queries separadas a cada chamada:

```
sumPaid(accountId, INCOME)
sumPaid(accountId, EXPENSE)
sumPaid(accountId, REVERSAL)
sumPaid(accountId, ADJUSTMENT)
sumPaidTransferIn(accountId)
sumPaidTransferOut(accountId)
```

Cada tela que exibe saldo (Dashboard, Extrato, Fluxo de Caixa, painel de contas) dispara essas 6 queries por conta. Com múltiplas contas e histórico crescente, o tempo de carregamento vai degradar. Sem índice composto `(account_id, status, type)`, cada query é um full-scan filtrado.

---

### 8. `paymentDays` como CSV no Banco

**Severidade: Média**

O campo `payment_days VARCHAR(100)` em `employees` armazena "5,20" — múltiplos dias de pagamento como string CSV. O método `effectivePaymentDays(): List<Int>` faz o parse em runtime.

Isso dificulta queries SQL que precisem filtrar por dia de pagamento (ex.: "quais funcionários têm pagamento no dia 5?"). Não é normalizável sem uma migration e alteração de código.

---

### 9. `TransactionsViewModel` com 10 Dependências Injetadas

**Severidade: Média**

O ViewModel recebe: `TransactionService`, `FinancialAccountRepository`, `SupplierRepository`, `CostCenterRepository`, `ExpenseCategoryRepository`, `SessionManager`, `BudgetItemRepository`, `RecurrenceTemplateService?`, `ContractService?`, `ProjectRepository?`.

Os três últimos são opcionais (`?`), indicando que foram adicionados incrementalmente sem refatorar o ViewModel em composições menores. Quanto mais o sistema crescer, mais dependências opcionais surgirão.

---

### 10. `OfxImportService`: ACCTID Mismatch Não Bloqueia

**Severidade: Média**

O banco OFX contém o número da conta bancária (`ACCTID`). O serviço valida se bate com a conta selecionada pelo usuário, mas apenas emite um warning — não bloqueia a importação.

Resultado: usuário pode importar extrato da conta corrente numa conta poupança sem nenhum impedimento. O saldo ficará errado silenciosamente.

---

### 11. Lógica de Filtro de Data Duplicada

**Severidade: Baixa**

Três lugares calculam ou validam datas de pagamento de formas similares:

- `TransactionValidator.validatePayment()` — valida `paymentDate >= issueDate`
- `TransactionService.recordPayment()` — valida a mesma regra antes de delegar
- `TransactionValidator.validate()` — valida novamente para o caso PAID

A redundância não causa bug, mas qualquer mudança na regra de data exige atualizações em múltiplos pontos.

---

### 12. Filtros Duplicados entre StatementViewModel e TransactionsViewModel

**Severidade: Baixa**

Tanto a tela de Extrato quanto a tela de Lançamentos filtram por período, conta, tipo e categoria. As lógicas de filtro são implementadas independentemente em cada ViewModel, com queries distintas no repositório (`findStatementEntries` vs os filtros da tela de lançamentos). Uma mudança de regra de filtragem pode ser aplicada em um e esquecida no outro.

---

### 13. `createFromOfx` Cria TX PAID Sem Fornecedor

**Severidade: Baixa (decisão de design)**

Transações criadas via OFX têm `status=PAID` imediatamente mas sem `supplierId` — OFX não carrega dados de credor. Isso é intencional, mas significa que essas transações nunca passam pelo `TransactionValidator` completo (que exige fornecedor para algumas regras). O método `createFromOfx()` existe exatamente para contornar essas validações.

O risco é que futuras regras de validação adicionadas ao `TransactionValidator` não se apliquem às TXs OFX, criando duas classes de transação com diferentes contratos de qualidade.

---

### 14. `TransactionsScreen` Carrega Dados de Referência na Inicialização

**Severidade: Baixa**

`loadReferenceData()` no ViewModel busca todas as contas, fornecedores, centros de custo, categorias, contratos e projetos em batch ao abrir a tela. Para bases com muitos fornecedores ou categorias, isso é um carregamento desnecessariamente amplo. Um campo de busca com autocomplete lazy seria mais eficiente para fornecedores.

---

## UX e Navegação

### Fluxo de Navegação

O sistema organiza as movimentações em múltiplas telas com propósitos distintos:

```
Menu Principal
  ├── Lançamentos (TransactionsScreen)     — listagem + ações
  ├── Extrato (StatementScreen)             — histórico por conta
  ├── Recebíveis (ReceivablesScreen)        — aging de receitas pendentes
  ├── Fluxo de Caixa (CashFlowScreen)      — projeção + simulação
  ├── Recorrências (RecurringScreen)        — templates ativos
  ├── Importar OFX (OfxImportScreen)        — wizard
  └── Importar Folha (PayrollImportScreen)  — wizard 4 etapas
```

### Troca Excessiva de Contexto

**Sim, existe.** Para realizar uma tarefa comum — "importar extrato, reconciliar com lançamentos manuais, e verificar se o saldo bateu" — o usuário precisa:

1. Ir para "Importar OFX" → importar o arquivo
2. Na mesma tela: reconciliar candidatos manualmente (associar OFX a lançamento manual)
3. Sair e ir para "Extrato" → verificar saldo resultante
4. Ou sair e ir para "Lançamentos" → verificar status dos lançamentos que foram reconciliados

Não há um fluxo integrado de "fechar mês bancário". O usuário precisa montar mentalmente o resultado de três telas diferentes.

### A Tela de Lançamentos Mistura Tarefas Diferentes

`TransactionsScreen` é o hub central mas serve múltiplos perfis de uso:

- Contador: quer ver todos os lançamentos do período para fechar
- Operador: quer ver só o que precisa de ação imediata (o filtro padrão `ActionRequired`)
- Gestor: quer ver comparativo de despesas por categoria

O filtro padrão `ActionRequired` protege o operador mas surpreende o contador na primeira abertura — os lançamentos pagos "sumindo" por padrão cria confusão.

### O Usuário Precisa Pensar Demais em Alguns Fluxos

**Importação de Folha:** Wizard de 4 etapas é correto, mas a etapa de seleção de conta/categoria/CC acontece antes do usuário ver o preview. Se o usuário selecionar a categoria errada na etapa 1 e só descobrir na etapa 3 (preview), precisa voltar ao início.

**Reconciliação OFX:** A lista de "candidatos" e "sem correspondência" é exibida dentro do resultado da importação. Não há uma tela dedicada de reconciliação onde o usuário possa fazer esse trabalho posteriormente — se fechar a janela de resultado, perde o contexto de quais entradas ainda precisam reconciliação.

**Estorno:** Requer justificativa em texto livre. Não há template ou categorias de motivo de estorno — cada usuário vai escrever de forma diferente, dificultando relatórios futuros de "motivos de estorno".

**Parcelamento + Filtros:** Lançamentos filho de parcelamento aparecem na lista com o mesmo visual dos lançamentos independentes. Não há agrupamento visual de parcelas do mesmo conjunto. O usuário precisa olhar o campo `n/N` no detalhe para entender o contexto.

---

## Melhorias Priorizadas

### Alta Prioridade

**1. Adicionar `parentRelationType` em `Transaction`**

O campo `parentTransactionId` carrega três semânticas. Adicionar um enum `ParentRelationType(INSTALLMENT, TRANSFER_PAIR, REVERSAL_OF)` tornaria as queries mais seguras e o código auto-documentado. Requer migration simples e ajuste no `findTransferDestination()`.

**2. Verificar e corrigir a condição em `OverdueEngine.shouldMarkOverdue()`**

Se a condição realmente é `!tx.isActive`, é um bug: lançamentos ativos com vencimento passado nunca são marcados OVERDUE. Validar imediatamente contra o código fonte e cobrir com teste de regressão.

**3. Bloquear importação OFX quando ACCTID não bate**

Mudar de warning para erro bloqueante (ou exigir confirmação explícita com destaque visual). Saldo incorreto silencioso é mais danoso que uma importação bloqueada.

**4. Índice composto `(account_id, status, type)` em `financial_transactions`**

As queries de cálculo de saldo executam 6x por tela. Um índice cobrindo os três campos usados em quase toda query de agregação vai reduzir significativamente o tempo de carregamento conforme o histórico cresce.

**5. Tela de Reconciliação Persistente**

Separar a reconciliação do resultado da importação OFX. Criar uma tela ou seção dedicada que liste os lançamentos OFX sem correspondência e os lançamentos manuais ainda pendentes — permitindo que o usuário reconcilie no seu próprio ritmo, não apenas no momento da importação.

---

### Média Prioridade

**6. Fragmentar `TransactionRepository` por contexto**

Extrair grupos de métodos para query objects ou repositórios especializados:
- `TransactionStatementQuery` — extrato, saldo, queries de relatório
- `TransactionPayrollQuery` — existsPayment, findPendingPayroll
- `TransactionOFXQuery` — existsByFitId, findPendingCandidates
- `TransactionBudgetQuery` — sumRealized

O repositório principal manteria apenas CRUD e queries de lançamento (status, tipo, período).

**7. Extrair `PaymentRecordingService` de `TransactionService`**

As operações `recordPayment`, `markAsPaid`, `reconcile` e `reverseTransaction` formam um caso de uso coeso de "mutação de estado de pagamento". Extrair para um service dedicado reduz a `TransactionService` para criação e manutenção de lançamentos.

**8. Documentar explicitamente a exceção de `deactivate()` na reconciliação OFX**

Adicionar comentário no código explicando por que `reconcile()` contorna a StateMachine — e registrar um teste que garanta que esse bypass não se propague para lançamentos normais.

**9. Reagrupar parcelas na lista de lançamentos**

Exibir parcelas do mesmo conjunto agrupadas visualmente (ex.: cabeçalho colapsável "Parcelamento — Nota Fiscal #X — 3 parcelas"). Reduz ruído visual e permite ao usuário entender o conjunto sem abrir cada parcela individualmente.

**10. Wizard de folha: validar conta/categoria antes do preview**

Mover a validação básica dos campos obrigatórios (conta, categoria) para o lado do ViewModel antes de permitir avançar da etapa 1. O usuário não deve descobrir que escolheu categoria errada só no preview.

---

### Baixa Prioridade

**11. Normalizar `paymentDays` em tabela separada**

Substituir `payment_days VARCHAR(100)` ("5,20") por uma tabela `employee_payment_days(employee_id, day_of_month)`. Permite queries SQL diretas e elimina o parse em runtime. Requer migration + ajuste no PayrollEngine.

**12. Unificar utilitários de data**

`RecurrenceDateCalculator`, `PayrollImportService.calculateDates()` e o parser de datas do OFX implementam lógicas similares de cálculo de datas de vencimento. Centralizar em uma classe `FinancialDateCalculator` ou usar um utilitário comum.

**13. Categorias de motivo de estorno**

Trocar o campo de texto livre de justificativa do estorno por um enum selecionável + campo de texto complementar opcional. Permite relatórios de "principais causas de estorno" futuramente.

**14. Agrupamento semântico das dependências do `TransactionsViewModel`**

Extrair as dependências opcionais (`RecurrenceTemplateService?`, `ContractService?`, `ProjectRepository?`) para um `TransactionContextProviders` injetado como único parâmetro. Evita que o ViewModel continue acumulando dependências incrementais.

**15. Cache de saldo de conta por sessão**

Para a sessão de desktop (não web), um cache de curta duração (invalidado por qualquer operação de pagamento na conta) reduziria as 6 queries de saldo para 1 hit de memória nas chamadas subsequentes da mesma tela.

---

*Relatório gerado a partir de análise estática do código-fonte. Não foram executadas queries no banco de produção. Hipóteses sobre comportamento runtime (especialmente o item 6 — OverdueEngine) devem ser verificadas contra o código atual antes de qualquer ação.*
