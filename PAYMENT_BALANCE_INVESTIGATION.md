# PAYMENT_BALANCE_INVESTIGATION.md
> Investigação focada: pagamento de despesa + saldo
> Data: 2026-08-10 | SisgFin

---

## 1. Resumo

O fluxo `recordPayment()` → saldo está funcionalmente correto para o caso nominal (pagamento total de uma despesa). A state machine protege contra estados inválidos. O saldo é calculado sob demanda por consulta direta ao banco — não existe campo `balance` armazenado. A fórmula inclui corretamente estornos, transferências e ajustes.

Dois problemas concretos encontrados no escopo desta investigação:

1. **Pagamento parcial (`PARTIAL`) não altera o saldo** — o cálculo ignora `PARTIAL`; a conta bancária real tem o dinheiro debitado mas o sistema ainda exibe o saldo cheio.
2. **Race condition em `recordPayment()`** — sem lock otimista ou transação de banco que envolva o check + update. Dois requests simultâneos via Ktor podem ambos passar pela validação e sobrescrever um ao outro.

Não existe teste de integração cobrindo `recordPayment()` com banco real nem `calculateBalance()`.

---

## 2. Fluxo Encontrado

```
UI: TransactionDetailsPanel
    botão "Quitar" → showPaymentDialog = true
    PaymentDialog: usuário informa paidAmount, paymentDate, juros, multa
    → viewModel.recordPayment(id, paymentDate, paidAmount, interest, fine)

ViewModel: TransactionsViewModel.recordPayment()
    → runOperation { service.recordPayment(id, paymentDate, paidAmount, ...) }
    [runOperation: executa em Dispatchers.IO, captura exceções, chama load() no sucesso]

Service: TransactionService.recordPayment()
    → [ver seção 3 para detalhe]
    → repository.update(updated)

Repository: TransactionRepository.update()
    → UPDATE financial_transactions
      SET status, paymentDate, paidAmount, interestAmount, fineAmount, updatedAt
      WHERE id = ?

Database: tabela financial_transactions
    → status alterado para PAID ou PARTIAL
    → paidAmount preenchido
    → paymentDate preenchido
    → updatedAt = now()
```

**Consulta de saldo** (chamada separada, não dentro de `recordPayment`):

```
UI: BalancesScreen / DashboardScreen
    → FinancialAccountViewModel / BalancePanelViewModel
    → FinancialAccountService.calculateBalance(accountId)
    → transactionRepository.sumPaid(accountId, EXPENSE) + outros
    → SELECT SUM(amount) FROM financial_transactions WHERE ...
```

---

## 3. `recordPayment()` — comportamento real

**Localização:** `TransactionService.kt:318-371`

**Parâmetros recebidos:**
- `id` — ID da transação
- `paymentDate` — data do pagamento
- `paidAmount` — valor pago (pode ser menor que `amount` para pagamento parcial)
- `interestAmount` — juros opcionais
- `fineAmount` — multa opcional

**Sequência exata:**

```
1. requirePermission(Permission.ConfirmPayment)
   → lança SecurityException se usuário não for ADMIN

2. repository.findById(id)
   → lança IllegalArgumentException se não encontrado
   → NOTE: findById() NÃO filtra isActive — retorna transações canceladas também
   → state machine bloqueia CANCELED na etapa 3

3. TransactionStateMachine.allowsPayment(existing.status)
   → permite apenas: PENDING, OVERDUE, PARTIAL
   → lança IllegalStateException para qualquer outro status (incluindo PAID, CANCELED)

4. TransactionValidator.validatePayment(total, paidAmount, paymentDate, issueDate)
   → rejeita paidAmount <= 0
   → rejeita paidAmount > total (sem overpagamento)
   → rejeita paymentDate < issueDate

5. TransactionStateMachine.resolveStatusAfterPayment(amount, paidAmount)
   → paidAmount >= amount → PAID
   → 0 < paidAmount < amount → PARTIAL

6. TransactionStateMachine.assertTransition(existing.status → newStatus)
   → valida que a transição é permitida na state machine

7. TransactionValidator.validateForSave(updated, existing)
   → segunda passagem de validação com o estado novo

8. repository.update(updated)
   → ← ÚNICO WRITE DO FLUXO DE PAGAMENTO
   → persiste: status, paymentDate, paidAmount, interestAmount, fineAmount, updatedAt

9. ledgerService.recordPayment(...)
   → NO-OP — stub vazio, não faz nada

10. addTimeline(PAYMENT | PARTIAL_PAYMENT, ...)
    → INSERT em transaction_timeline_events
    → registra: eventType, message, amountValue, statusFrom, statusTo, performedBy, createdAt

11. audit(TRANSACTION_PAID | TRANSACTION_PARTIAL_PAYMENT, ...)
    → INSERT em audit_logs
    → registra: entityType, entityId, action, newValue, performedBy, createdAt
```

**O que é registrado:**

| Campo alterado | Tabela | Valor |
|---------------|--------|-------|
| `status` | `financial_transactions` | `PAID` ou `PARTIAL` |
| `paymentDate` | `financial_transactions` | parâmetro `paymentDate` |
| `paidAmount` | `financial_transactions` | parâmetro `paidAmount` |
| `interestAmount` | `financial_transactions` | parâmetro (nullable) |
| `fineAmount` | `financial_transactions` | parâmetro (nullable) |
| `updatedAt` | `financial_transactions` | `LocalDateTime.now()` |
| evento timeline | `transaction_timeline_events` | INSERT novo registro |
| auditoria | `audit_logs` | INSERT novo registro |

**Data/hora:** `paymentDate` é parâmetro (usuário informa). `updatedAt` é sempre `now()` do servidor.

**Usuário:** `performedBy` gravado em timeline e audit_logs via `sessionManager.currentUser.value?.id`.

**Proteção contra pagamento duplicado:** apenas via state machine. PAID é estado terminal; segundo chamada para um PAID lança `IllegalStateException`. Sem lock de banco entre `findById` e `update`.

---

## 4. `calculateBalance()` — comportamento real

**Localização:** `FinancialServices.kt` — `FinancialAccountService.calculateBalance(accountId: Int)`

**O saldo NÃO é armazenado.** É calculado sob demanda por 7 queries separadas ao banco.

**Implementação real:**

```kotlin
val income      = transactionRepository.sumPaid(accountId, INCOME)
val expense     = transactionRepository.sumPaid(accountId, EXPENSE)
val reversal    = transactionRepository.sumPaid(accountId, REVERSAL)
val adjustment  = transactionRepository.sumPaid(accountId, ADJUSTMENT)
val transferIn  = transactionRepository.sumPaidTransferIn(accountId)
val transferOut = transactionRepository.sumPaidTransferOut(accountId)
return account.initialBalance + income + reversal + adjustment + transferIn - expense - transferOut
```

**Fórmula:**

```
Saldo = saldoInicial
      + Σ amount (INCOME,     status=PAID, isActive=true)
      + Σ amount (REVERSAL,   status=PAID, isActive=true)
      + Σ amount (ADJUSTMENT, status=PAID, isActive=true)
      + Σ amount (TRANSFER,   status=PAID, isActive=true, parentId IS NOT NULL)  ← transferência entrada
      - Σ amount (EXPENSE,    status=PAID, isActive=true)
      - Σ amount (TRANSFER,   status=PAID, isActive=true, parentId IS NULL)      ← transferência saída
```

**O que `sumPaid()` faz:**

```kotlin
SELECT SUM(amount)           -- ← coluna amount, NÃO paidAmount
FROM financial_transactions
WHERE accountId = ?
  AND type = ?
  AND status = 'PAID'
  AND isActive = true
```

**Mapa de estados no cálculo:**

| Status | Entra no saldo? |
|--------|----------------|
| PENDING | Não |
| OVERDUE | Não |
| SCHEDULED | Não |
| DRAFT | Não |
| PARTIAL | **Não** |
| PAID | **Sim** |
| CANCELED | Não (`isActive=false` filtrado) |

---

## 5. Relação Entre Pagamento e Saldo

O pagamento **não altera** nenhum campo de saldo diretamente. Ele apenas muda o `status` da transação de PENDING/OVERDUE para PAID no banco.

O saldo muda **porque** `calculateBalance()` é recalculado quando chamado, e a nova consulta `sumPaid(EXPENSE)` agora inclui a transação recém-paga.

**Cadeia causal:**

```
recordPayment(id, ...) 
  → UPDATE financial_transactions SET status='PAID' WHERE id=?
  
calculateBalance(accountId)
  → SELECT SUM(amount) FROM financial_transactions
    WHERE accountId=? AND type='EXPENSE' AND status='PAID'
  → agora inclui o lançamento recém pago
  → resultado = saldoInicial - expense(novo total)
```

**Cenário R$ 10.000 → R$ 9.000:**

```
Antes:
  initialBalance = 10.000
  sumPaid(EXPENSE) = 0   (despesa está PENDING)
  calculateBalance = 10.000 - 0 = 10.000  ✓

Após recordPayment(id, paidAmount=1000):
  status → PAID
  sumPaid(EXPENSE) = 1.000
  calculateBalance = 10.000 - 1.000 = 9.000  ✓

Após reverseTransaction(id, "motivo"):
  REVERSAL criado: type=REVERSAL, status=PAID, amount=1.000
  sumPaid(REVERSAL) = 1.000
  calculateBalance = 10.000 + 1.000 - 1.000 = 10.000  ✓
```

**Ciclo de pagamento parcial (R$ 400 de R$ 1.000):**

```
Antes:
  calculateBalance = 10.000

Após recordPayment(id, paidAmount=400):
  status → PARTIAL   ← NÃO é PAID
  sumPaid(EXPENSE) = 0   ← PARTIAL excluído da query
  calculateBalance = 10.000  ← SALDO NÃO MUDA

Na conta bancária real: R$ 400 saíram.
No sistema: saldo ainda mostra R$ 10.000.
```

---

## 6. Testes Existentes

Diretamente relacionados a `recordPayment` ou `calculateBalance`:

| Teste | Arquivo | Cobre |
|-------|---------|-------|
| `test partial payment status logic` | `TransactionWorkflowTest.kt` | apenas aritmética de Money (paidFull >= amount), sem banco |
| `RN-04 saldo com todos os tipos` | `TransferAndReversalTest.kt` | fórmula de saldo em memória sem banco |
| `RN-16 paymentDate >= issueDate` | `TransactionValidatorTest.kt` | validatePayment() em memória |
| `PAID sem paymentDate gera erro` | `TransactionValidatorTest.kt` | validate() em memória |
| `PARTIAL com paidAmount maior que total` | `TransactionValidatorTest.kt` | validate() em memória |

---

## 7. Teste de Integração Ausente

Não existe atualmente um teste de integração cobrindo este fluxo.

Nenhum teste existente:
- chama `recordPayment()` com banco real
- chama `calculateBalance()` com banco real
- verifica que o saldo muda após um pagamento
- verifica que um estorno reverte o saldo
- verifica o comportamento de pagamento parcial no saldo

---

## 8. Problemas Encontrados

| ID | Problema | Severidade | Evidência |
|----|----------|------------|-----------|
| P1 | Pagamento parcial (PARTIAL) não altera saldo — sistema exibe saldo incorreto em relação à realidade bancária | **ALTO** | `sumPaid()` filtra `status='PAID'`; `PARTIAL` excluído. `TransactionRepository.kt:315-323` |
| P2 | Race condition: dois `recordPayment()` simultâneos podem ambos passar pelo check de estado e sobrescrever um ao outro | **MÉDIO** | Sem lock entre `findById` e `update`. `TransactionService.kt:326-348`. Risco real somente via Ktor multi-client |
| P3 | `sumPaid()` soma coluna `amount`, não `paidAmount` | **BAIXO / INFO** | Para PAID: `validatePayment()` garante `paidAmount <= amount`. Quando `paidAmount == amount` (caso PAID normal), os valores são idênticos. Sem impacto prático em uso normal. `TransactionRepository.kt:312` |

---

## 9. Pontos que Precisam de Decisão

**D1 — Comportamento de pagamento parcial no saldo é intencional?**

Atualmente, pagar R$ 400 de uma despesa de R$ 1.000 não altera o saldo. O design pode ser intencional (saldo só reflete dinheiro totalmente liquidado) ou um bug não percebido. Não é possível determinar pelo código qual era a intenção.

Se intencional → precisa estar documentado e comunicado ao cliente (o saldo no sistema não reflete saques parciais).

Se não intencional → a fórmula de saldo precisa incluir `paidAmount` de transações PARTIAL.

**D2 — O servidor Ktor está sendo usado em produção com múltiplos usuários?**

Se sim, a race condition (P2) é um risco real de pagamento duplicado/sobrescrito e precisa de mitigação (lock otimista via `updatedAt`, ou `SELECT FOR UPDATE` dentro de uma transação Exposed).

Se apenas desktop single-user → P2 não tem impacto prático.
