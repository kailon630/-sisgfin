# P0-5 — Verificação pós-P0-4: encargos no saldo

**Data:** 2026-08-10  
**Escopo:** verificação somente leitura — nenhum código alterado  
**Referência de código:** commit em que P0-4 foi concluído (BUILD SUCCESSFUL, 187 testes)

---

## Q1 — `principalPaid = paidAmount - interestAmount - fineAmount` é bug se `paidAmount` é principal puro?

**Não é bug para dados novos (pós-P0-4). É bug para dados antigos (pré-P0-4).**

### Semântica alterada pelo P0-4

Antes do P0-4, `recordPayment` armazenava `paidAmount = valorPrincipal` (apenas o principal desta baixa — sobrescrita). Juros eram coluna separada, independente.

Após o P0-4, `recordPayment` acumula:
```kotlin
val newPaidAmount = (existing.paidAmount ?: Money.ZERO) + paidAmount + juros + multa
```
`paidAmount` na coluna agora armazena **principal + juros + multa acumulados**. A propriedade computada `principalPaid = paidAmount - interestAmount - fineAmount` recupera corretamente o principal amortizado *para esses novos registros*.

### Incompatibilidade de migração

Para registros criados **antes** do P0-4 onde havia encargos:
- `paidAmount` (coluna) = principal puro, ex.: `500.00`
- `interestAmount` (coluna) = `50.00` (registrado separado)
- `principalPaid` calculado = `500 - 50 = 450` ← **ERRADO** (deveria ser 500)
- `outstandingPrincipal` calculado = `1000 - 450 = 550` ← **ERRADO** (deveria ser 500)

Se `markAsPaidFull` for chamado sobre esse registro antigo, passará `outstandingPrincipal = 550` como `principal` para `recordPayment`, sobrecobrando o usuário em R$50.

**Nenhuma migration corrige a semântica do `paid_amount` para registros históricos com encargos.**

---

## Q2 — Os valores esperados de T4 correspondem à semântica principal puro?

**Não. T4 usa a nova semântica pós-P0-4 de forma internamente consistente, mas sua asserção de saldo diverge do comportamento real de `calculateBalance`.**

T4 (`PartialPaymentAccumulationTest`):
```kotlin
// Saldo de conta deve refletir saída de 1050 (não 1000)
val balance = AccountBalanceFormula.compute(
    initialBalance = Money.fromString("10000.00"),
    expense        = tx2.paidAmount!!  // = 1050
)
assertEquals(0, Money.fromString("8950.00").compareTo(balance))
```

O comentário está errado. `calculateBalance` chama `sumPaid(EXPENSE)` que faz `SUM(amount)`, não `SUM(paid_amount)`. Para esse título, `amount = 1000`. O saldo real que o sistema produziria é `9000`, não `8950`. T4 testa um cenário hipotético (expense = paidAmount) que não corresponde ao comportamento de produção.

---

## Q3 — O que `sumPaid`, `sumPartialPaid` e `sumPaidBefore` somam?

| Função | Query (coluna somada) | Status alvo |
|---|---|---|
| `sumPaid` | `SUM(amount)` — valor de face do título | PAID |
| `sumPartialPaid` | `SUM(paid_amount)` — valor acumulado pago | PARTIAL |
| `sumPaidBefore` | `SUM(amount)` — valor de face do título | PAID (antes de data) |
| `sumPartialPaidBefore` | `SUM(paid_amount)` — valor acumulado pago | PARTIAL (antes de data) |

Nenhuma das quatro funções acessa `interest_amount` ou `fine_amount` diretamente. Para PAID, juros/multa são completamente invisíveis. Para PARTIAL, juros/multa entram *indiretamente* porque após P0-4 `paid_amount` os acumula.

---

## Q4 — `interest_amount` e `fine_amount` entram em `calculateBalance` ou `openingBalance`?

**Para títulos PAID: não, em nenhum caminho.**

`calculateBalance` em `FinancialServices.kt`:
```kotlin
val expense        = transactionRepository.sumPaid(accountId, EXPENSE)       // SUM(amount)
val expensePartial = transactionRepository.sumPartialPaid(accountId, EXPENSE) // SUM(paid_amount)
```

Para PAID: `expense = SUM(amount)` — `interest_amount` e `fine_amount` ignorados.  
Para PARTIAL: `expensePartial = SUM(paid_amount)` — após P0-4, `paid_amount` inclui encargos (efeito indireto, não via coluna `interest_amount`/`fine_amount`).

`openingBalance` usa `sumPaidBefore` e `sumPartialPaidBefore` — mesma lógica.

---

## Q5 — Qual mudança de saldo um título de R$1000 quitado com R$50 de juros produz?

**Mudança de saldo: −R$1.000. Os R$50 de juros são invisíveis ao saldo.**

### Cenário: baixa única (PAID direto)

| Campo | Valor |
|---|---|
| `amount` | 1000,00 |
| `paidAmount` (após P0-4) | 1050,00 (principal + juros) |
| `interestAmount` | 50,00 |
| `status` | PAID |

`calculateBalance`:
- `expense = sumPaid(EXPENSE) = SUM(amount) = 1.000,00`
- Saldo final = `10.000 − 1.000 = 9.000`
- **R$50 de juros desaparecem** — R$1.050 saíram do caixa mas o saldo mostra −R$1.000

### Testes documentando o comportamento atual

Arquivo: `src/test/kotlin/br/com/sisgfin/financial/transactions/EncargosNoSaldoTest.kt`

```
Q5a — titulo PAID baixa unica - calculateBalance usa amount nao paidAmount - juros invisiveis
Q5b — statement mostra paidAmount 1050 mas calculateBalance ve amount 1000 - divergencia 50
Q5c — titulo PARTIAL - calculateBalance usa paidAmount que inclui juros - encargos visiveis
Q5d — assimetria PARTIAL-PAID - juros da primeira baixa somem ao quitar status
```

Resultado: **BUILD SUCCESSFUL — 4/4 testes passam** (documentam comportamento atual, não o correto).

### Cenário de assimetria PARTIAL → PAID (Q5d)

1ª baixa: R$300 principal + R$50 juros → PARTIAL  
- `paidAmount = 350` → `expensePartial = 350` → saldo = `9.650`

2ª baixa: R$700 principal → PAID  
- `expense = amount = 1.000` (ignora `paidAmount = 1.050`) → saldo = `9.000`

A 2ª baixa reduziu o saldo em R$650, mas R$700 foram pagos. Os R$50 de juros da 1ª baixa "somem" ao mudar de balde PARTIAL para PAID.

---

## Q6 — `StatementExporter` mostra valor com ou sem encargos? Há divergência?

**StatementModels usa `paidAmount` — inclui encargos. `calculateBalance` usa `amount` para PAID. Há divergência confirmada.**

`StatementModels.signedAmount`:
```kotlin
TransactionType.EXPENSE -> (tx.paidAmount ?: tx.amount).negate()
```

Para título PAID com amount=1000 e paidAmount=1050 (pós-P0-4):
- **Statement mostra:** −R$1.050
- **calculateBalance produz:** −R$1.000 (via `SUM(amount)`)
- **Divergência:** R$50 por título com encargos

Isso significa que o saldo corrente do extrato (`signedAmount` acumulado) não coincide com `calculateBalance`. Para qualquer conta com títulos PAID que tiveram encargos após o P0-4, o saldo exibido no extrato difere do saldo calculado pela fórmula de conta.

`ReportsExporter` (linha 75) e `CashFlowService` têm o mesmo padrão `paidAmount ?: amount` — mesma divergência se usados para cálculos que não são reconciliados com `calculateBalance`.

---

## Resumo executivo

| # | Pergunta | Veredito |
|---|---|---|
| Q1 | `principalPaid` tem bug? | **Bug de migração**: correto para dados pós-P0-4; errado para dados históricos com encargos |
| Q2 | T4 é internamente consistente? | **Parcialmente**: semântica P0-4 OK; asserção de saldo usa `paidAmount` (1050), mas `calculateBalance` real usa `amount` (1000) |
| Q3 | O que as funções somam? | `sumPaid`/`sumPaidBefore` → `amount`; `sumPartialPaid`/`sumPartialPaidBefore` → `paid_amount` |
| Q4 | Encargos entram no saldo? | **Não** para PAID; **indiretamente sim** para PARTIAL (via `paid_amount` pós-P0-4) |
| Q5 | Mudança de saldo com juros? | **−R$1.000** (não −R$1.050) — juros invisíveis para PAID |
| Q6 | Divergência statement vs balance? | **Confirmada** — statement usa `paidAmount` (1050), balance usa `amount` (1000) |

### Problemas identificados (sem correção nesta fase)

1. **Migração de dados históricos**: registros pré-P0-4 com `interestAmount` preenchido terão `principalPaid` e `outstandingPrincipal` calculados de forma errada. Risco de sobrecobrança em `markAsPaidFull`.
2. **Assimetria PAID/PARTIAL**: encargos visíveis no saldo enquanto PARTIAL, invisíveis após PAID. Juros pagos na fase PARTIAL "somem" ao transitar para PAID.
3. **Divergência statement/balance**: extrato bancário e saldo de conta divergem em R$(total de juros pagos em títulos PAID).
4. **T4 asserção incorreta**: `expense = tx2.paidAmount` não espelha `calculateBalance` real — o teste valida cenário hipotético, não produção.
