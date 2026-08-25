# Estado Atual — Agosto 2026

_Gerado em 2026-08-25. Pós Parte A (principalPaid fix) e P0_5A_AUDITORIA.md._

---

## 1. Migrações V29–corrente

| Versão | Arquivo | Propósito | Status |
|--------|---------|-----------|--------|
| V29 | `transaction_reversed_type.sql` | Adiciona `reversed_type` a `financial_transactions` (C1: direção do estorno no saldo) | Aplicada |
| V30 | `transaction_origin.sql` | Adiciona coluna `origin` + backfill por campo estrutural (T-13) | Aplicada; **editada após aplicação** ⚠️ |
| V31 | `employees_document_unique_employment_type_name.sql` | Normaliza `employment_type` para `.name` + unique constraint em documento | Aplicada; **editada após aplicação** ⚠️ |
| V32 | `reclassify_transaction_origin.sql` | Reclassifica `origin` usando tipo/ofx_fitid antes de `employee_id` (correção da prioridade errada de V30 para REVERSAL/TRANSFER) | **NÃO aplicada**; arquivo `??` (untracked) |
| V33 | `transaction_payments.sql` | M1: cria tabela `transaction_payments` + backfill via timeline e campo colapsado | Aplicada 2026-08-17; **editada nesta sessão** ⚠️ |
| V34 | `transaction_version.sql` | Adiciona coluna `version INTEGER` a `financial_transactions` (R4: lock otimista) | Aplicada |
| V35 | `sanitize_text_fields.sql` | T-19: remove caracteres de controle de campos de texto livre em outras entidades | Aplicada |
| V36 | `sanitize_transaction_text_fields.sql` | T-21: sanitiza `description`/`document_type`/`document_number` em `financial_transactions` | Aplicada |

**Violações Flyway (checksum mismatch):** V30, V31, V33 foram editados após serem aplicados ao DB de dev.
Checksums gravados: V30 = `-840639645`, V31 = `-2040834267`, V33 = `-66395560`.
Próximo start do app falhará com `FlywayException: Validate failed` para essas três versões.
Resolução necessária: `flyway repair` em dev ou migration corretiva V37.

**V32 pendente:** não aplicada. Sem efeito no DB atual. Enquanto não aplicada, registros importados de REVERSAL/TRANSFER com `employee_id` permanecem com `origin = 'PAYROLL_ENGINE'` (classificação errada de V30).

---

## 2. transaction\_payments — Estágio M1–M6

### Contagem no DB (sessão anterior — 2026-08-17)

| Fonte | Rows |
|-------|------|
| via timeline (backfill M1) | 1 |
| via campo colapsado (backfill M1) | 0 |
| via dual-write (M2, recordPayment) | 0 |
| **total** | **1** |
| estornadas (`reversed_by_id IS NOT NULL`) | 0 |

### Status por etapa

**M1 — tabela + backfill** ✅  
V33 criou a tabela e populou via dois blocos: (a) timeline (`event_type IN ('PAYMENT', 'PARTIAL_PAYMENT')`) e (b) campo colapsado (`paid_amount > 0` sem eventos de pagamento). 1 título backfillado via timeline. Coluna `idempotency_key` adicionada ao DDL da SPEC (não estava no original).

**M2 — dual-write** ✅  
`TransactionService.recordPayment` insere em `transaction_payments` ao mesmo tempo que atualiza `financial_transactions`. 0 rows via dual-write: nenhum pagamento novo registrado desde a aplicação de V33. **Bug pendente**: `reversePaymentAndUpdateTitle` (TransactionRepository.kt:423) calcula `newPaidAmount = newPrincipal + newInterest + newFine` — semântica antiga, inclui encargos em `paidAmount`. Deve ser `newPrincipal` only (Parte A).

**M3 — portão de reconciliação** ✅  
`ReconciliationTest` implementado (5 testes). Query §4 executada: **0 divergências** (ver seção 6).

**M4 — leituras migradas** ⚠️ PARCIAL  
- `FinancialServices.calculateBalance` (L64) ✅ — usa `paymentRepository.sumCashEffectiveByAccountAndType` para todos os tipos.  
- `TransactionRepository.openingBalance` (L869) ✅ — usa `paymentRepository.sumCashEffectiveByAccountAndTypeBefore` + variantes de transferência e estorno.  
- `TransactionRepository.sumRealizedByProject` (L809) ❌ — ainda usa `SUM(paidAmount) WHERE status = 'PAID'`. Não usa cashEffective. Não é chamado por `calculateBalance`.

**M5 — UI** ⚠️ PARCIAL  
- M5-A (estorno de baixa, serviço) ✅ — `TransactionService.reversePayment` + `TransactionRepository.reversePaymentAndUpdateTitle` implementados. Bug em L423 (ver acima).  
- M5-B (listagem e estorno de baixa individual na UI) ✅ — `TransactionDetailsPanel` carrega `viewModel.baixas` (lista de `TransactionPayment`) e renderiza cada baixa individualmente. Estorno de baixa individual via `ReversalDialog` chama `viewModel.reversePayment(selectedBaixaId, justification)`.

**M6 — depreciação de paidAmount** ❌  
`paidAmount`, `interestAmount`, `fineAmount` ainda gravados em `financial_transactions` a cada `recordPayment`. Coluna `paidAmount` é fonte autoritativa de `outstandingPrincipal` e `resolveStatusAfterPayment`. Remoção não iniciada.

---

## 3. cashEffective — Definição e 8 pontos de uso

**Definição** (`TransactionPayment.kt:22`):
```kotlin
val cashEffective: Money
    get() = principalAmount + interestAmount + fineAmount - discountAmount
```
Representa o impacto líquido de uma baixa individual no caixa da conta.

### 8 pontos de uso em produção

| # | Local | Uso |
|---|-------|-----|
| 1 | `FinancialServices.calculateBalance` (L64) | Saldo corrente: soma cashEffective por tipo via `paymentRepository.sumCashEffectiveByAccountAndType` |
| 2 | `TransactionRepository.openingBalance` (L869–895) | Saldo inicial: 7 chamadas a variantes de cashEffective (income, expense, adjustment, transferIn, transferOut, reversalCredit, reversalDebit) |
| 3 | `BudgetItemRepository.sumRealized` (L176) | Realizado orçamentário: cashEffective de EXPENSE PAID com netting de REVERSAL |
| 4 | `BudgetItemRepository.sumRealizedMonth` (L111) | Realizado mensal: idem com filtro de mês |
| 5 | `TransactionPaymentRepository` — SQL nativo | `principal_amount + interest_amount + fine_amount - discount_amount` nas queries `sumCashEffective*` |
| 6 | `ReconciliationTest` | Invariante de portão M3: `Σ(cashEffective) == principalAcumulado + somaEncargos` |
| 7 | `TransactionDetailsPanel` (L541) | UI: `baixa.cashEffective` como valor exibido no diálogo de estorno de baixa individual |
| 8 | `ReconciliationDivergence.delta` | Monitoramento: `delta = somaBaixas - paidAmount` para detectar divergências entre as duas fontes |

**paidAmount dual-write**: `recordPayment` ainda grava `paidAmount` (principal acumulado) e `interestAmount`/`fineAmount` no título, além de inserir em `transaction_payments`. Campo `paidAmount` permanece autoritativo para `outstandingPrincipal` e máquina de estados.

**sumPaid/sumPartialPaid — código morto**: `sumPaid`, `sumPartialPaid`, `sumPaidBefore`, `sumPartialPaidBefore` em `TransactionRepository.kt` não têm callers externos após M4. `calculateBalance` e `openingBalance` já usam cashEffective; essas funções são órfãs.

---

## 4. Decisões D1 / D3 / D4

### D1 — Estorno de baixa ✅ IMPLEMENTADO

`TransactionService.reversePayment(paymentId, justification)` e `TransactionRepository.reversePaymentAndUpdateTitle`.

**Bug pendente (não corrigido em Parte A):** `reversePaymentAndUpdateTitle:423`:
```kotlin
// ERRADO — inclui encargos em paidAmount (semântica pré-Parte A):
val newPaidAmount = newPrincipal + newInterest + newFine
// CORRETO — paidAmount é principal puro:
val newPaidAmount = newPrincipal
```
Impacto: ao estornar uma baixa com encargos, `paidAmount` do título absorve `newInterest + newFine`, criando saldo devedor artificialmente reduzido.

### D3 — Desconto ✅ IMPLEMENTADO

D3(a): campo `discount_amount` em `transaction_payments`. `recordPayment` lê `totalDiscount` via `paymentRepository.sumDiscountByTransaction` para calcular `principalQuitado = newPaidAmount + totalDiscount` na validação de status.

D3(b): `BudgetItemRepository.sumRealized` e `sumRealizedMonth` usam `cashEffective = principal + interest + fine - discount`. Desconto reduz automaticamente o realizado.

### D4 — Fechamento de período ❌ NÃO IMPLEMENTADO

Sem guard de `payment_date` por período fechado. Sem `closeMonth`, `closePeriod`, tabela de controle de períodos, ou validação de data contra períodos encerrados em `TransactionValidator` ou `recordPayment`. Baixas em períodos históricos são aceitas sem restrição.

---

## 5. F5-escrita / F3 / SummaryTileRow / sumRealized REVERSAL

### F5-escrita — beneficiário unificado ❌ NÃO IMPLEMENTADO

`CounterpartyResolver.kt` implementa o lado de leitura: resolução batch de IDs para nomes (`CounterpartyMap`, mapeamento fornecedor/funcionário/centro de custo). O campo unificado de beneficiário na escrita de lançamentos não foi implementado.

### F3 — Contas a Receber alinhada com Contas a Pagar ❌ NÃO IMPLEMENTADO

`ReceivablesScreen` não usa `SummaryTileRow`. Funcionalidades de baixa parcial, estorno e visualização de baixas individuais presentes em `PayablesScreen` não estão replicadas na tela de recebíveis.

### SummaryTileRow ✅ EXTRAÍDO E EM USO

Componente em `TransactionListComponents.kt:198`. Usado em `PayablesScreen.kt`. Pronto para reuso em `ReceivablesScreen` quando F3 for implementado.

### sumRealized — netting de REVERSAL

| Função | Local | cashEffective | Netta REVERSAL |
|--------|-------|---------------|----------------|
| `sumRealized` | `BudgetItemRepository.kt:176` | ✅ | ✅ via JOIN `parent_transaction_id`, filtra `reversed_type = 'EXPENSE'` |
| `sumRealizedMonth` | `BudgetItemRepository.kt:111` | ✅ | ✅ idem, com filtro de mês |
| `sumRealizedByProject` | `TransactionRepository.kt:809` | ❌ `SUM(paidAmount)` | ❌ não netta estornos |

`sumRealizedByProject` usa `SUM(paidAmount) WHERE status = 'PAID'` — semântica pré-M4. Estornos (status REVERSAL) não são subtraídos. Projetos com estornos mostram realizado inflado.

---

## 6. Query de reconciliação SPEC §4

Executada na sessão de 2026-08-17 contra o DB de dev:

```sql
-- SPEC_TRANSACTION_PAYMENTS §4: detecta divergências entre paidAmount e Σ(cashEffective)
SELECT
    t.id,
    t.amount,
    t.paid_amount,
    COALESCE(SUM(tp.principal_amount + tp.interest_amount + tp.fine_amount - tp.discount_amount), 0) AS soma_baixas,
    COALESCE(SUM(tp.principal_amount + tp.interest_amount + tp.fine_amount - tp.discount_amount), 0)
        - COALESCE(t.paid_amount, 0) AS delta
FROM financial_transactions t
LEFT JOIN transaction_payments tp
    ON tp.transaction_id = t.id
    AND tp.reversed_by_id IS NULL
WHERE t.is_active = true
    AND t.paid_amount IS NOT NULL
    AND t.paid_amount > 0
GROUP BY t.id, t.amount, t.paid_amount
HAVING ABS(
    COALESCE(SUM(tp.principal_amount + tp.interest_amount + tp.fine_amount - tp.discount_amount), 0)
    - COALESCE(t.paid_amount, 0)
) > 0.01;
```

**Resultado:**

```
 id | amount | paid_amount | soma_baixas | delta
----+--------+-------------+-------------+-------
(0 rows)

--- total divergentes: 0
```

Estado do DB no momento: 1 row em `transaction_payments` (via timeline backfill), 0 via campo colapsado, 0 via dual-write. Invariante preservada.

---

_Próximas ações prioritárias:_
- _Corrigir `reversePaymentAndUpdateTitle:423` (bug D1 pós-Parte A)_
- _Aplicar V32 ou integrar ao V37 com `flyway repair`_
- _Resolver checksums de V30/V31/V33 antes do próximo start do app_
- _Migrar `sumRealizedByProject` para cashEffective (M4 incompleto)_
- _Implementar D4 (fechamento de período)_
