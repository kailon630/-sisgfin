# R4 — Idempotência das engines e concorrência

## 1. Resumo executivo

`PayrollEngine`, `RecurrenceEngine` e a API Ktor sobem no boot em três `CoroutineScope(Dispatchers.IO)` com `runCatching` (engines sem log de falha). Idempotência é só por **query de existência** no código — **não** há UNIQUE de `(employee_id, due_date)` nem `(recurrence_template_id, due_date)`. Não há versionamento otimista nem `FOR UPDATE` / `pg_advisory_lock`. Desktop e Ktor compartilham o mesmo singleton Koin de `TransactionService`. A query mensal de “duplicatas” por funcionário retornou 2 linhas/competência, mas são **dois dias de pagamento distintos** (05 e 15), não o mesmo `due_date`.

## 2. Estado atual

### 2.1 Boot em `Main.kt`

Após `startKoin` + `koinReady`, chama `launchBackgroundEngines()` (`Main.kt:90–92`).

```169:207:src/main/kotlin/br/com/sisgfin/Main.kt
private fun launchBackgroundEngines() {
    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            val payrollEngine = getKoin().get<PayrollEngine>()
            val now = YearMonth.now()
            payrollEngine.generateForMonth(now)
            payrollEngine.generateForMonth(now.plusMonths(1))
        }
    }
    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            getKoin().get<RecurrenceEngine>().generateAhead(monthsAhead = 2)
        }
    }
    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            createKtorServer(
                // ...
                transactionService = getKoin().get<TransactionService>(),
                // ...
            ).start(wait = false)
        }.onSuccess { /* println API */ }
         .onFailure { e -> println("Falha ao iniciar API REST: ${e.message}") }
    }
}
```

| Engine | Dispatcher | Tratamento de falha |
|--------|------------|---------------------|
| PayrollEngine | `Dispatchers.IO` | `runCatching` engole erro **sem** log |
| RecurrenceEngine | `Dispatchers.IO` | idem |
| Ktor | `Dispatchers.IO` | `onFailure` imprime mensagem |

### 2.2 `PayrollEngine` — checagem antes de criar

```41:46:src/main/kotlin/br/com/sisgfin/employees/PayrollEngine.kt
                for (day in employee.effectivePaymentDays()) {
                    val dueDate = yearMonth.atDay(day.coerceAtMost(yearMonth.lengthOfMonth()))
                    if (transactionRepository.existsPaymentForEmployee(employee.id, dueDate)) {
                        skipped++
                        continue
                    }
```

`existsPaymentForEmployee`: mesmo `employeeId` + mesmo dia de `dueDate` + status ∈ {PENDING, PAID, PARTIAL, SCHEDULED} + `is_active`. **Somente query em código** — sem constraint UNIQUE correspondente.

### 2.3 `RecurrenceEngine.generateAhead()`

```50:55:src/main/kotlin/br/com/sisgfin/recurrence/RecurrenceEngine.kt
        for (dueDate in dates) {
            if (transactionRepository.existsGeneratedFor(template.id, dueDate)) {
                skipped++
                continue
            }
```

`existsGeneratedFor`: `recurrenceTemplateId` + `dueDate` no dia + `is_active`. Também só em código.

### 2.4 Índices UNIQUE em `financial_transactions`

Via `pg_indexes` (base real):

| indexname | indexdef |
|-----------|----------|
| `financial_transactions_pkey` | UNIQUE `(id)` |
| `ux_financial_transactions_account_fitid` | UNIQUE `(account_id, ofx_fitid)` WHERE `ofx_fitid IS NOT NULL` |
| demais | índices **não** UNIQUE (account, status, type, due_date, active, financial_project, contract, recurrence) |

**Não há** UNIQUE em `(employee_id, due_date)` nem `(recurrence_template_id, due_date)`.

### 2.5 Locks / versão

- Nenhuma coluna `version` / optimistic lock encontrada no domínio financeiro.
- Nenhum uso de `SELECT ... FOR UPDATE` ou `pg_advisory_lock` no código Kotlin (grep sem matches).

### 2.6 API Ktor × UI desktop

Mesma instância: `TransactionService` é `single` no Koin; `createKtorServer(... transactionService = getKoin().get())` e ViewModels desktop consomem o mesmo bean. Caminhos de escrita **compartilham** o serviço (não são processos/DB connections isolados com locks de aplicação).

## 3. Resultados das queries

**“Duplicatas” por funcionário × competência (mês):**

| employee_id | competencia | COUNT(*) |
|------------:|-------------|---------:|
| 1 | 2026-09-01 | 2 |
| 1 | 2026-08-01 | 2 |

Detalhe: ids 1–4 com `due_date` **05 e 15** de cada mês — padrão de dois `paymentDays`, não o mesmo vencimento duplicado.

**Duplicatas por `recurrence_template_id` + `due_date`:** 0 linhas.

## 4. Achados

| # | Severidade | Achado | Arquivo:linha | Impacto |
|---|-----------|--------|---------------|---------|
| 1 | ALTO | Idempotência das engines sem UNIQUE no banco | `PayrollEngine.kt:43`; `RecurrenceEngine.kt:52`; ausência de índice | Race (dois boots / API+engine) pode criar duplicata |
| 2 | MÉDIO | Falhas do Payroll/Recurrence no boot são engolidas sem log | `Main.kt:171–182` | Engine pode falhar silenciosamente |
| 3 | MÉDIO | Query pedida agrupa por mês, não por `due_date` — marca folha multi-dia como “dup” | query R4 | Falso positivo analítico nesta base |
| 4 | BAIXO | Único UNIQUE de negócio em txs é OFX fitid | `V16__transaction_ofx_fitid.sql` | Proteção de idempotência só no caminho OFX |
| 5 | MÉDIO | Sem lock otimista / `FOR UPDATE` em escritas financeiras | codebase | Concorrência UI+API depende de sorte de timing |

## 5. Incertezas

- Não foi reproduzida race real (dois `generateForMonth` paralelos).
- Não medido se reinício da app no mesmo dia cria duplicatas apesar do check (TOCTOU).
- Comportamento sob múltiplas instâncias desktop apontando ao mesmo Postgres não foi testado (arquitetura assume processo único).
