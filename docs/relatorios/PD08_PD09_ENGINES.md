# PD-08 + PD-09 — Engines no boot: falha silenciosa e concorrência

**Data:** 2026-08-27  
**Escopo:** Observabilidade de engines + lock de instância única + idempotência OVERDUE.  
**Testes adicionados:** T1–T8 em `PD08EngineRunTest.kt`; T1–T5 em `PD09IdempotencyTest.kt`; TB1–TB3 em `PD08BPartialFailureTest.kt`  
**Suite após PD-08 + PD-09 + PARTIAL_FAILURE:** 538 testes, todos verdes.  
**Migrações:** V38 (constraints UNIQUE), V39 (engine_runs), V40 (coluna `failed`).  
**Limpeza de dados:** 6 duplicatas canceladas em produção (audit_logs ids correspondentes inseridos).

---

## 1. Existiam duplicatas na base?

**Sim.** `employee_id = 1` com 3 lançamentos para `due_date = 2026-08-05` e 5 para `due_date = 2026-08-15`. Recorrência: zero duplicatas.

### Causa raiz

`existsPaymentForEmployee` filtrava `status IN (PENDING, PAID, PARTIAL, SCHEDULED)`. OVERDUE ausente. Quando `syncOverdueStatuses` marcava o lançamento como vencido, o engine deixava de enxergá-lo e recriava a cada boot.

### Confirmação pelos timestamps

| due_date    | id original | ids duplicados | datas de criação dos duplicados |
|-------------|-------------|----------------|---------------------------------|
| 2026-08-05  | 1           | 5, 6           | 11/08, 14/08 (boots após vencimento) |
| 2026-08-15  | 2           | 7, 8, 9, 10    | 17/08 às 09:54, 13:38, 13:57, 20:17 |

### Limpeza executada

```sql
UPDATE financial_transactions
   SET status = 'CANCELED'
 WHERE id NOT IN (
     SELECT MIN(id) FROM financial_transactions
      WHERE employee_id IS NOT NULL AND is_active = true
        AND origin IN ('PAYROLL_ENGINE', 'PAYROLL_IMPORT')
      GROUP BY employee_id, due_date
 )
   AND employee_id IS NOT NULL AND is_active = true
   AND origin IN ('PAYROLL_ENGINE', 'PAYROLL_IMPORT')
   AND status NOT IN ('PAID', 'PARTIAL')
   AND NOT EXISTS (
       SELECT 1 FROM transaction_payments tp
        WHERE tp.transaction_id = financial_transactions.id AND tp.reversed_by_id IS NULL
   );
-- UPDATE 6
```

6 registros inseridos em `audit_logs` com motivo "duplicata gerada por bug PD-09". Saldo de caixa inalterado — os 6 eram OVERDUE sem baixa em `transaction_payments`.

---

## 2. `CONCURRENTLY` funcionou com o Flyway do projeto?

**Não foi usado.** `CREATE INDEX CONCURRENTLY` exige autocommit; Flyway executa migrações em transação por padrão. A migração V38 usa `CREATE UNIQUE INDEX` sem `CONCURRENTLY`. Trava breve, aceitável em boot de instalação. Registrado no arquivo da migração.

---

## 3. Filtro de `origin` no índice de folha está correto?

**Sim.** `TransactionOrigin` contém: `MANUAL, PAYROLL_ENGINE, PAYROLL_IMPORT, RECURRENCE, OFX, API, INSTALLMENT, TRANSFER, REVERSAL, DUPLICATE`.

Predicado do índice: `origin IN ('PAYROLL_ENGINE', 'PAYROLL_IMPORT')`.

Lançamento `MANUAL` ou `API` para o mesmo funcionário no mesmo dia (adiantamento, reembolso) não é bloqueado. O índice também excluiu `status = 'CANCELED'` para permitir recriação legítima após cancelamento.

---

## 4. Onde o operador vê que a folha do mês foi gerada?

**Tela Funcionários → banner `PayrollStatusBanner` no topo.**

Caminho: Menu lateral → Funcionários.

O banner exibe:
- `WsSuccess` (verde): "Folha de Agosto/2026: gerada em 27/08 12:00 — 3 criado(s), 0 já existia(m)"
- `WsDanger` (vermelho): "Folha de Agosto/2026 não foi gerada neste mês" — se nenhuma execução bem-sucedida
- `WsDanger` (vermelho): mensagem de erro — se última execução falhou
- `WsWarning` (amarelo): "geração em andamento em outra instância" — se SKIPPED_LOCKED
- `WsWarning` (amarelo): "geração parcial — N criado(s), M falha(s)" — PARTIAL_FAILURE (estado mais perigoso: parte dos funcionários não recebeu lançamento)
- `WsWarning` (amarelo): indicador de progresso — enquanto execução em andamento

Botão **"Gerar folha do mês"** disponível em todos os estados exceto durante execução. Executa `engineOrchestrator.runPayrollForMonth(YearMonth.now())` via `EmployeeViewModel.runPayrollNow()`.

---

## 5. PARTIAL_FAILURE — comportamento

`generateForMonth` e `generateAhead` agora envolvem cada funcionário/template em `runCatching`. Falha individual não aborta os demais. O resultado por funcionário carrega `failed: Int` e `failureReason: String?`.

`EngineOrchestrator.onSuccess` classifica:
- `failed > 0 && created > 0` → `finishPartial` → status `PARTIAL_FAILURE`
- `failed > 0 && created == 0` → `finishFailed` → status `FAILED`
- `failed == 0` → `finishSuccess` → status `SUCCESS`

Coluna `failed INTEGER NOT NULL DEFAULT 0` adicionada em `engine_runs` pela migração V40.

Banner exibe PARTIAL_FAILURE em amarelo com contagem de falhas e nomes dos funcionários afetados. Status mais crítico operacionalmente: parte da folha gerada, parte não.

---

## 6. Testes que falhavam antes

### PD-09 (idempotência)
Não havia testes antes. `PD09IdempotencyTest.T1` documenta o comportamento corrigido: quando `existsPaymentForEmployee` retorna `true` (agora inclui OVERDUE), engine pula. Antes, a query retornava `false` para OVERDUE → criava duplicata a cada boot.

### PD-08 (engine_runs)
Não havia testes antes. Os 8 testes em `PD08EngineRunTest` cobrem novos comportamentos: gravação de `FAILED/SUCCESS/SKIPPED_LOCKED`, isolamento entre engines, idempotência via lock.

---

## 6. `AppLogger` grava em destino persistido?

**Não. Pendência aberta.**

`AppLogger` usa `java.util.logging.Logger("SisgFin")` — somente console/JUL. Log some ao fechar o app. Para falha de engine de madrugada, o único registro é a linha `status = 'FAILED'` na tabela `engine_runs`, com o campo `error` contendo a mensagem da exceção. O `engine_runs` é durável.

**O que está coberto:**
- Falha fica registrada em `engine_runs.error` (persistido no banco)
- Operador vê banner vermelho na tela de Funcionários na próxima abertura
- `AppLogger.error` faz log em nível SEVERE para o JUL handler disponível

**O que falta:** sink durável para o JUL (arquivo ou tabela `application_logs`). Isso seria uma pendência de infraestrutura separada — não implementado neste escopo.

---

## 7. Arquitetura implementada

```
EngineOrchestrator
├── locker: EngineLocker (default: AdvisoryLocker → pg_try_advisory_lock)
│   └── Testável: AlwaysAcquireLocker / NeverAcquireLocker
├── runPayrollForMonth(yearMonth) → EngineRun
│   ├── runRepo.startRun("PAYROLL", ref)
│   ├── locker.tryLock(LOCK_PAYROLL) { payrollEngine.generateForMonth() }
│   │   ├── onSuccess → runRepo.finishSuccess(created, skipped)
│   │   └── onFailure → AppLogger.error + runRepo.finishFailed(error)
│   └── null lock → runRepo.finishSkipped()
└── runRecurrence(monthsAhead) → EngineRun (análogo)

EngineRunRepository
├── startRun → INSERT status=RUNNING
├── finishSuccess / finishFailed / finishSkipped → UPDATE
└── findLastSuccessByEngineAndReference → para UI

Main.kt
└── launchBackgroundEngines():
    ├── orchestrator.runPayrollForMonth(now)
    ├── orchestrator.runPayrollForMonth(now.plusMonths(1))
    └── orchestrator.runRecurrence(monthsAhead = 2)
    (cada um em CoroutineScope(Dispatchers.IO) separado)
```
