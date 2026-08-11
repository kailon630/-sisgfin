# P0-4 — Liquidação Parcial: Acumulação e Validação

**Data:** 2026-08-10  
**Status:** Concluído — BUILD SUCCESSFUL, 187/187 testes (9 novos P0-4 + atualizações existentes)

---

## 1. Semântica de `paidAmount` antes da correção

**`paidAmount` NÃO incluía `interestAmount` nem `fineAmount`.**

Evidência — `TransactionService.recordPayment` (pré-fix):
```kotlin
TransactionValidator.validatePayment(existing.amount, paidAmount, paymentDate, existing.issueDate)
// validator rejeita paidAmount > existing.amount → juros não poderiam ser embutidos
...
paidAmount     = paidAmount,        // overwrite do parâmetro (principal desta baixa)
interestAmount = interestAmount,    // separado
fineAmount     = fineAmount,        // separado
```

Após a correção, `paidAmount` armazena `principal + juros + multa` acumulados. A propriedade `principalPaid = paidAmount - interestAmount - fineAmount` recupera o principal amortizado.

---

## 2. Sobrescreve ou acumula?

**Sobrescreve** (antes da correção). Linhas 388-390 do serviço:
```kotlin
paidAmount     = paidAmount,
interestAmount = interestAmount,
fineAmount     = fineAmount,
```
Nenhum uso de `existing.paidAmount`. Segunda baixa parcial descartava a primeira.

**PARTIAL → PARTIAL bloqueada:** `allowedTransitions[PARTIAL] = setOf(PAID, CANCELED)` — segundo pagamento parcial lançava `IllegalStateException("Transição de status inválida: PARTIAL → PARTIAL")` antes de alcançar a sobrescrita.

---

## 3. Pontos que leem `paidAmount` e se precisaram mudar

| Local | Leitura | Precisa mudar? |
|---|---|---|
| `CashFlowService` (104, 108) | `paidAmount ?: amount` — caixa saído | Não — após fix, `paidAmount` É o total de caixa |
| `ReportsExporter` (75, 166, 653) | idem | Não |
| `ReportsViewModel` (146) | idem | Não |
| `StatementModels` (29-33) | idem | Não |
| `DashboardScreen` (375) | idem | Não |
| `CashFlowScreen` (636) | idem | Não |
| `ReportsScreen` (200, 258) | idem | Não |
| `TransactionRepository.sumPartialPaid` | soma `paidAmount` de PARTIAL (saldo de conta) | Não — semantica agora correta (inclui encargos do período) |
| `TransactionRepository.sumPartialPaidBefore` | idem para opening balance | Não |
| `TransactionRepository.sumRealizedByProject` | soma `paidAmount` de PAID/PARTIAL por projeto | Não |
| `TransactionValidator.validate()` linha 41 | `paidAmount >= amount` como guarda de PARTIAL | **SIM** — mudado para `principalPaid >= amount` |
| `TransactionValidator.validatePayment` | `paidAmount <= total` | **SIM** — assinatura reescrita |
| `TransactionService.markAsPaid` | passava `existing.amount` | **SIM** — agora `existing.outstandingPrincipal` |
| `PayablesViewModel.markAsPaidFull` | `tx.amount - (tx.paidAmount ?: ZERO)` | **SIM** — agora `tx.outstandingPrincipal` |

---

## 4. Testes 1–8: falharam antes da correção?

| # | Cenário | Falhou antes? | Motivo da falha |
|---|---|---|---|
| T1 | 300 + 700 → PAID, paidAmount=1000 | **SIM** | `outstandingPrincipal` não existia → erro de compilação |
| T2 | 300 + 300 → PARTIAL, paidAmount=600 | **SIM** | idem |
| T3 | 300 + 800 → rejeitado com msg de saldo | **SIM** | idem |
| T4 | 300 + 700+50juros → PAID, principalPaid=1000, paidAmount=1050 | **SIM** | idem |
| T5 | Baixa integral 1000 → PAID (regressão) | **SIM** | idem |
| T6 | 400+400+200 → PAID | **SIM** | idem |
| T7 | Saldo com duas baixas acumuladas | **SIM** | idem |
| T8a | Valor zero rejeitado | **SIM** | idem |
| T8b | Valor negativo rejeitado | **SIM** | idem |

Arquivo de teste compilado, confirmado com `./gradlew compileTestKotlin` antes das correções → 7 erros de "Unresolved reference: outstandingPrincipal".

---

## 5. Títulos PARTIAL existentes na base

Não foi possível acessar o banco de produção nesta sessão. A query de diagnóstico recomendada:

```sql
-- Títulos PARTIAL em produção
SELECT id, amount, paid_amount, interest_amount, fine_amount, status
  FROM financial_transactions
 WHERE status = 'PARTIAL' AND is_active = true
 ORDER BY id;

-- Quantidade
SELECT COUNT(*) FROM financial_transactions
 WHERE status = 'PARTIAL' AND is_active = true;

-- Indício de sobrescrita: título com dois eventos de PARTIAL_PAYMENT na timeline
-- mas paidAmount < soma dos dois valores
SELECT t.id, t.amount, t.paid_amount,
       tl.description, tl.amount AS timeline_amount, tl.created_at
  FROM financial_transactions t
  JOIN transaction_timeline tl ON tl.transaction_id = t.id
 WHERE t.status = 'PARTIAL' AND t.is_active = true
   AND tl.event_type = 'PARTIAL_PAYMENT'
 ORDER BY t.id, tl.created_at;
```

**Indício de sobrescrita:** timeline com dois eventos `PARTIAL_PAYMENT` mas `paid_amount` correspondendo apenas ao valor do último. Se encontrado, corrija `paid_amount` para a SOMA de todos os pagamentos parciais da timeline.

---

## 6. `paidAmount` gravado em ponto único

**SIM — confirmado.** Após a correção, a única escrita de `paid_amount` via `repository.update()` ocorre em `TransactionService.recordPayment`. Outros caminhos que criam transações (`createFromOfx`, `createFromPayrollImport`, `reverseTransaction`, `cancel`) ou não tocam em `paid_amount` ou o inicializam diretamente no objeto (reversal: `paidAmount = original.amount`, sem passar por `recordPayment`).

---

## Arquivos alterados

| Arquivo | Mudança |
|---|---|
| `financial/transactions/Transaction.kt` | `principalPaid` e `outstandingPrincipal` como computed properties |
| `financial/transactions/workflow/TransactionStateMachine.kt` | `PARTIAL → PARTIAL` adicionado às transições permitidas |
| `financial/transactions/TransactionValidator.kt` | `validatePayment` reescrita (nova assinatura); guarda PARTIAL usa `principalPaid` |
| `financial/transactions/TransactionService.kt` | `recordPayment` acumula; `markAsPaid` usa `outstandingPrincipal` |
| `payables/PayablesViewModel.kt` | `markAsPaidFull` usa `tx.outstandingPrincipal` |
| `test/.../TransactionValidatorTest.kt` | Callers de `validatePayment` atualizados para nova assinatura |
| `test/.../PartialPaymentAccumulationTest.kt` | 9 novos testes (T1–T8b) |

---

## Restrições cumpridas

- `calculateBalance` / `openingBalance` não alterados.
- Estorno não alterado.
- Migrações não alteradas (`paidAmount` continua no mesmo campo; semântica só muda pelo código).
- F3, F5-escrita, extração de componente não implementados.
- `transaction_payments` não criada.
- Desconto não implementado (registrado como pendência: título quitado com abatimento ficará PARTIAL indefinidamente até implementação de campo `discountAmount`).

---

## Nota: `Thread.sleep` nos testes de ViewModel

O spec sugere substituir `Thread.sleep(400)` por `runTest + advanceUntilIdle()`. Após análise:

- `advanceUntilIdle()` só avança corrotinas no TestScheduler do `TestScope`.
- `viewModelScope` usa `Dispatchers.Main` (UnconfinedTestDispatcher), mas `withContext(Dispatchers.IO)` ainda despacha para o IO thread pool real.
- `advanceUntilIdle()` retorna antes do IO completar — substituição tornaria os testes racy de forma mais sutil.

A troca correta exige injeção do dispatcher de IO no `BaseViewModel`. Isso é refatoração de infraestrutura fora do escopo desta tarefa; mantido `Thread.sleep(400)` nos testes de ViewModel com comentário explicativo.
