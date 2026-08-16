# Relatório de Migração M1–M3 — `transaction_payments`

**Data:** 2026-08-16  
**Base:** `dashboard-postgres` / `sisgfin`  
**Migração aplicada:** V33

---

## 1. Totais do backfill

| Métrica | Valor |
|---|---|
| Transações ativas com `paid_amount > 0` | 1 |
| Baixas geradas via **timeline** (data individual) | 1 |
| Baixas geradas via **campo colapsado** (data aproximada) | 0 |
| Total de linhas em `transaction_payments` após backfill | 1 |

---

## 2. Títulos com data aproximada (campo colapsado)

Nenhum. Todos os títulos com `paid_amount > 0` possuíam eventos `PAYMENT` ou
`PARTIAL_PAYMENT` na `transaction_events`. A coluna `payment_date` de cada baixa
reflete a data real do registro do evento.

---

## 3. Pernas TRANSFER PENDING legadas

Consulta executada:

```sql
SELECT id, account_id, amount, due_date, parent_transaction_id
  FROM financial_transactions
 WHERE type = 'TRANSFER' AND status = 'PENDING' AND is_active = true;
```

**Resultado: 0 linhas.** Nenhuma perna TRANSFER legada em PENDING na base de
desenvolvimento.

---

## 4. Resultado da reconciliação (M3)

Consulta de portão executada contra `sisgfin` (base dev):

```sql
SELECT t.id, t.amount, t.paid_amount,
       COALESCE(SUM(p.principal_amount + p.interest_amount + p.fine_amount
                    - p.discount_amount), 0) AS soma_baixas
  FROM financial_transactions t
  LEFT JOIN transaction_payments p
         ON p.transaction_id = t.id AND p.reversed_by_id IS NULL
 WHERE t.is_active = true AND t.paid_amount IS NOT NULL AND t.paid_amount > 0
 GROUP BY t.id, t.amount, t.paid_amount
HAVING COALESCE(SUM(p.principal_amount + p.interest_amount + p.fine_amount
                    - p.discount_amount), 0) <> t.paid_amount;
```

**Resultado: 0 linhas — PORTÃO APROVADO.** O sistema pode avançar para M4.

---

## 5. O que ainda lê `paidAmount` (migrar no M4)

| Componente | Arquivo | O que precisa mudar |
|---|---|---|
| `calculateBalance` | `AccountBalanceFormula.kt` | Usar Σ(cashEffective das baixas) em vez de `paid_amount` |
| `openingBalance` | `TransactionRepository.kt` | Usar `payment_date` da baixa em vez de `paymentDate` do título |
| `sumPartialPaid` | `TransactionRepository.kt` | Somar `principal_amount` das baixas |
| `sumRealized` | `TransactionRepository.kt` / `LedgerService.kt` | Somar caixa efetivo das baixas |
| Extrato Excel | `StatementExporter.kt` | Listar baixas individuais em vez de campo colapsado |
| Livro Diário TCESP | `LedgerService.kt` | Uma linha por baixa real, não por título |

---

## 6. Observações sobre o backfill

**Timeline como fonte primária:** A migração usa `transaction_events` com
`event_type IN ('PAYMENT', 'PARTIAL_PAYMENT')`. Cada evento gera uma linha em
`transaction_payments` com `payment_date = created_at::date` do evento.
O `amount` da timeline armazena `cashThisBaixa = principal + juros + multa`;
como a decomposição individual não está disponível, o valor total é armazenado
em `principal_amount` com `interest_amount = 0` e `fine_amount = 0`.
O invariante de reconciliação `Σ(cashThisBaixa) = paid_amount` é preservado.

**Campo colapsado como fallback:** Para títulos sem eventos de pagamento na
timeline, seria usada a coluna `paid_amount` do título (data aproximada = `payment_date`
do título, que reflete apenas a última baixa). Na base dev, nenhum caso foi
encontrado.

---

## 7. Limitações conhecidas (não bloqueiam M4)

- **Atomicidade update/insert (M2):** As escritas em `repository.update()` e
  `paymentRepository.insertOrIgnore()` são sequenciais, não atômicas. Refatorar
  para o padrão C-15 (`updateWithPayment` no repositório) requer atualizar os
  testes `CARACTERIZACAO` existentes — fora do escopo deste bloco.

- **Desconto e reconciliação:** Baixas com `discount_amount > 0` mostrarão
  divergência na query de reconciliação porque `paid_amount` não inclui o efeito
  do desconto (decisão de M2 para não afetar leituras). A reconciliação completa
  com desconto é responsabilidade do M4.

- **`PayrollImportService.findByCpf()`** não filtra funcionários inativos — gap
  preexistente registrado como C-16 no KANBAN.
