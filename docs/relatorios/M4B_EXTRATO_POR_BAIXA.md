# M4b — Extrato e Livro Diário por Baixa

**Data:** 2026-08-17  
**Tag:** `m4b-extrato-por-baixa`

---

## O que mudou

### Antes

`findStatementEntries()` e `findAllPaid()` retornavam uma linha por **título** (`financial_transactions`)
filtrado por `payment_date` do próprio título. Um título pago em três baixas aparecia como uma linha,
na data da última baixa, com o valor total (`paidAmount`).

### Depois

`findPaymentEntries()` retorna uma linha por **baixa ativa** (`transaction_payments`).

| Campo | Antes | Depois |
|---|---|---|
| Fonte da data | `financial_transactions.payment_date` | `transaction_payments.payment_date` |
| Fonte do valor | `paidAmount ?? amount` do título | `cashEffective` da baixa (`principal + juros + multa − desconto`) |
| Baixas estornadas | apareciam se título fosse PAID | excluídas (`reversed_by_id IS NULL`) |
| Linha por baixa | não | sim |
| Ordinal | ausente | `(PAGTO N/M)` quando >1 baixa ativa; denominador omitido se PARTIAL |

---

## Livro Diário — histórico TCESP

Formato do sufixo de ordinal adicionado a `buildTcespDesc`:

| Situação | Histórico gerado |
|---|---|
| 1 baixa única | `PAGO A, FORNECEDOR X CF NF 100` |
| 1ª de 3 baixas (título PAID) | `PAGO A, FORNECEDOR X CF NF 100 (PAGTO 1/3)` |
| 2ª de 3 baixas (título PAID) | `PAGO A, FORNECEDOR X CF NF 100 (PAGTO 2/3)` |
| 2ª baixa, título ainda PARTIAL | `PAGO A, FORNECEDOR X CF NF 100 (PAGTO 2)` |

Prefixo (`PAGO A` / `RECEBIDO DE`), credor e parte `CF [DOC]` preservados sem alteração.

---

## Verificação na base dev (2026-08-17)

```sql
SELECT COUNT(DISTINCT t.id) AS titulos_multi_baixa,
       SUM(cnt) - COUNT(DISTINCT t.id) AS linhas_adicionais_no_livro
FROM (
  SELECT p.transaction_id, COUNT(*) AS cnt
  FROM transaction_payments p
  WHERE p.reversed_by_id IS NULL
  GROUP BY p.transaction_id
  HAVING COUNT(*) > 1
) sub
JOIN financial_transactions t ON t.id = sub.transaction_id
WHERE t.is_active = true;
```

**Resultado em dev:** 0 títulos com múltiplas baixas — base de desenvolvimento tem dados mínimos.
O único pagamento registrado (`Pagamento KAILON — Agosto/2026`, R$ 5.000, 2026-08-14) tem
1 baixa ativa. O Livro Diário antes e depois mostra a mesma linha; a diferença de formato
(data e valor) se torna visível quando há baixas parciais com encargos ou datas diferentes
do título.

---

## Consumidores de `findStatementEntries` após M4b

| Consumidor | Situação |
|---|---|
| `StatementViewModel.loadStatement()` | **migrado** → usa `findPaymentEntries` |
| `ReportsViewModel.applyLivroDiarioFilter()` | **migrado** → usa `findPaymentEntries` |
| `StatementRoutes.kt` (API REST) | **ainda usa** `findStatementEntries` — endpoint de API externa; não migrado para não alterar contrato HTTP sem teste de integração |

`findStatementEntries` não foi removido; continua servindo a `StatementRoutes`.

---

## Consistência do saldo acumulado do Extrato

O `openingBalance` já usa `sumCashEffectiveByAccountAndTypeBefore` (desde M4 Bloco 2),
que filtra por `transaction_payments.payment_date < before`. O saldo acumulado do Extrato
percorre as mesmas baixas que compõem o saldo de abertura, portanto:

```
saldo_final_extrato = openingBalance + Σ(cashEffective das baixas no período)
                    = calculateBalance(account) se o período cobrir toda a história
```

A invariância não depende de M4b — a fórmula de saldo nunca usou `findStatementEntries`.
