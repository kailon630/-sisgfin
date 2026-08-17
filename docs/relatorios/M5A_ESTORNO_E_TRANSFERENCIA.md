# M5-A — Estorno de Baixa Individual e Transferência como Evento

**Épico**: E2 — Integridade de Caixa  
**Commits**: `59aeca5` → `79aab43` (branch `main`, 2026-08-16)  
**Status**: ✅ Concluído  
**Testes**: 351 → 367 (+ 16 testes)

---

## Objetivo

Implementar duas decisões arquiteturais pendentes do E2:

- **D1**: estorno de baixa individual — operação própria distinta do estorno de título; `reversed_by_id` + baixa de correção; permite PAID → PENDING/PARTIAL via `assertReversalTransition`
- **D5**: transferência como evento consumado — pernas nascem PAID com baixas na mesma transação atômica; `recordPayment` rejeita tipo TRANSFER

---

## Blocos implementados

### Block 1 — Estorno de baixa (commit `59aeca5`)

**D1 implementado**: `TransactionService.reversePayment(paymentId, justification)` delega para `TransactionRepository.reversePaymentAndUpdateTitle()`.

**Operação atômica** (padrão C-15, dentro de um único `transaction {}`):
1. Carrega e valida a baixa original (não pode estar já estornada)
2. Insere marcador de correção com `reversed_by_id = paymentId` — excluído imediatamente das somas
3. Atualiza original: `reversed_by_id = correctionId` — referência cruzada mútua; ambos excluídos das somas
4. Consulta baixas ativas restantes e recalcula `paidAmount`, `interestAmount`, `fineAmount`
5. Deriva `newStatus` a partir de `principalQuitado` vs. `amount` e `dueDate`
6. Persiste novo estado via `update(entity)`

**Status após estorno**:
| Situação | Status resultante |
|---|---|
| `principalQuitado ≥ amount` | PAID |
| `0 < principalQuitado < amount` | PARTIAL |
| `principalQuitado = 0` e vencido | OVERDUE |
| `principalQuitado = 0` e a vencer | PENDING |

**`assertReversalTransition`** adicionada em `TransactionStateMachine`:
- Não altera `allowedTransitions` nem `forbiddenExplicit`
- `assertTransition` inalterado — RN-13 preservado para todos os caminhos normais
- Permite qualquer transição exceto CANCELED em qualquer direção

**Marcador de correção**: copia todos os valores da baixa original (satisfaz `CHECK principal_amount > 0`) mas é imediatamente excluído das somas via `reversed_by_id IS NOT NULL`.

**`PAYMENT_REVERSED`** adicionado em `TimelineEventType` com descrição "Baixa estornada".

**Novos testes** — `ReversePaymentTest` (7 testes):
- Permissão negada lança `SecurityException`
- Justificativa em branco lança `IllegalArgumentException`
- Justificativa com espaços lança `IllegalArgumentException`
- Delegação ao repositório com `paymentId` e `justification` corretos
- Timeline registra evento `PAYMENT_REVERSED`
- Audit registra ação `"PAYMENT_REVERSED"`
- Transição PAID → PENDING propagada no evento de timeline

### Block 2 — Transição exclusiva de saída de PAID (commit `056d9af`)

**Novos testes** — `ReversalTransitionTest` (5 testes):
- `assertTransition PAID → PENDING` ainda lança (RN-13 preservado)
- `assertReversalTransition PAID → PENDING` não lança
- `assertReversalTransition PAID → PARTIAL` não lança
- `isTerminal(PAID)` retorna `true` (sem efeito colateral do M5-A)
- `allowsCancel(PAID)` retorna `false`

### Block 3 — Transferência como evento consumado (commit `79aab43`)

**D5 implementado**:

`createTransfer` atualizado:
- `status = TransactionStatus.PAID` (antes `PENDING`)
- `paymentDate = date` (antes ausente)
- Chama `insertTransferPairWithBaixas(source, destination, amount, date, userId)` — 4 registros atômicos (2 títulos + 2 baixas)

`recordPayment` recebe guard:
```kotlin
if (existing.type == TransactionType.TRANSFER) {
    throw IllegalStateException("Transferências não passam por recordPayment. Use createTransfer.")
}
```

`insertTransferPair` mantido para compatibilidade; `insertTransferPairWithBaixas` é o método de produção.

**Testes atualizados**:
- `CreateTransferIntegrationTest`: status `PENDING → PAID` (2 asserções); todos os mocks `insertTransferPair → insertTransferPairWithBaixas(any,any,any,any,any)` (8 ocorrências)
- `RecordPaymentIntegrationTest`: C-01 CARACTERIZACAO invertido — guard D5 rejeita TRANSFER com `IllegalStateException`
- `TransactionOriginTest`: mock atualizado para `insertTransferPairWithBaixas`

**Novos testes** — `TransferAsEventTest` (4 testes):
- Source nasce com `status = PAID` e `paymentDate != null`
- Destination nasce com `status = PAID` e `paymentDate != null`
- `insertTransferPairWithBaixas` chamado; `insertTransferPair` não chamado
- `recordPayment` em tipo TRANSFER lança `IllegalStateException`

---

## Ausências documentadas (Block 4 parcial)

- **Verificação contra DB dev**: `findReconciliationDivergences()` não executado (sem conexão de desenvolvimento disponível no momento do commit); M3 continua como portão de integridade
- **UI de estorno**: `reversePayment` exposto no service; diálogo de UI não implementado (M5-B)
- **Listagem de baixas no painel**: M5 — UI ainda pendente

---

## Arquivos modificados

| Arquivo | Mudança |
|---|---|
| `TimelineEventType.kt` | + `PAYMENT_REVERSED("Baixa estornada")` |
| `TransactionStateMachine.kt` | + `assertReversalTransition` |
| `TransactionRepository.kt` | + `PaymentReversalResult`, `reversePaymentAndUpdateTitle`, `insertTransferPairWithBaixas`; imports `BigDecimal`, `TransactionStateMachine` |
| `TransactionService.kt` | + `reversePayment`; `createTransfer` PAID + baixas; guard TRANSFER em `recordPayment` |
| `ReversePaymentTest.kt` | Novo — 7 testes (D1) |
| `ReversalTransitionTest.kt` | Novo — 5 testes (assertReversalTransition) |
| `TransferAsEventTest.kt` | Novo — 4 testes (D5) |
| `CreateTransferIntegrationTest.kt` | Atualizado — status + método |
| `RecordPaymentIntegrationTest.kt` | C-01 invertido |
| `TransactionOriginTest.kt` | Mock atualizado |
