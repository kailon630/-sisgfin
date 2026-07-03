# Relatório Técnico — Fase 3A.1 — Transaction Foundation

**Projeto:** SisgFin  
**Data:** 22/05/2026  
**Escopo:** Fundação do motor transacional (contas a pagar/receber) — sem parcelamentos, ledger, conciliação ou dashboard financeiro novo.

**Build:** `./gradlew compileKotlin test` → **BUILD SUCCESSFUL**

---

# 1. Estrutura criada

```
src/main/kotlin/br/com/sisgfin/financial/transactions/
├── Transaction.kt
├── TransactionType.kt
├── TransactionStatus.kt
├── TransactionValidator.kt
├── FinancialTransactionsTable.kt      # Exposed → financial_transactions
├── TransactionRepository.kt
├── TransactionService.kt
├── TransactionsViewModel.kt
├── TransactionsScreen.kt
└── TransactionStatusStyle.kt            # badges visuais Ws-System

src/main/resources/db/migration/
└── V6__transaction_engine_foundation.sql   # (V5 já existia: money_precision)

src/main/kotlin/br/com/sisgfin/
└── LegacyTransactionRepository.kt       # legado dashboard (tabela transactions)
```

**Integração DI (Koin):**

- `TransactionRepository`, `TransactionService`, `TransactionsViewModel` registrados.
- `LegacyTransactionRepository` substitui `TransactionRepository` legado no dashboard.

**Integração UI:**

- `MainLayout` → `Screen.Transactions` usa `TransactionsScreen` com painel direito e resize (herdado do shell).

---

# 2. Modelagem de transações

## Entidade `Transaction`

| Campo | Tipo | Observação |
|-------|------|------------|
| `id` | `Int` | PK auto-increment |
| `type` | `TransactionType` | enum domínio |
| `status` | `TransactionStatus` | enum domínio |
| `description` | `String` | obrigatório (validação) |
| `amount` | **`Money`** | nunca `Double` |
| `issueDate` | `LocalDateTime` | emissão |
| `dueDate` | `LocalDateTime` | vencimento obrigatório |
| `paymentDate` | `LocalDateTime?` | obrigatório se `PAID` |
| `accountId` | `Int` | FK `financial_accounts` |
| `supplierId` | `Int?` | FK opcional |
| `projectId` | `Int?` | FK opcional |
| `notes` | `String?` | |
| `createdBy` | `Int?` | usuário sessão |
| `createdAt` / `updatedAt` | `LocalDateTime` | auditoria temporal |
| `isActive` | `Boolean` | exclusão lógica |
| `parentTransactionId` | `Int?` | **preparação** recorrência/parcelas |
| `ledgerEntryId` | `Int?` | **preparação** ledger |

Implementa `Identifiable` + `Activatable` (infra 2.5B).

## Separação do legado

| | Legado | Motor 3A.1 |
|---|--------|------------|
| Tabela | `transactions` | `financial_transactions` |
| Conta | `accounts` | `financial_accounts` |
| Classe | `LegacyTransaction` | `Transaction` |
| Uso | Dashboard KPIs | Tela Movimentações |

**Decisão arquitetural:** não migrar/destruir legado nesta fase — evita quebrar dashboard até fase de unificação contábil.

---

# 3. Status implementados

```kotlin
enum class TransactionStatus {
    DRAFT, PENDING, PAID, OVERDUE, PARTIAL, CANCELED, SCHEDULED
}
```

| Status | Persistido | Regra nesta fase |
|--------|------------|------------------|
| `PAID` | Sim | Exige `paymentDate` (`TransactionValidator`) |
| `OVERDUE` | Sim | Pode ser setado manualmente; **sem** job automático |
| `CANCELED` | Sim | `deactivate()` → `is_active=0` + status CANCELED; registro permanece |
| `PARTIAL` | Sim | Sem lógica de valor parcial ainda |
| `SCHEDULED` | Sim | Sem agendador |

**Transição bloqueada:** `CANCELED` → `PENDING` (mensagem: usar estorno no futuro).

---

# 4. Tipos implementados

```kotlin
enum class TransactionType {
    INCOME, EXPENSE, TRANSFER, ADJUSTMENT, REVERSAL
}
```

| Tipo | UI label | Validação 3A.1 |
|------|----------|----------------|
| `INCOME` | Receita | — |
| `EXPENSE` | Despesa | default em nova transação |
| `TRANSFER` | Transferência | não permite `supplierId` (prep. ledger) |
| `ADJUSTMENT` | Ajuste | — |
| `REVERSAL` | Estorno | sem fluxo dedicado ainda |

`TRANSFER` existe no modelo e BD para **extensão ledger**; sem par contábil nesta fase.

---

# 5. Regras de validação

Centralizadas em `TransactionValidator` + reforço em `TransactionService`:

| Regra | Implementação |
|-------|---------------|
| `amount > 0` | `!isZero() && !isNegative()` |
| `dueDate` obrigatório | tipo non-null em `Transaction` |
| `description` obrigatória | `isNotBlank()` |
| `accountId` obrigatório | `> 0` + conta existe em `FinancialAccountRepository` |
| `PAID` exige `paymentDate` | validator |
| `CANCELED` → `PENDING` proibido | validator com `previousStatus` |
| Transferência sem fornecedor | validator |

Erros lançados como `IllegalArgumentException` / `IllegalStateException` → `ErrorClassifier` no `BaseCrudViewModel` → `AppError` + snackbar.

---

# 6. Arquitetura utilizada

```
TransactionsScreen
       ↓ actions
TransactionsViewModel extends BaseCrudViewModel<Transaction>
       ↓ CrudOperations
TransactionService
       ↓
TransactionRepository → FinancialTransactionsTable (Exposed)
       ↓
SQLite financial_transactions

TransactionService → AuditRepository (ações TRANSACTION_*)
                   → SessionManager (createdBy / performedBy)
                   → FinancialAccountRepository (integridade conta)
```

**Padrões reutilizados (Fase 2.5B):**

- `BaseCrudViewModel`, `CrudAction`, `CrudEvent`, `CrudState`, `Result` pipeline
- `CrudEventEffects` (snackbar)
- `BaseCrudPanel` (painel contextual)
- Koin `factory` para ViewModel

**Não utilizado:** `AuditedCrudService` — auditoria transacional usa **ações semânticas** (`TRANSACTION_PAID`, etc.), não só CREATE/UPDATE genérico.

---

# 7. Auditoria integrada

| Ação audit | Quando |
|------------|--------|
| `TRANSACTION_CREATED` | `create()` após insert |
| `TRANSACTION_UPDATED` | `update()`, `markAsPaid()` |
| `TRANSACTION_CANCELED` | status → CANCELED ou `deactivate()` |
| `TRANSACTION_PAID` | status → PAID (create ou update) |

`entityType = "TRANSACTION"`, `entityId = id`, `newValue = description`, `performedBy = session user`.

**Não registrado ainda:** diff `oldValue`/`newValue` campo a campo (apenas descrição como resumo).

---

# 8. Fluxo operacional da UI

## 8.1 Listagem

1. Usuário abre **Movimentações** na sidebar.
2. `TransactionsViewModel` carrega via `TransactionService.listAll()` (com filtros ativos).
3. Tabela: Tipo | Descrição | Vencimento | Valor | Status (badge).

## 8.2 Busca e filtros

- Campo de busca → `service.applySearch()` + `repository.search()` (LIKE em description).
- Chips: Todas, Pendente, Pago, Vencido, Agendado → `filterByStatus`.
- (Tipo: suporte no service/VM; chips de tipo podem ser adicionados em 3A.2.)

## 8.3 Seleção e painel

- **Clique simples:** seleciona + abre painel direito (`TransactionDetailsPanel` / `BaseCrudPanel`).
- **Duplo clique:** `openDialog()` → popup rápido.
- **Escape:** fecha painel (shell `MainLayout`).

## 8.4 Edição

- Painel: tipo, status, valor, datas, conta (chips), notas, botão **Marcar como Pago**.
- Salvar → `TransactionService.save()` → validação + audit + reload.

## 8.5 Cancelamento

- `toggleActive` no CRUD → `deactivate()` → `is_active=false`, status CANCELED, audit `TRANSACTION_CANCELED`.

---

# 9. Componentes reutilizados

| Componente | Uso |
|------------|-----|
| `BaseCrudViewModel` | TransactionsViewModel |
| `BaseCrudPanel` | Painel detalhes |
| `CrudEventEffects` | Feedback operacional |
| `TableHeaderCell`, `EmptyState`, `WsButton`, `WsTextField`, `DetailSection` | Tabela e formulários |
| `MainLayout` painel resize + Escape | Shell workstation |
| `Money` / `MoneyFormatter` | Valores |
| `TransactionStatusBadge`, `TransactionTypeLabel` | **Novos** — Ws-System discreto |

**Melhoria vs auditoria anterior:** `TransactionRow` usa `hoverable` + `collectIsHoveredAsState` — **hover funcional** neste módulo.

---

# 10. Dívidas técnicas restantes

1. **Unificação** `accounts` legado ↔ `financial_accounts` + dashboard.
2. **OVERDUE automático** (job/rules por `dueDate`).
3. **PARTIAL** sem modelo de valor pago parcial.
4. **Datas no painel** como string `AAAA-MM-DD` (sem DatePicker workstation).
5. **Supplier/Project** não expostos no painel (campos no modelo, UI pendente).
6. **Popup rápido** simplificado (poucos campos).
7. **`markAsPaid` errors** no VM não propagam para `CrudEvent` (try/catch silencioso).
8. **Testes** ausentes para `TransactionValidator` e `TransactionService`.
9. **Filtro por tipo** na toolbar UI incompleto (service pronto).
10. Migration nomeada **V6** (prompt citava V5 — V5 já ocupado por `money_precision`).

---

# 11. Riscos identificados

| Risco | Severidade | Detalhe |
|-------|------------|---------|
| Dois motores de transação no BD | Alta | Operador confunde dashboard legado vs movimentações reais |
| Status OVERDUE manual | Média | Dados incorretos sem automação |
| PAID sem ledger | Média | Saldo de conta **não** altera — expectativa de usuário |
| TRANSFER incompleto | Média | Tipo permitido sem segunda perna contábil |
| Validação de data por parse frágil | Baixa | fallback `LocalDate.now()` |
| Filtros no `TransactionService` (stateful) | Baixa | singleton Koin — OK desktop single-user |

---

# 12. Preparação para ledger

**Incluído nesta fase:**

- Tabela sem `balance_after` (saldo virá do ledger).
- `ledger_entry_id` nullable (FK futura).
- `parent_transaction_id` (parcelas/recorrência).
- Tipos `TRANSFER`, `ADJUSTMENT`, `REVERSAL`.
- Auditoria por evento de negócio.
- Conta em `financial_accounts` (plano correto).

**Ainda necessário para ledger:**

- Tabela `ledger_entries` / partidas dobradas.
- Serviço de posting idempotente.
- Vínculo `Transaction` → `ledger_entry_id` preenchido ao pagar.
- Estorno (`REVERSAL`) como evento derivado, não edição de status.
- Imutabilidade de lançamentos postados.

---

# 13. Limitações atuais

- Sem parcelamentos, recorrência, conciliação, fluxo de caixa, projeções.
- Sem impacto em saldo de conta ao marcar PAID.
- Sem integração fornecedor/projeto na UI.
- Dashboard continua com `LegacyTransaction` / tabela antiga.
- Sem paginação, analytics, export.
- Sem command palette / atalhos Ctrl+S na tela de transações.

---

# 14. Antes vs Depois

## Antes (pré-3A.1)

```
Screen.Transactions → placeholder Text()
Tabela transactions (legado) → só Dashboard getRecent(10)
Sem TransactionType/Status
Sem validação de negócio
Sem auditoria TRANSACTION_*
Sem Money no motor novo
```

## Depois (pós-3A.1)

```
Screen.Transactions → TransactionsScreen (workstation)
Tabela financial_transactions → motor oficial
Transaction entity + enums + validator
TransactionService + audit semântica
BaseCrudViewModel + painel + popup + hover + badges
Legado isolado em LegacyTransactionRepository
```

**Mudança de natureza:** de **stub de navegação** para **primeiro núcleo real de contas a pagar/receber**.

---

# 15. Avaliação HONESTA

## O sistema agora possui fundação transacional real?

**Sim, de forma incremental e honesta.**

Existe entidade de domínio, persistência dedicada, serviço com validação, auditoria, UI operacional integrada ao shell workstation e separação do legado. Isso **não é** CRUD cosmético: há regras de status, Money, e preparação ledger.

**Porém** ainda não é motor financeiro completo: pagar uma transação **não move dinheiro contabilmente** — apenas atualiza estado e auditoria.

## Quais riscos ainda existem?

1. Dualidade legado vs motor novo (confusão operacional e de relatório).  
2. Status financeiros sem engine de regras temporais (OVERDUE).  
3. Expectativa de saldo imediato ao marcar PAID.  
4. Cobertura de testes nula no domínio transacional.

## O que ainda falta para o ledger?

1. Modelo de lançamentos (partidas, contas contábeis ou wallet interna).  
2. `PostingService` transacional (ACID) ao confirmar PAID.  
3. Preencher `ledger_entry_id` e bloquear edição pós-post.  
4. `REVERSAL` como operação derivada.  
5. Unificar saldo exibido = f(initial_balance) + Σ ledger.  
6. Migrar dashboard para ler motor novo ou projeção consolidada.

---

# Verificação

```bash
./gradlew compileKotlin test
# BUILD SUCCESSFUL
```

**Credenciais seed:** `admin` / `123` — criar conta em **Contas e Caixas** antes de lançar transação com `accountId` válido.

---

*Fim do relatório — Fase 3A.1 Transaction Foundation.*
