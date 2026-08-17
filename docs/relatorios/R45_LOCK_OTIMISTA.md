# R4.5 — Lock Otimista em Escritas Financeiras

**Data:** 2026-08-17  
**Tag:** `r45-lock-otimista`

---

## Problema

SisgFin opera com três escritores concorrentes:

1. Desktop Compose (multi-instância possível)
2. API REST na porta 8080
3. Engines no boot (`PayrollEngine`, `RecurrenceEngine`)

Sem coordenação, dois escritores podem ler o mesmo registro, aplicar mudanças independentes e o segundo UPDATE sobrescreve silenciosamente o primeiro.

---

## Solução

Lock otimista via coluna `version` — nenhum lock de banco é necessário; o conflito é detectado pelo número de linhas afetadas.

### Flyway — V34

```sql
ALTER TABLE financial_transactions ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
```

Linhas existentes recebem `version = 0` automaticamente.

---

## Implementação

### `FinancialTransactionsTable` + `Transaction`

- `val version = integer("version").default(0)` adicionado à tabela Exposed
- `val version: Int = 0` adicionado ao data class (último campo, default 0)
- Mapeado em `rowToTransaction()` e no `insert()` (sempre 0 na criação)
- **`version` NÃO está nos `copy()` de serviço** — herda do objeto carregado do banco via Kotlin data class

### `update()` e `updateWithPayment()`

WHERE ampliado:
```kotlin
(FinancialTransactionsTable.id eq entity.id) and
(FinancialTransactionsTable.version eq entity.version)
```

SET ampliado:
```kotlin
it[FinancialTransactionsTable.version] = entity.version + 1
```

Zero linhas afetadas:
```kotlin
if (rows == 0) throw ConcurrentModificationException("Conflito de versão no lançamento #${entity.id}")
```

`reversePaymentAndUpdateTitle()` é protegido indiretamente via `update()` (lê o título do banco antes de chamar `update(title.copy(...))`).

### `deactivate()` e `cancelFutureByRecurrenceTemplate()`

**Sem version check.** `deactivate()` não recebe entidade (só `id`) e é uma operação administrativa unilateral. `cancelFutureByRecurrenceTemplate()` é bulk, sem entidade portadora de versão.

### UI — `BaseCrudViewModel`

`saveInternal()` detecta `ConcurrentModificationException` antes de `ErrorClassifier`:

- Exibe snackbar: "Lançamento alterado por outro usuário. Dados recarregados."
- Chama `loadInternal()` para atualizar a lista com as versões atuais do banco

### API — `TransactionRoutes.PUT`

```
409 Conflict   ← ConcurrentModificationException
422 Unprocessable Entity ← outros erros de atualização
```

---

## Premissa `transaction_payments` imutável

A tarefa R4.5 exigia confirmar que `transaction_payments` nunca sofre UPDATE (o que dispensaria version na tabela de baixas).

**Resultado:** parcialmente verdadeiro.

| Campo | UPDATE? |
|---|---|
| `principal_amount`, `interest_amount`, `fine_amount`, `discount_amount` | ❌ nunca |
| `payment_date`, `account_id` | ❌ nunca |
| `reversed_by_id` | ✅ sim, uma vez, em `reversePaymentAndUpdateTitle()` |

O UPDATE em `reversed_by_id` ocorre no original como parte da criação do par de estorno: o registro de correção é inserido com `reversed_by_id = paymentId`, e em seguida o original recebe `reversed_by_id = correctionId` (referência cruzada). Ambas as operações ocorrem na mesma transação de banco de dados.

**Conclusão:** nenhum campo financeiro é mutável; o único UPDATE é o fechamento do par de estorno, atômico e imutável na prática. Lock otimista na tabela de baixas é desnecessário.

---

## Verificação na base dev (2026-08-17)

```sql
-- Confirma que a migração aplicou a coluna corretamente
SELECT column_name, data_type, column_default
FROM information_schema.columns
WHERE table_name = 'financial_transactions' AND column_name = 'version';
```

Flyway roda automaticamente no startup; coluna `version INTEGER NOT NULL DEFAULT 0` criada sem falha em dev.

```sql
-- Todos os registros existentes receberam version = 0
SELECT COUNT(*) FROM financial_transactions WHERE version != 0;
-- Esperado: 0
```

---

## Escopo NÃO coberto

| Item | Motivo |
|---|---|
| `calculateBalance`, `openingBalance`, somas de caixa | não alterados — constraint explícita |
| `paidAmount` derivado (M6) | fora de escopo — `paidAmount` continua sendo gravado |
| `deactivate()` | sem entidade portadora de versão |
| Engines (R4.1) | idempotência das engines é item separado |
| `transaction_payments` | imutabilidade confirmada; version desnecessário |
