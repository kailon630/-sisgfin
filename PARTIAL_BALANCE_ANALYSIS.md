# PARTIAL_BALANCE_ANALYSIS.md
> Análise: impacto de PARTIAL no cálculo de saldo
> Data: 2026-08-10 | SisgFin

---

## 1. Problema Confirmado

`calculateBalance()` ignora transações com `status = PARTIAL`. Quando um usuário paga R$400 de uma despesa de R$1.000, o dinheiro saiu da conta bancária real mas o saldo no sistema permanece inalterado.

**Evidência direta:**

```kotlin
// TransactionRepository.kt:315-323
FinancialTransactionsTable
    .select(sumExpr)
    .where {
        ...
        (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) // ← só PAID
        ...
    }
```

`calculateBalance()` em `FinancialServices.kt` chama `sumPaid()` para todos os tipos. `sumPaid()` filtra exclusivamente `status = 'PAID'`. `PARTIAL` nunca entra.

---

## 2. Código Atual

### `calculateBalance()` — `FinancialServices.kt`

```kotlin
fun calculateBalance(accountId: Int): Money {
    val account = accountRepository.findById(accountId) ?: return Money.ZERO
    val income      = transactionRepository.sumPaid(accountId, TransactionType.INCOME)
    val expense     = transactionRepository.sumPaid(accountId, TransactionType.EXPENSE)
    val reversal    = transactionRepository.sumPaid(accountId, TransactionType.REVERSAL)
    val adjustment  = transactionRepository.sumPaid(accountId, TransactionType.ADJUSTMENT)
    val transferIn  = transactionRepository.sumPaidTransferIn(accountId)
    val transferOut = transactionRepository.sumPaidTransferOut(accountId)
    return account.initialBalance + income + reversal + adjustment + transferIn - expense - transferOut
}
```

### `sumPaid()` — `TransactionRepository.kt:311-324`

```
Tabela:   financial_transactions
Colunas:  SUM(amount)          ← NÃO é paidAmount
Filtros:  accountId = ?
          type = ?
          status = 'PAID'      ← exclui PARTIAL
          isActive = true
Resultado: soma dos valores nominais de todas as transações PAGAS integralmente
```

### `sumPaidTransferIn()` — `TransactionRepository.kt:352-363`

```
Tabela:   financial_transactions
Colunas:  SUM(amount)
Filtros:  accountId = ?
          type = 'TRANSFER'
          status = 'PAID'
          isActive = true
          parentTransactionId IS NOT NULL   ← lado destino da transferência
```

### `sumPaidTransferOut()` — `TransactionRepository.kt:366-378`

```
Tabela:   financial_transactions
Colunas:  SUM(amount)
Filtros:  accountId = ?
          type = 'TRANSFER'
          status = 'PAID'
          isActive = true
          parentTransactionId IS NULL       ← lado origem da transferência
```

### `openingBalance()` — `TransactionRepository.kt:504-512`

Usada na tela de extrato. Mesma lógica: chama funções privadas `sumPaidBefore()` que também filtram só `status = 'PAID'`. **Tem o mesmo problema para o saldo de abertura do extrato.**

---

## 3. Impacto de PARTIAL

### Qual coluna usar para PARTIAL?

**Para PAID:**
- `validatePayment()` garante `paidAmount <= amount`
- `resolveStatusAfterPayment()` garante `paidAmount >= amount` para PAID
- Portanto: `paidAmount == amount` para todo PAID — as duas colunas são equivalentes

**Evidência no código:**

```kotlin
// TransactionValidator.kt:97-99
if (paidAmount.compareTo(total) > 0) {
    throw IllegalArgumentException("Valor pago não pode exceder o valor da transação.")
}

// TransactionStateMachine.kt:81
paidAmount.compareTo(totalAmount) >= 0 -> TransactionStatus.PAID
```

**Para PARTIAL:**
- `paidAmount < amount` (garantido pelo fluxo)
- `amount` = dívida total — NÃO representa caixa movimentado
- `paidAmount` = dinheiro efetivamente saído/entrado — valor correto para saldo

```
PARTIAL de EXPENSE
→ deve usar paidAmount   (dinheiro que saiu da conta)

PARTIAL de INCOME
→ deve usar paidAmount   (dinheiro que entrou na conta)
```

### Semântica do `recordPayment()` para PARTIAL

`recordPayment()` **substitui** `paidAmount`, não acumula:

```kotlin
// TransactionService.kt:339-346
val updated = existing.copy(
    status = newStatus,
    paidAmount = paidAmount,   // ← REPLACE, não += paidAmount
    ...
)
```

Portanto:
- Primeiro pagamento parcial (R$400): `paidAmount = 400`
- Segundo pagamento parcial (R$600): `paidAmount = 600` (substituído, não somado)
- Pagamento final (R$1000): `paidAmount = 1000`, status = PAID

O valor `paidAmount` sempre reflete o total acumulado pago até o momento — não o incremento da última operação. Isso é correto para a fórmula de saldo.

---

## 4. Solução Mínima Recomendada

**Adicionar uma função `sumPartialPaid()` no repositório e incluí-la em `calculateBalance()`.**

Essa abordagem:
- Não altera `sumPaid()` — sem risco de regressão para PAID
- Não altera a assinatura de nenhuma função existente
- Não muda o banco de dados
- Não muda Transaction, nem Service, nem validadores
- É reversível

**Nova função a criar em `TransactionRepository.kt`:**

```kotlin
fun sumPartialPaid(accountId: Int, type: TransactionType): Money = transaction {
    val sumExpr = FinancialTransactionsTable.paidAmount.sum()   // ← paidAmount, não amount
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

**Alteração em `FinancialAccountService.calculateBalance()`:**

```kotlin
fun calculateBalance(accountId: Int): Money {
    val account = accountRepository.findById(accountId) ?: return Money.ZERO
    val income          = transactionRepository.sumPaid(accountId, INCOME)
    val expense         = transactionRepository.sumPaid(accountId, EXPENSE)
    val reversal        = transactionRepository.sumPaid(accountId, REVERSAL)
    val adjustment      = transactionRepository.sumPaid(accountId, ADJUSTMENT)
    val transferIn      = transactionRepository.sumPaidTransferIn(accountId)
    val transferOut     = transactionRepository.sumPaidTransferOut(accountId)
    // Adições:
    val incomePartial   = transactionRepository.sumPartialPaid(accountId, INCOME)
    val expensePartial  = transactionRepository.sumPartialPaid(accountId, EXPENSE)
    return account.initialBalance +
           income + incomePartial +
           reversal + adjustment + transferIn -
           expense - expensePartial - transferOut
}
```

**Mesma alteração em `openingBalance()`** para consistência com o extrato (ver seção 6).

---

## 5. Riscos da Alteração

| Risco | Probabilidade | Detalhe |
|-------|--------------|---------|
| Double counting PARTIAL→PAID | **Zero** | Quando PARTIAL vira PAID, o registro sai do bucket PARTIAL (status != PARTIAL) e entra no bucket PAID. Sem sobreposição. |
| Quebra de PAID existente | **Zero** | `sumPaid()` não é alterado. REVERSALs, ADJUSTMENTs continuam idênticos. |
| `paidAmount` null em PARTIAL | **Possível** | Se um registro PARTIAL existir com `paidAmount = null` no banco (dado corrompido), `SUM(paidAmount)` retorna null → `?.toMoney() ?: Money.ZERO`. Sem crash, resultado = 0. |
| Extrato inconsistente se `openingBalance()` não for atualizado | **Certo** | Sem atualizar `openingBalance()`, saldo no extrato diverge do saldo no painel. |
| Impacto em dados existentes | **Imediato** | Transações PARTIAL já existentes no banco passam a contar no saldo. O saldo exibido vai mudar para refletir a realidade financeira. **Isso é o comportamento correto**, mas precisa ser comunicado. |

---

## 6. Outros Tipos Afetados

### `sumPaid()` é usado para:

| Tipo | Usado em `calculateBalance()` | Afetado pela mudança? |
|------|-------------------------------|----------------------|
| INCOME | Sim | Não — nova função separada `sumPartialPaid(INCOME)` |
| EXPENSE | Sim | Não — nova função separada `sumPartialPaid(EXPENSE)` |
| REVERSAL | Sim | Não — REVERSAL nunca tem status PARTIAL (criado diretamente como PAID) |
| ADJUSTMENT | Sim | Não — ADJUSTMENT também sempre PAID quando registrado |
| TRANSFER | Não (usa funções próprias) | Não |

### TRANSFER + PARTIAL

Existe o estado PARTIAL para TRANSFER? Tecnicamente sim — o state machine permite. Mas `createTransfer()` cria sempre PENDING. Um usuário poderia pagar parcialmente um lado da transferência via `recordPayment()` → PARTIAL. Esse cenário não é tratado pela solução acima.

**Decisão necessária:** transferência parcialmente paga — devo incluir no saldo? Se sim, `sumPartialPaid()` precisaria de uma variante para TRANSFER também. Se não, o comportamento atual (ignorar) é mantido.

### `openingBalance()` — segundo arquivo que precisa ser corrigido

`openingBalance()` usa funções privadas `sumPaidBefore()`. Para corrigir o extrato, precisaria de uma função privada `sumPartialPaidBefore(accountId, type, before)` equivalente.

**Arquivos afetados:**
1. `TransactionRepository.kt` — `sumPartialPaid()` + `sumPartialPaidBefore()` (private)
2. `FinancialServices.kt` — `calculateBalance()` + `openingBalance()` via repositório

---

## 7. Estorno

### Estado atual do estorno

```kotlin
// TransactionService.kt:263-266
if (original.status != TransactionStatus.PAID) {
    throw IllegalStateException(
        "Apenas lançamentos com status Pago podem ser estornados..."
    )
}
```

**PARTIAL não pode ser estornado.** Confirmado: `reverseTransaction()` rejeita qualquer status != PAID.

### O estorno de PAID não é afetado pela correção

Um REVERSAL é criado com:
```kotlin
type = REVERSAL
status = PAID      // ← entra em sumPaid(REVERSAL) com amount = original.amount
amount = original.amount
paidAmount = original.amount
```

O `calculateBalance()` soma REVERSAL na direção positiva (`+reversal`). O REVERSAL de uma EXPENSE PAID de R$1.000 soma +R$1.000, revertendo exatamente a subtração feita pela despesa original.

**Cenário verificado:**
```
saldo inicial: 10.000
despesa PAID: -1.000 → saldo = 9.000
REVERSAL da despesa: +1.000 → saldo = 10.000  ✓
```

A correção de PARTIAL não toca nesse caminho.

---

## 8. Cenários Esperados

| Tipo | Valor (amount) | paidAmount | Status | Saldo atual | Saldo correto | OK? |
|------|---------------|-----------|--------|-------------|---------------|-----|
| EXPENSE | 1.000 | 0 | PENDING | 0 | 0 | ✓ |
| EXPENSE | 1.000 | 400 | PARTIAL | **0** | **-400** | ✗ |
| EXPENSE | 1.000 | 1.000 | PAID | -1.000 | -1.000 | ✓ |
| INCOME | 1.000 | 0 | PENDING | 0 | 0 | ✓ |
| INCOME | 1.000 | 400 | PARTIAL | **0** | **+400** | ✗ |
| INCOME | 1.000 | 1.000 | PAID | +1.000 | +1.000 | ✓ |
| REVERSAL | 1.000 | 1.000 | PAID | +1.000 | +1.000 | ✓ |
| EXPENSE PARTIAL→PAID | 1.000 | 400→1.000 | PAID | -1.000 | -1.000 | ✓ (sem double count) |

**PARTIAL→PAID sem double count explicado:**

Quando a despesa PARTIAL (paidAmount=400) recebe pagamento final (paidAmount=1000) → status vira PAID:
- `sumPartialPaid(EXPENSE)`: SELECT WHERE status='PARTIAL' → zero (registro agora é PAID)
- `sumPaid(EXPENSE)`: SELECT WHERE status='PAID' → 1.000 (usa `amount=1.000`)
- Resultado: -1.000 total. Correto. Sem double count.

---

## 9. Testes Necessários

Testes existentes relacionados ao saldo: `TransferAndReversalTest.kt` (fórmula RN-04 em memória pura, sem banco, sem PARTIAL).

**Casos novos necessários** (não criar ainda):

| # | Cenário | O que verificar |
|---|---------|----------------|
| T1 | EXPENSE PARTIAL paidAmount=400, amount=1000 | `calculateBalance()` = initialBalance - 400 |
| T2 | INCOME PARTIAL paidAmount=400, amount=1000 | `calculateBalance()` = initialBalance + 400 |
| T3 | EXPENSE PAID paidAmount=1000, amount=1000 | `calculateBalance()` = initialBalance - 1000 (sem regressão) |
| T4 | INCOME PAID paidAmount=1000, amount=1000 | `calculateBalance()` = initialBalance + 1000 (sem regressão) |
| T5 | EXPENSE REVERSAL após PAID | `calculateBalance()` = initialBalance (sem regressão) |
| T6 | EXPENSE PARTIAL paidAmount=400 → segundo pagamento → PAID | `calculateBalance()` passa de -400 para -1000, sem double count |
| T7 | EXPENSE PARTIAL e EXPENSE PAID na mesma conta | `calculateBalance()` = initialBalance - 400 - 1000 = -1400 |
| T8 | `openingBalance()` com PARTIAL presente | mesmo resultado que `calculateBalance()` para o mesmo período |

Todos os testes T1–T8 requerem banco de dados real para testar o comportamento de `calculateBalance()` / `openingBalance()`.

---

## 10. Arquivos que Precisarão Ser Alterados

| Arquivo | Alteração necessária | Linhas afetadas |
|---------|---------------------|----------------|
| `TransactionRepository.kt` | Adicionar `sumPartialPaid(accountId, type)` | Nova função, ~14 linhas |
| `TransactionRepository.kt` | Adicionar `sumPartialPaidBefore(accountId, type, before)` (private) | Nova função, ~15 linhas |
| `FinancialServices.kt` | Modificar `calculateBalance()` para incluir `incomePartial` e `expensePartial` | +4 linhas, 1 linha alterada |
| `TransactionRepository.kt` | Modificar `openingBalance()` para incluir PARTIAL | +4 linhas, 1 linha alterada |

**Total: 2 arquivos, ~35 linhas novas, 2 linhas alteradas.**

Nenhuma mudança em banco de dados, schema, entidades, validadores, service ou UI.
