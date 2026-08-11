# PARTIAL_BALANCE_IMPLEMENTATION.md
> Implementação da correção de PARTIAL no cálculo de saldo
> Data: 2026-08-10 | SisgFin

---

## 1. Resumo

Correção implementada com sucesso. Lançamentos com `status = PARTIAL` agora contribuem para o saldo da conta financeira usando `paidAmount` (valor efetivamente pago), tanto em `calculateBalance()` quanto em `openingBalance()`. Nenhum comportamento existente foi alterado. 125 testes passando, 0 falhas.

---

## 2. Arquivos Alterados

| Arquivo | Tipo de alteração |
|---------|------------------|
| `src/main/kotlin/br/com/sisgfin/financial/transactions/TransactionRepository.kt` | Adição de 2 funções novas + modificação de `openingBalance()` |
| `src/main/kotlin/br/com/sisgfin/FinancialServices.kt` | Modificação de `calculateBalance()` |
| `src/test/kotlin/br/com/sisgfin/financial/transactions/PartialBalanceTest.kt` | Arquivo novo — 11 testes |

---

## 3. Alterações Realizadas

### `TransactionRepository.kt`

**Função nova:** `sumPartialPaid(accountId, type)` — pública

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

- Usa `paidAmount.sum()` — coluna correta para valor parcialmente pago
- Filtra `status = 'PARTIAL'` — sem sobreposição com `sumPaid()` que filtra `PAID`
- Retorna `Money.ZERO` se não houver resultado (nulo seguro)

**Função nova:** `sumPartialPaidBefore(accountId, type, before)` — privada

```kotlin
private fun sumPartialPaidBefore(accountId: Int, type: TransactionType, before: LocalDate): Money = transaction {
    val sumExpr = FinancialTransactionsTable.paidAmount.sum()
    FinancialTransactionsTable.select(sumExpr)
        .where {
            (FinancialTransactionsTable.accountId eq accountId) and
            (FinancialTransactionsTable.type eq type.name) and
            (FinancialTransactionsTable.status eq TransactionStatus.PARTIAL.name) and
            (FinancialTransactionsTable.isActive eq true) and
            (FinancialTransactionsTable.paymentDate less before.atStartOfDay())
        }
        .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
}
```

- Mesma lógica de `sumPaidBefore()`, mas para PARTIAL com `paidAmount`
- Usada por `openingBalance()` para consistência com o extrato

**Modificação:** `openingBalance()`

```kotlin
// Antes:
return initialBalance + income + reversal + adjustment + transferIn - expense - transferOut

// Depois:
val incomePartial   = sumPartialPaidBefore(accountId, TransactionType.INCOME, before)
val expensePartial  = sumPartialPaidBefore(accountId, TransactionType.EXPENSE, before)
return initialBalance + income + incomePartial + reversal + adjustment + transferIn - expense - expensePartial - transferOut
```

### `FinancialServices.kt`

**Modificação:** `calculateBalance()`

```kotlin
// Antes:
return account.initialBalance + income + reversal + adjustment + transferIn - expense - transferOut

// Depois:
val incomePartial  = transactionRepository.sumPartialPaid(accountId, TransactionType.INCOME)
val expensePartial = transactionRepository.sumPartialPaid(accountId, TransactionType.EXPENSE)
return account.initialBalance + income + incomePartial + reversal + adjustment + transferIn - expense - expensePartial - transferOut
```

---

## 4. Testes Criados

**Arquivo:** `PartialBalanceTest.kt` — 11 testes

| Teste | Cenário | Resultado esperado |
|-------|---------|-------------------|
| T1 | Expense PARTIAL paidAmount=400, initial=10.000 | 9.600,00 |
| T2 | Income PARTIAL paidAmount=400, initial=10.000 | 10.400,00 |
| T3 | Expense PAID amount=1.000 (sem regressão) | 9.000,00 |
| T4 | Income PAID amount=1.000 (sem regressão) | 11.000,00 |
| T5 | Expense PAID + Reversal (sem regressão) | 10.000,00 |
| T6a | Estado PARTIAL paidAmount=400 | -400 no saldo |
| T6b | Mesmo lançamento após virar PAID | -1.000 no saldo |
| T6c | Double counting explicitamente verificado como impossível | assert negativo |
| T7 | PARTIAL 400 + PAID 1.000 na mesma conta | -1.400 total |
| T8 | Fórmula de openingBalance com PARTIAL | -1.400 de abertura |
| Cenário completo | PENDING → PARTIAL → PAID em sequência | 10.000 → 9.600 → 9.000 |

---

## 5. Testes Executados

```
./gradlew test
```

**Resultado:** BUILD SUCCESSFUL

| Suite | Testes | Falhas |
|-------|--------|--------|
| PartialBalanceTest (novos) | 11 | 0 |
| InstallmentCalculatorTest | 15 | 0 |
| TransactionValidatorTest | 17 | 0 |
| TransferAndReversalTest | 18 | 0 |
| TransactionWorkflowTest | 3 | 0 |
| MoneyTest | 6 | 0 |
| DocumentValidatorTest | 18 | 0 |
| OfxParserTest | 12 | 0 |
| PayrollXlsxParserTest | 8 | 0 |
| RecurrenceEngineTest | 17 | 0 |
| **Total** | **125** | **0** |

---

## 6. Resultado dos Testes

```
BUILD SUCCESSFUL in 44s
10 actionable tasks: 4 executed, 6 up-to-date
```

Todos os 125 testes passaram. Nenhuma regressão introduzida.

---

## 7. Comparação Antes/Depois

### T1 — Expense PARTIAL

```
Antes da correção:
  Saldo inicial:   R$ 10.000,00
  Despesa PARTIAL: R$ 1.000,00 / paidAmount R$ 400,00
  Saldo calculado: R$ 10.000,00  ← ERRADO

Depois da correção:
  Saldo inicial:   R$ 10.000,00
  Despesa PARTIAL: R$ 1.000,00 / paidAmount R$ 400,00
  Saldo calculado: R$  9.600,00  ← CORRETO
```

### T2 — Income PARTIAL

```
Antes da correção:
  Saldo inicial:    R$ 10.000,00
  Receita PARTIAL:  R$ 1.000,00 / paidAmount R$ 400,00
  Saldo calculado:  R$ 10.000,00  ← ERRADO

Depois da correção:
  Saldo inicial:    R$ 10.000,00
  Receita PARTIAL:  R$ 1.000,00 / paidAmount R$ 400,00
  Saldo calculado:  R$ 10.400,00  ← CORRETO
```

### T3 — Expense PAID (sem regressão)

```
Antes e depois:
  Saldo inicial:  R$ 10.000,00
  Despesa PAID:   R$ 1.000,00
  Saldo:          R$  9.000,00  ← mantido igual
```

### T5 — Estorno (sem regressão)

```
Antes e depois:
  Saldo inicial:  R$ 10.000,00
  Despesa PAID:   R$ 1.000,00
  Estorno PAID:   R$ 1.000,00
  Saldo:          R$ 10.000,00  ← mantido igual
```

---

## 8. Verificação de Double Counting

**Cenário:** despesa de R$1.000, primeiro pagamento parcial R$400, depois quitação total R$1.000.

```
Estado 1 — PARTIAL (paidAmount=400):
  sumPaid(EXPENSE)        = 0       ← status != PAID
  sumPartialPaid(EXPENSE) = 400     ← paidAmount do registro PARTIAL
  impacto = -400
  saldo = 10.000 - 400 = 9.600  ✓

Estado 2 — PAID (paidAmount=1000, amount=1000):
  sumPaid(EXPENSE)        = 1.000   ← amount do registro PAID
  sumPartialPaid(EXPENSE) = 0       ← nenhum PARTIAL (registro mudou de status)
  impacto = -1.000
  saldo = 10.000 - 1.000 = 9.000  ✓

NÃO ocorre: -400 - 1.000 = -1.400
```

O status de um lançamento é mutuamente exclusivo: ou é PARTIAL ou é PAID — nunca os dois. `sumPaid()` e `sumPartialPaid()` filtram por status diferentes, sem sobreposição possível.

---

## 9. Problemas Encontrados Fora do Escopo

**OUT_OF_SCOPE #1 — Testes de integração com banco real**

Nenhuma infraestrutura de banco em memória (H2, H2/PG compatibility mode) está disponível no projeto. Os testes `T1`–`T8` foram implementados como testes de fórmula em memória, seguindo o padrão dos testes existentes. Isso verifica a lógica matemática mas não verifica o comportamento das queries SQL contra um banco real.

Para validar completamente `sumPartialPaid()` e `sumPartialPaidBefore()` contra PostgreSQL, seria necessário adicionar H2 ou Testcontainers às dependências de teste. Isso está fora do escopo desta implementação.

**OUT_OF_SCOPE #2 — TRANSFER + PARTIAL não coberto**

Transferências parcialmente pagas não entram no saldo (comportamento mantido da versão anterior). A questão de domínio para transferências parciais foi explicitamente adiada conforme decisão do usuário.

**OUT_OF_SCOPE #3 — `syncOverdueStatuses()` chamado em `listAll()`**

Side-effect de escrita durante leitura, identificado na auditoria anterior. Não tem relação com PARTIAL e não foi alterado.

---

## 10. Conclusão

- **Implementação:** concluída
- **Testes criados:** 11 novos testes passando
- **Suite completa:** 125/125, 0 falhas, 0 regressões
- **Pendências:** testes de integração com banco real (OUT_OF_SCOPE #1)
- **Decisão pendente:** TRANSFER + PARTIAL (OUT_OF_SCOPE #2, aguarda decisão de domínio)
