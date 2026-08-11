# F2-fix P0 — Relatório de Correção

**Data:** 2026-08-10  
**Status:** Concluído — BUILD SUCCESSFUL, 4/4 testes P0 passando (+ suite completa sem regressões)

---

## Bugs corrigidos

### P0-1 — Erros silenciados nas operações de escrita

**Sintoma:** `markAsPaidFull`, `cancelTransaction`, `duplicateTransaction` continham `onFailure { /* logged by service */ }`. O comentário era falso: `TransactionService` não loga exceções. Qualquer falha era descartada silenciosamente e o usuário não recebia feedback.

**Root cause investigado:**
- `TransactionService.recordPayment`, `cancel`, `duplicate` lançam exceções sem chamar `AppLogger`.
- O comentário foi inserido em sessão anterior para fazer o build passar sem a assinatura correta de `AppLogger.error(AppError)`.

**Conduta aplicada:** nunca remover tratamento de erro — corrigir a chamada.

**Correção em `PayablesViewModel.kt`:**
- Adicionados imports `AppLogger` e `ErrorClassifier`.
- Padrão aplicado nos três métodos:
  ```kotlin
  withContext(Dispatchers.IO) {
      runCatching { transactionService.op(...) }
  }.onSuccess {
      load()
  }.onFailure { err ->
      val appError = ErrorClassifier.classify(err)
      AppLogger.error(appError)
      _uiState.value = _uiState.value.copy(errorMessage = appError.userMessage)
  }
  ```
- `load()` chamado **apenas em caso de sucesso**: o `errorMessage` não é apagado pela recarga automática.
- Erro exibido em `PayablesScreen` via `uiState.errorMessage?.let { Text(it, color = WsDanger) }` (padrão de `ReceivablesScreen`).

---

### P0-2 — "Quitar" visível para OPERADOR sem verificação de permissão

**Sintoma:** `TransactionContextMenu` em `PayablesScreen` exibia "Quitar" para qualquer usuário. RN-12 exige que a ação fique **oculta** (não desabilitada) para quem não tem `Permission.ConfirmPayment`.

**Correção em `TransactionsScreen.kt` — `TransactionContextMenu`:**
- Novo parâmetro `canPay: Boolean = true` (default `true` mantém comportamento existente em `TransactionsScreen`).
- Guard: `if (canPay && status != PAID && status != CANCELED)`.
- Chamada existente em `TransactionsScreen` não precisa de alteração (usa default).

**Correção em `PayablesViewModel.kt`:**
```kotlin
fun canConfirmPayment(): Boolean = transactionService.canConfirmPayment()
```

**Correção em `PayablesScreen.kt`:**
```kotlin
val canPay = viewModel.canConfirmPayment()
// ...
TransactionContextMenu(canPay = canPay, ...)
```

---

### P0-3 — `markAsPaidFull` passava valor total em vez do saldo restante para PARTIAL

**Sintoma:** para título `PARTIAL` com `amount = R$ 1.000` e `paidAmount = R$ 300`, `markAsPaidFull` chamava `recordPayment(id, now, tx.amount)` — R$ 1.000. `TransactionService.recordPayment` não valida contra `paidAmount` existente; aceita o valor e registra saída de R$ 1.000 no ledger quando o restante correto seria R$ 700.

**Verificação de impacto:**
- `TransactionValidator.validatePayment` verifica apenas `paidAmount <= total` e `> 0` — não compara com `paidAmount` acumulado.
- `TransactionStateMachine.resolveStatusAfterPayment` compara `paidAmount vs totalAmount` apenas — resolve PAID para valor total, mesmo com parcial anterior.
- Resultado: ledger com saída errada. CRÍTICO.

**Correção em `PayablesViewModel.kt`:**
```kotlin
val remaining = tx.amount - (tx.paidAmount ?: Money.ZERO)
transactionService.recordPayment(id, LocalDateTime.now(), remaining, null, null)
```

**Correção em `TransactionContextMenu`:**
- Label muda para **"Quitar saldo"** quando `transaction.status == PARTIAL`, para comunicar ao usuário que é quitação do saldo remanescente.

**Não alterado:** `TransactionService.recordPayment` — fora do escopo desta tarefa.

---

## Testes

| Arquivo | Testes adicionados |
|---|---|
| `PayablesViewModelWriteTest.kt` | 4 |

| Teste | Verifica | Falhou antes? | Passa depois? |
|---|---|---|---|
| P0-1 `recordPayment` throws → `errorMessage` | P0-1 | SIM | SIM |
| P0-2 `cancel` throws → `errorMessage` | P0-1 | SIM | SIM |
| P0-3 `duplicate` throws → `errorMessage` | P0-1 | SIM | SIM |
| P0-6 PARTIAL 1000/300 → `recordPayment` recebe 700 | P0-3 | SIM | SIM |

**Dependências de teste adicionadas** (sem impacto em produção):
- `io.mockk:mockk:1.13.12` — mock de `TransactionService` (classe final Kotlin)
- `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0` — `UnconfinedTestDispatcher` para substituir `Dispatchers.Main`

**Estratégia de sincronização:** `UnconfinedTestDispatcher` como `Dispatchers.Main` faz `viewModelScope.launch {}` executar inline até a primeira suspensão; `Thread.sleep(400)` aguarda o `withContext(Dispatchers.IO)` completar nos fakes in-memory (< 5 ms de execução real).

---

## Arquivos alterados

| Arquivo | Tipo | Mudança |
|---|---|---|
| `build.gradle.kts` | Config | Adicionadas 2 dependências de teste |
| `payables/PayablesViewModel.kt` | Fix | P0-1 (log + errorMessage), P0-3 (remaining), P0-2 (canConfirmPayment) |
| `financial/transactions/TransactionsScreen.kt` | Fix | P0-2 (`canPay` param + label "Quitar saldo" para PARTIAL) |
| `payables/PayablesScreen.kt` | Fix | P0-2 (passa `canPay`) + P0-1 (exibe `errorMessage`) |
| `test/payables/PayablesViewModelWriteTest.kt` | Teste | 4 novos testes (P0-1, P0-2, P0-3, P0-6) |

---

## Restrições cumpridas

- `TransactionService.recordPayment` não alterado.
- Migrações não alteradas.
- Cálculo de saldo não alterado.
- F3, F5-escrita, extração de componente não implementados.
- Chamadas de log corrigidas — nunca removidas.
