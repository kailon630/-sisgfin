# DELTA vs RETRATO — Reconciliação do Código Real

**Data:** 2026-08-10
**Escopo:** somente leitura — nenhum arquivo alterado
**Referência:** `docs/RETRATO_PROJETO.md` atualizado em 2026-08-10

---

## 1. Migrações

### 1.1 Listagem completa

```
V1__init.sql
V2__employees.sql
V3__user_management_and_audit.sql
V4__base_financial_registrations.sql
V5__money_precision.sql
V6__transaction_engine_foundation.sql
V7__transaction_workflow.sql
V8__transaction_installment_and_document.sql
V9__expense_categories.sql
V10__seed_expense_categories.sql
V11__supplier_document_unique.sql
V12__rename_user_role_operador.sql
V13__investment_account.sql
V14__budget_items.sql
V15__employee_payment_days.sql
V16__transaction_ofx_fitid.sql
V17__ofx_imports.sql
V18__transaction_reconciled_with.sql
V19__recurrence_templates.sql
V20__transaction_recurrence_fk.sql
V21__contracts.sql
V22__transaction_contract_fk.sql
V23__supplier_entity_type.sql
V24__employee_supplier_fk.sql
V25__employee_banking_fields.sql
V26__drop_employee_supplier_fk.sql
V27__transaction_charges.sql
V28__financial_projects.sql
V29__transaction_reversed_type.sql
```

### 1.2 V29 — na íntegra

```sql
-- C1: direção do estorno no cálculo de saldo
ALTER TABLE financial_transactions
    ADD COLUMN reversed_type VARCHAR(20) NULL;

COMMENT ON COLUMN financial_transactions.reversed_type IS
    'Tipo do lançamento original estornado. Preenchido apenas quando type = REVERSAL. Define a direção do estorno no cálculo de saldo.';

-- Backfill dos estornos existentes a partir do lançamento pai
UPDATE financial_transactions r
   SET reversed_type = o.type
  FROM financial_transactions o
 WHERE r.type = 'REVERSAL'
   AND r.parent_transaction_id = o.id
   AND r.reversed_type IS NULL;

CREATE INDEX idx_ft_reversed_type
    ON financial_transactions (reversed_type)
    WHERE type = 'REVERSAL';
```

---

## 2. Testes

### 2.1 Árvore completa

```
src/test/kotlin/br/com/sisgfin/
├── budget/
│   └── BudgetRealizedReversalBehaviorTest.kt
├── core/validation/
│   └── DocumentValidatorTest.kt
├── financial/money/
│   └── MoneyTest.kt
├── financial/transactions/
│   ├── EncargosNoSaldoTest.kt
│   ├── InstallmentCalculatorTest.kt
│   ├── PartialBalanceTest.kt
│   ├── PartialPaymentAccumulationTest.kt
│   ├── TerminalImmutabilityTest.kt
│   ├── TransactionQueryTest.kt
│   ├── TransactionValidatorTest.kt
│   └── TransferAndReversalTest.kt
│   └── workflow/
│       ├── OverdueEngineTest.kt
│       └── TransactionWorkflowTest.kt
├── ofx/
│   └── OfxParserTest.kt
├── payables/
│   ├── CounterpartyResolverTest.kt
│   ├── PayablesUiStateTest.kt
│   └── PayablesViewModelWriteTest.kt
├── payroll/
│   └── PayrollXlsxParserTest.kt
└── recurrence/
    └── RecurrenceEngineTest.kt
```

### 2.2 Arquivos ausentes na seção 13 do RETRATO

**EncargosNoSaldoTest.kt** — 4 testes (P0-5):
```
Q5a titulo PAID baixa unica - calculateBalance usa amount nao paidAmount - juros invisiveis
Q5b statement mostra paidAmount 1050 mas calculateBalance ve amount 1000 - divergencia 50
Q5c titulo PARTIAL - calculateBalance usa paidAmount que inclui juros - encargos visiveis
Q5d assimetria PARTIAL-PAID - juros da primeira baixa somem ao quitar status
```

**PartialPaymentAccumulationTest.kt** — 9 testes (P0-4):
```
T1 duas baixas 300 mais 700 produzem PAID com paidAmount acumulado de 1000
T2 duas baixas 300 mais 300 produzem PARTIAL com paidAmount acumulado de 600
T3 segunda baixa de 800 com saldo devedor de 700 e rejeitada com mensagem de saldo
T4 quitacao com encargos — PAID principalPaid 1000 paidAmount 1050 saldo 1050
T5 baixa integral 1000 produz PAID sem regressao
T6 tres baixas 400 mais 400 mais 200 produzem PAID com paidAmount de 1000
T7 saldo com duas baixas parciais acumuladas reflete total correto
T8a baixa de valor zero e rejeitada
T8b baixa de valor negativo e rejeitada
```

**TransactionQueryTest.kt** — 12 testes:
```
defaults — tipos e status vazios, eixo DUE, onlyActive true
aPagar() filtra somente EXPENSE
aPagar() equivale a filterActionRequired filtrado por EXPENSE
aPagar() nao inclui PAID nem CANCELED
aReceber() filtra somente INCOME
aReceber() usa os mesmos status que aPagar()
aPagar e aReceber tipos disjuntos
extrato() equivale a findStatementEntries com eixo PAYMENT
extrato() nao inclui status em aberto
extrato() eixo PAYMENT implica paymentDate como criterio de data
query com search e costCenterId
query com onlyActive false inclui inativos
```

**TerminalImmutabilityTest.kt** — 7 testes:
```
C2 update de amount em PAID lanca excecao
C2 update de accountId em PAID lanca excecao
C2 update de paymentDate em PAID lanca excecao
C2 update de notes em PAID e permitido
C2 update de categoryId em PAID e permitido
C2 update de amount em PENDING e permitido pela guarda de terminalidade
C2 PUT API usa o mesmo servico — guarda nao depende da UI
```

**PayablesViewModelWriteTest.kt** — 4 testes (F2-fix P0):
```
P0-1 recordPayment lanca excecao - errorMessage preenchido
P0-2 cancel lanca excecao - errorMessage preenchido
P0-3 duplicate lanca excecao - errorMessage preenchido
P0-6 PARTIAL 1000 com 300 pagos - markAsPaidFull envia saldo restante 700
```

**PayablesUiStateTest.kt** — 7 testes:
```
ALL mostra todos os itens
OVERDUE mostra apenas status OVERDUE
TODAY mostra apenas dueDate igual a hoje
THIS_WEEK inclui hoje e dias ate domingo
summary total inclui todos os itens carregados
EMPTY summary tem zeros
estado inicial tem tileFilter ALL e lista vazia
```

**BudgetRealizedReversalBehaviorTest.kt** — 2 testes:
```
R7 EXPENSE 1000 PAID e REVERSAL 1000 na mesma rubrica resulta em realizado 2000
R7 Demonstrativo trata REVERSAL como receita nao como reducao de despesa
```

**CounterpartyResolverTest.kt** — 7 testes:
```
nameFor retorna nome do fornecedor quando supplierId presente
nameFor retorna nome do funcionario quando apenas employeeId presente
nameFor prefere supplierId quando ambos presentes
nameFor retorna null quando ids ausentes
nameFor retorna null quando id nao encontrado no mapa
EMPTY retorna null para qualquer transacao
mapa com multiplos fornecedores resolve corretamente cada id
```

### 2.3 Contagem real

| Fonte | Quantidade |
|---|---|
| Retrato (seção 13) | 131 |
| Novos arquivos não listados no retrato | +60 |
| **Código real (`@Test` count)** | **191** |

Arquivos novos: EncargosNoSaldoTest (4) + PartialPaymentAccumulationTest (9) + TransactionQueryTest (12) + TerminalImmutabilityTest (7) + PayablesViewModelWriteTest (4) + PayablesUiStateTest (7) + BudgetRealizedReversalBehaviorTest (2) + CounterpartyResolverTest (7) = 52. Os 8 restantes estão em arquivos do retrato que receberam casos novos (TransactionValidatorTest e TransferAndReversalTest foram modificados nas sessões P0).

---

## 3. Histórico

### 3.1 `git log --oneline -40`

```
86d8464 feat: módulo Projetos, drag & drop nas importações e suporte a Adiantamento Salarial — v1.0.8
d082506 feat: ações rápidas em previsões não liquidadas na importação OFX — v1.0.7
45f371b feat: juros/multa na quitação, toggle ativo/inativo nos 4 CRUDs e bump v1.0.6
f7bf710 feat: tipo de vínculo em funcionários, WsDocumentField, Fase 9 e gaps de conciliação OFX
a7eeb2c fix: corrigir ícone MSI e aplicar design system (Onda 2) — v1.0.5
bf828df chore: bump packageVersion para 1.0.4
f7aead5 ci: adicionar passo de testes antes do build MSI
589d986 feat: design system refinement (Onda 1) + ícone oficial aplicado
798b6f5 feat: aplicar ícone oficial em todo o app e instalador MSI
415e3ea fix: incluir módulos java.sql e java.naming no JRE empacotado
8575510 fix: adicionar permissions contents:write para criar Release
431bf1e fix: aumentar heap do Gradle para evitar OOM ao compilar no Windows runner
b0b3bbd feat: versão inicial — migração PostgreSQL, startup DB config popup, GitHub Actions MSI
```

`git log --oneline --since="2026-07-27"` → **sem saída**.

### 3.2 Commits posteriores a 86d8464

**Não existe nenhum commit posterior a 86d8464.** `86d8464` (2026-07-27) é o HEAD.

Todo o trabalho P0-1 a P0-5, V29, novos testes, `gradle.properties` (VFORK), `build.gradle.kts` (jvmArgs) — tudo está no working tree sem commit. O git status na abertura da sessão mostrava `M` (modified tracked) e `??` (untracked) para os arquivos das sessões P0.

---

## 4. Documentos de correção

**Arquivos encontrados:**
- `docs/P0_4_LIQUIDACAO.md` — spec da tarefa P0-4 (não relatório)
- `docs/relatorios/F2_FIX_P0.md` — relatório de P0-1, P0-2, P0-3
- `docs/relatorios/P0_4_LIQUIDACAO.md` — relatório de P0-4
- `docs/relatorios/P0_5_ENCARGOS_NO_SALDO.md` — relatório de P0-5

**Sequência P0 identificada:**

| ID | Descrição | Status |
|---|---|---|
| P0-1 | Erros silenciados em `markAsPaidFull`, `cancelTransaction`, `duplicateTransaction` — `onFailure` descartava exceção; corrigido para `AppLogger.error + _uiState.errorMessage` | Concluído |
| P0-2 | "Quitar" visível para OPERADOR — corrigido via `canPay: Boolean` em `TransactionContextMenu` e `viewModel.canConfirmPayment()` em `PayablesScreen` | Concluído |
| P0-3 | `markAsPaidFull` passava `tx.amount` em vez de saldo restante para PARTIAL — corrigido para `tx.outstandingPrincipal` | Concluído |
| P0-4 | Segunda baixa parcial sobrescrevia `paidAmount`; `PARTIAL → PARTIAL` bloqueado; acumulação implementada; `principalPaid`/`outstandingPrincipal` adicionados; `TransactionStateMachine` e `validatePayment` corrigidos | Concluído — 187 testes |
| P0-5 | Verificação pós-P0-4: encargos invisíveis ao saldo em PAID; assimetria PARTIAL→PAID; divergência statement vs `calculateBalance` | Documentado, não corrigido |

`docs/relatorios/P0_4_LIQUIDACAO.md:4`:
```
Status: Concluído — BUILD SUCCESSFUL, 187/187 testes (9 novos P0-4 + atualizações existentes)
```

Após P0-5 foram adicionados 4 testes em `EncargosNoSaldoTest`, totalizando **191** na data deste relatório.

---

## 5. Estado atual das funções críticas

### 5.1 `FinancialAccountService.calculateBalance()` — `FinancialServices.kt:58-85`

```kotlin
fun calculateBalance(accountId: Int): Money {
    val account = accountRepository.findById(accountId) ?: return Money.ZERO
    val income         = transactionRepository.sumPaid(accountId, TransactionType.INCOME)
    val expense        = transactionRepository.sumPaid(accountId, TransactionType.EXPENSE)
    val adjustment     = transactionRepository.sumPaid(accountId, TransactionType.ADJUSTMENT)
    val transferIn     = transactionRepository.sumPaidTransferIn(accountId)
    val transferOut    = transactionRepository.sumPaidTransferOut(accountId)
    val incomePartial  = transactionRepository.sumPartialPaid(accountId, TransactionType.INCOME)
    val expensePartial = transactionRepository.sumPartialPaid(accountId, TransactionType.EXPENSE)
    val reversalCredit = transactionRepository.sumPaidReversalOf(
        accountId, listOf(TransactionType.EXPENSE)
    )
    val reversalDebit = transactionRepository.sumPaidReversalOf(
        accountId, listOf(TransactionType.INCOME, TransactionType.ADJUSTMENT)
    )
    return br.com.sisgfin.financial.accounts.AccountBalanceFormula.compute(
        initialBalance = account.initialBalance,
        income = income,
        incomePartial = incomePartial,
        expense = expense,
        expensePartial = expensePartial,
        adjustment = adjustment,
        transferIn = transferIn,
        transferOut = transferOut,
        reversalCredit = reversalCredit,
        reversalDebit = reversalDebit
    )
}
```

### 5.2 `TransactionRepository.sumPaid()` — `TransactionRepository.kt:361-374`

```kotlin
fun sumPaid(accountId: Int, type: TransactionType): Money = transaction {
    val sumExpr = FinancialTransactionsTable.amount.sum()
    FinancialTransactionsTable
        .select(sumExpr)
        .where {
            (FinancialTransactionsTable.accountId eq accountId) and
            (FinancialTransactionsTable.type eq type.name) and
            (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
            (FinancialTransactionsTable.isActive eq true)
        }
        .firstOrNull()
        ?.get(sumExpr)
        ?.toMoney() ?: Money.ZERO
}
```

### 5.3 `TransactionRepository.sumPartialPaid()` — `TransactionRepository.kt:377-390`

```kotlin
fun sumPartialPaid(accountId: Int, type: TransactionType): Money = transaction {
    val sumExpr = FinancialTransactionsTable.paidAmount.sum()
    FinancialTransactionsTable
        .select(sumExpr)
        .where {
            (FinancialTransactionsTable.accountId eq accountId) and
            (FinancialTransactionsTable.type eq type.name) and
            (FinancialTransactionsTable.status eq TransactionStatus.PARTIAL.name) and
            (FinancialTransactionsTable.isActive eq true)
        }
        .firstOrNull()
        ?.get(sumExpr)
        ?.toMoney() ?: Money.ZERO
}
```

### 5.4 `AccountBalanceFormula.kt` — arquivo inteiro

```kotlin
package br.com.sisgfin.financial.accounts

import br.com.sisgfin.financial.money.Money

/**
 * Fórmula única de saldo de conta (RN-04 estendida).
 * Usada por FinancialAccountService.calculateBalance e
 * TransactionRepository.openingBalance — qualquer alteração
 * deve manter as duas chamadas alinhadas.
 *
 * Estornos são dirigidos por reversedType do lançamento original:
 * - crédito: estorno de EXPENSE (devolve ao caixa)
 * - débito: estorno de INCOME / ADJUSTMENT (retira do caixa)
 */
object AccountBalanceFormula {

    fun compute(
        initialBalance: Money,
        income: Money = Money.ZERO,
        incomePartial: Money = Money.ZERO,
        expense: Money = Money.ZERO,
        expensePartial: Money = Money.ZERO,
        adjustment: Money = Money.ZERO,
        transferIn: Money = Money.ZERO,
        transferOut: Money = Money.ZERO,
        reversalCredit: Money = Money.ZERO,
        reversalDebit: Money = Money.ZERO
    ): Money =
        initialBalance +
            income + incomePartial + adjustment + transferIn + reversalCredit -
            expense - expensePartial - transferOut - reversalDebit
}
```

### 5.5 `TransactionService.recordPayment()` — `TransactionService.kt:364-433`

```kotlin
fun recordPayment(
    id: Int,
    paymentDate: LocalDateTime,
    paidAmount: Money,
    interestAmount: Money? = null,
    fineAmount: Money? = null
) {
    requirePermission(Permission.ConfirmPayment)
    val existing = repository.findById(id)
        ?: throw IllegalArgumentException("Transação não encontrada.")
    if (!TransactionStateMachine.allowsPayment(existing.status)) {
        throw IllegalStateException("Status ${existing.status.displayName} não permite quitação.")
    }

    val juros = interestAmount ?: Money.ZERO
    val multa = fineAmount ?: Money.ZERO

    TransactionValidator.validatePayment(
        outstanding = existing.outstandingPrincipal,
        principal   = paidAmount,
        interest    = interestAmount,
        fine        = fineAmount,
        paymentDate = paymentDate,
        issueDate   = existing.issueDate
    )

    // Acumula sobre o que já existe — paidAmount armazena (principal + juros + multa) cumulativos
    val newPaidAmount     = (existing.paidAmount     ?: Money.ZERO) + paidAmount + juros + multa
    val newInterestAmount = (existing.interestAmount ?: Money.ZERO) + juros
    val newFineAmount     = (existing.fineAmount     ?: Money.ZERO) + multa
    val newPrincipalPaid  = newPaidAmount - newInterestAmount - newFineAmount

    val newStatus = TransactionStateMachine.resolveStatusAfterPayment(
        existing.amount.value,
        newPrincipalPaid.value
    )
    TransactionStateMachine.assertTransition(existing.status, newStatus)

    val cashThisBaixa = paidAmount + juros + multa
    val newOutstanding  = existing.outstandingPrincipal - paidAmount

    val updated = existing.copy(
        status         = newStatus,
        paymentDate    = paymentDate,
        paidAmount     = newPaidAmount,
        interestAmount = if (newInterestAmount.isZero()) null else newInterestAmount,
        fineAmount     = if (newFineAmount.isZero()) null else newFineAmount,
        updatedAt      = LocalDateTime.now()
    )
    TransactionValidator.validateForSave(updated, existing)
    repository.update(updated)

    ledgerService.recordPayment(updated, cashThisBaixa, paymentDate)

    val timelineType = if (newStatus == TransactionStatus.PAID) {
        TimelineEventType.PAYMENT
    } else {
        TimelineEventType.PARTIAL_PAYMENT
    }
    val timelineDesc = if (newStatus == TransactionStatus.PAID) {
        "Quitada — $cashThisBaixa"
    } else {
        "Pagamento parcial — $paidAmount (saldo devedor: $newOutstanding)"
    }
    addTimeline(id, timelineType, timelineDesc, cashThisBaixa, existing.status, newStatus)

    val auditAction = if (newStatus == TransactionStatus.PAID) "TRANSACTION_PAID" else "TRANSACTION_PARTIAL_PAYMENT"
    audit(auditAction, id, auditDetail(newStatus, existing.status, cashThisBaixa))
    audit("TRANSACTION_STATUS_CHANGED", id, auditDetail(newStatus, existing.status, cashThisBaixa))
}
```

### 5.6 `TransactionService.createTransfer()` — `TransactionService.kt:246-302`

```kotlin
fun createTransfer(
    sourceAccountId: Int,
    destinationAccountId: Int,
    amount: Money,
    date: LocalDateTime,
    description: String,
    notes: String? = null,
    costCenterId: Int? = null,
    categoryId: Int? = null
): Pair<Int, Int> {
    if (sourceAccountId == destinationAccountId) {
        throw IllegalArgumentException("Conta de origem e destino não podem ser iguais.")
    }
    if (amount.isZero() || amount.isNegative()) {
        throw IllegalArgumentException("Valor da transferência deve ser maior que zero.")
    }
    validateAccount(sourceAccountId)
    validateAccount(destinationAccountId)

    val userId = sessionManager.currentUser.value?.id
    val now = LocalDateTime.now()

    val source = Transaction(
        type = TransactionType.TRANSFER,
        status = TransactionStatus.PENDING,
        amount = amount,
        description = description,
        issueDate = now,
        dueDate = date,
        accountId = sourceAccountId,
        costCenterId = costCenterId,
        categoryId = categoryId,
        notes = notes,
        createdBy = userId,
        createdAt = now,
        updatedAt = now
    )
    val sourceId = repository.insert(source)
    addTimeline(sourceId, TimelineEventType.TRANSFER_OUT,
        "Transferência de $amount enviada para conta #$destinationAccountId",
        amount, null, source.status)
    audit("TRANSFER_CREATED", sourceId, "source=$sourceAccountId;dest=$destinationAccountId;amount=$amount")

    val destination = source.copy(
        id = 0,
        accountId = destinationAccountId,
        description = "Recebimento: $description",
        parentTransactionId = sourceId
    )
    val destinationId = repository.insert(destination)
    addTimeline(destinationId, TimelineEventType.TRANSFER_IN,
        "Transferência de $amount recebida da conta #$sourceAccountId",
        amount, null, destination.status)
    audit("TRANSFER_CREATED", destinationId, "source=$sourceAccountId;dest=$destinationAccountId;amount=$amount;pair=#$sourceId")

    return sourceId to destinationId
}
```

### 5.7 `LedgerService.kt` — arquivo inteiro

```kotlin
package br.com.sisgfin.financial.ledger

import br.com.sisgfin.financial.money.Money
import br.com.sisgfin.financial.transactions.Transaction
import java.time.LocalDateTime

/**
 * Gancho arquitetural para o ledger (Fase ledger futura).
 * Implementação no-op nesta fase.
 */
class LedgerService {

    fun recordPayment(
        transaction: Transaction,
        paidAmount: Money,
        paymentDate: LocalDateTime
    ) {
        // LedgerService.recordPayment() — reservado para posting contábil
    }

    fun recordStatusChange(transaction: Transaction) {
        // reservado
    }
}
```

---

## 6. Verificação pontual

| # | Pergunta | Resposta | Evidência |
|---|---|---|---|
| 6.1 | `sumPaid()` agrega `amount` ou `paid_amount` para PAID? | **`amount`** | `TransactionRepository.kt:362`: `FinancialTransactionsTable.amount.sum()` |
| 6.2 | Juros e multa saem do saldo quando título está PAID? | **NÃO** | `sumPaid` usa `SUM(amount)`; `interest_amount`/`fine_amount` não consultados por `calculateBalance` |
| 6.3 | PARTIAL→PAID troca de `paid_amount` para `amount`? | **SIM** | `expensePartial = SUM(paid_amount)` sobre PARTIAL; `expense = SUM(amount)` sobre PAID; transição de status muda o balde. `AccountBalanceFormula.kt:29-31` |
| 6.4 | `createTransfer()` cria as duas pernas com PENDING? | **SIM** | `TransactionService.kt:269`: `status = TransactionStatus.PENDING`; `destination = source.copy(...)` herda |
| 6.5 | `recordPayment()` está em único `transaction {}`? | **NÃO** | Cada chamada ao repositório abre transação Exposed independente. Não há atomicidade entre update + timeline + audit |
| 6.6 | Existe coluna `version` ou lock otimista? | **NÃO** | `FinancialTransactionsTable.kt` sem coluna version; UPDATE usa apenas `WHERE id = entity.id` |
| 6.7 | Existe tabela de baixas (`transaction_payments`)? | **NÃO** | Nenhum arquivo ou migration com esse nome. `docs/specs/SPEC_TRANSACTION_PAYMENTS.md` existe mas a tabela não foi implementada |
| 6.8 | `LedgerService.recordPayment()` continua no-op? | **SIM** | `LedgerService.kt:13-19`: corpo vazio |

---

## 7. Build

```
> Task :test
> Task :check
> Task :build

BUILD SUCCESSFUL in 24s
10 actionable tasks: 1 executed, 9 up-to-date
```

Compila: SIM. Testes passam: SIM — 191 testes, nenhuma falha.

---

## DELTA vs RETRATO

| item | retrato diz | código diz | divergente? |
|---|---|---|---|
| Última migration | V28 | V29 (`reversed_type` + backfill + índice) | **SIM** |
| Total de testes | 131 | 191 | **SIM** |
| Arquivos de teste listados | 11 arquivos | 19 arquivos | **SIM** |
| `recordPayment` — acumula ou sobrescreve? | não mencionado | acumula (P0-4 concluído) | **SIM** |
| `paidAmount` semântica | não mencionado | principal + juros + multa cumulativos (pós-P0-4) | **SIM** |
| `principalPaid` / `outstandingPrincipal` | não mencionados | propriedades computadas em `Transaction.kt` | **SIM** |
| `PARTIAL → PARTIAL` na StateMachine | não mencionado | permitido (adicionado em P0-4) | **SIM** |
| Commits após 2026-07-27 | n/a | zero — todo P0 está no working tree sem commit | **SIM** |
| `sumPaid` — coluna agregada para PAID | não mencionado | `SUM(amount)` (não `paid_amount`) | — |
| `sumPartialPaid` — coluna agregada para PARTIAL | mencionado como implementado | `SUM(paid_amount)` | OK |
| Juros/multa no saldo quando PAID | não mencionado | invisíveis — `SUM(amount)` ignora encargos | — |
| `LedgerService` | não mencionado | no-op confirmado | — |
| `transaction_payments` | não mencionado | NÃO EXISTE | — |
| Build | BUILD SUCCESSFUL | BUILD SUCCESSFUL, 191 testes | OK (contagem difere) |
| Versão do pacote | 1.0.8 | 1.0.8 | OK |
