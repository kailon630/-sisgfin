# P0-5A — Auditoria pós-correção: principalPaid

**Data:** 2026-08-25  
**Escopo:** auditoria de leitura sobre a Parte A implementada nesta sessão  
**Status da suite:** BUILD SUCCESSFUL — nenhum dado alterado neste documento

---

## 1. Contexto de versão e migrações

### git log --oneline -20 (HEAD = b5bebbe)

```
b5bebbe test(reconciliation): cobertura da query do portao M3 (C-19)
b6b039c docs(t21): relatorio e KANBAN de sanitizacao de transacoes
a427cec fix(data): migracao de sanitizacao de campos de transacao (T-21)
929da8a feat(ofx): sanitiza MEMO e FITID na importacao (T-21)
9b02d1c feat(transactions): sanitiza description, notes e documento na escrita (T-21)
f9ffeed docs(kanban): T-20 restaurado, T-21, C-19, contagem 436, tag r45 corrigida
ed1a94f docs: relatorio T-19 + KANBAN atualizado com T-19 concluido e T-20 registrado
428f599 fix(migration): V35 limpa controles de texto em employees e suppliers (T-19)
577c3c4 feat(validation): TextSanitizer remove controles de celulas SCI/Excel (T-19)
a8dc2cb build: bump packageVersion to 1.0.9
0bb85a4 ci: add write permissions and pwsh path resolution for release
42b4d17 ci: replace softprops release action with native gh cli
f31cd8c ci: retrigger v1.0.9 build
b0752b9 ci: remove duplicate release.yml workflow
b117faa ci: add GitHub Actions workflow for MSI release
3091975 test(concurrency): cobertura do lock otimista + protege deactivate (R4.5)
756b967 docs(r4.5): verificacao DB, doc e KANBAN (R4.5)
a8c164b feat(ui): mensagem de conflito de concorrencia na quitacao (R4.5)
c18a88d feat(transactions): update com verificacao de versao (R4.5)
4bf3383 feat(transactions): coluna version para controle de concorrencia (R4.5)
```

### Migrações em db/migration (V1–V36 + V32 não aplicada)

| Versão | Arquivo | Função | Aplicada em? |
|--------|---------|--------|--------------|
| V1–V29 | (legado) | Estrutura base, workflow, encargos, projetos | 2026-07-03 a 2026-08-10 |
| V30 | `V30__transaction_origin.sql` | Adiciona coluna `origin VARCHAR(20)` em `financial_transactions`; popula com CASE; cria índice. (T-13) | 2026-08-17 |
| V31 | `V31__employees_document_unique_employment_type_name.sql` | Normaliza `employment_type` para `.name`; adiciona `UNIQUE INDEX` em `employees.document`. (T-15, T-10) | 2026-08-17 |
| V32 | `V32__reclassify_transaction_origin.sql` | **NÃO APLICADA** — corrição da lógica de classificação de `origin`: usa campos estruturais (type, ofx_fitid, recurrence_template_id) em vez de ILIKE. (T-13 correção) | — |
| V33 | `V33__transaction_payments.sql` | Cria tabela `transaction_payments` com colunas principal/interest/fine/discount; backfill de duas fontes (timeline e campo colapsado). (M1) | 2026-08-17 |
| V34 | `V34__transaction_version.sql` | Coluna `version INTEGER` em `financial_transactions` para lock otimista. (R4.5) | 2026-08-17 |
| V35 | `V35__sanitize_text_fields.sql` | Limpa caracteres de controle em `employees` e `suppliers`. (T-19) | 2026-08-17 |
| V36 | `V36__sanitize_transaction_text_fields.sql` | Limpa caracteres de controle em `financial_transactions`. (T-21) | 2026-08-17 |

---

## 2. V33 — violação de imutabilidade Flyway

**V33 foi aplicada em dev em 2026-08-17 08:59:16 com checksum `-66395560`.**

```
version | installed_on              | checksum
33      | 2026-08-17 08:59:16.510   | -66395560
```

**V33 foi editada nesta sessão.** A linha modificada:

```sql
-- ANTES (backfill "campo colapsado"):
t.paid_amount - COALESCE(t.interest_amount, 0) - COALESCE(t.fine_amount, 0),

-- DEPOIS:
t.paid_amount,
```

A condição WHERE também foi simplificada (removida a sub-expressão `> 0` que dependia da fórmula).

**Consequência direta:** na próxima inicialização do app com Flyway em modo `validate` ou `migrate`, a verificação de checksum falhará com:

```
Migration checksum mismatch for migration version 33
-> Applied to database : -66395560
-> Resolved locally    : <novo checksum>
```

**Adicionalmente:** V30 e V31 também estão modificadas no working directory (marcadas M no git status ao início desta sessão — modificação pré-existente, anterior a esta sessão). Ambas foram aplicadas em 2026-08-17. Efeito idêntico ao V33.

### Ação obrigatória antes do próximo start

Três caminhos:

1. **Criar V37 de re-backfill** corrigindo os registros afetados e reverter V33/V30/V31 para o conteúdo aplicado (checksums devem bater com `flyway_schema_history`).
2. **Reparar via `flyway repair`** (atualiza os checksums na tabela) — válido apenas em dev, nunca em cliente.
3. **Recriar o banco dev do zero** (aceitável se não houver dados de produção).

Para V30 e V31, a correção adiciona `IF NOT EXISTS` nos DDL statements — operação segura para rodar novamente. Opção de reparação via `flyway repair` é simples nesses dois casos.

---

## 3. Análise dos quatro testes alterados

### 3.1 `SaldoDevedorTest` — B2-03

**Diagnóstico: setup INCORRETO (encoding do bug, não da intenção)**

```diff
-    paidAmount     = "350.00",  // linha 72
+    paidAmount     = "300.00",
```

O comentário original dizia `// paidAmount = 350 (300 principal + 50 juros)` — isso é semântica P0-4 (encargos embutidos em paidAmount). A intenção do teste era provar que `outstandingPrincipal = 700` independentemente de encargos. Mas o setup estava errado: com `paidAmount = 350` e a formula `principalPaid = 350 - 50 = 300`, a asserção `700` passava por uma cadeia de erros que se cancelavam. **O teste validava comportamento incorreto via aritmética acidentalmente correta.**

Com `paidAmount = "300.00"` (principal puro) e `interestAmount = "50.00"` (separado), a asserção `outstandingPrincipal = 700` agora segue o caminho correto: `1000 - 300 = 700`.

### 3.2 `PartialPaymentAccumulationTest` — `applyPayment()` e T4

**`applyPayment()` — encoding do bug:**

```diff
- val newPaidAmount  = (tx.paidAmount ?: Money.ZERO) + principal + juros + multa  // linha 70
- val newPrincipal   = newPaidAmount - newInterest - newFine  // linha 73
+ val newPaidAmount = (tx.paidAmount ?: Money.ZERO) + principal
```

A função espelhava o comportamento de `TransactionService.recordPayment`. A acumulação `+ juros + multa` em `newPaidAmount` era o bug. Corrigida junto com o service.

**T4 — assertion sobre paidAmount INCORRETA:**

```diff
- assertEquals(0, Money.fromString("1050.00").compareTo(tx2.paidAmount!!),  // linha 146
+ assertEquals(0, Money.fromString("1000.00").compareTo(tx2.paidAmount!!),
```

T4 afirmava `paidAmount = 1050` (principal + juros), que era a semântica errada. A asserção `principalPaid = 1000` continuava presente e estava certa. **O teste validava comportamento incorreto na linha 146.** Reescrito para afirmar `paidAmount = 1000` (principal puro) + `outstandingPrincipal = 0`.

A assertiva de saldo (`balance = 8950 com expense = paidAmount`) foi removida: `calculateBalance` não usa `paidAmount` do título — usa `cashEffective` das baixas (M4). Era asserção de cenário hipotético sem correspondência com produção, conforme identificado no P0-5.

**T7 (linha 199):** apenas comentário atualizado. Setup e valores não mudam (sem encargos — `paidAmount == cashEffective` nesse cenário). Sem impacto funcional.

### 3.3 `RecordPaymentIntegrationTest` — 5 testes

**Testes de encargos (3 testes, linhas 238, 254, 271):** assertivas de `paidAmount = 1050 / 1030 / 1070` validavam o bug diretamente. Exemplos:

```diff
- assertEquals(0, Money.fromString("1050.00").compareTo(updated.paidAmount!!))  // linha 249
+ assertEquals(0, Money.fromString("1000.00").compareTo(updated.paidAmount!!))
```

Esses testes foram escritos para caracterizar o comportamento P0-4 como correto — **eram assertivas do bug, não do comportamento esperado.**

**P0-5 PARTIAL (linha 323):** setup com `paidAmount = "350.00"` modelava estado pós-P0-4 (principal + juros embutidos). Corrigido para `"300.00"`. A asserção final `paidAmount = 1050` (linha 337) era consequência do bug; corrigida para `1000`.

### 3.4 `ReconciliationTest` — `ciclo parcial com juros`

**Estados mock — setup INCORRETO:**

```diff
- expense(status = TransactionStatus.PARTIAL, paidAmount = "350.00", interestAmount = "50.00"),  // linha 85
- expense(status = TransactionStatus.PARTIAL, paidAmount = "750.00", interestAmount = "50.00")
+ expense(status = TransactionStatus.PARTIAL, paidAmount = "300.00", interestAmount = "50.00"),
+ expense(status = TransactionStatus.PARTIAL, paidAmount = "700.00", interestAmount = "50.00")
```

`paidAmount = "750.00"` vinha de `350 + 400` (semântica antiga: principal + juros embutidos). Com o fix, a 3ª baixa tentava pagar 300 sobre um `outstandingPrincipal = 1000 - 750 = 250` → `IllegalArgumentException`. O setup estava errado para os novos valores.

**Invariante — reformulado:**

```diff
- val paidAmount = Money.fromString("1050.00")                           // linha 98
- assertEquals(0, paidAmount.compareTo(somaBaixas), ...)
+ val somaCashEffective = inserted.fold(Money.ZERO) { acc, p -> acc + p.cashEffective }
+ val somaEncargos      = Money.fromString("50.00")
+ val principalAcumulado = Money.fromString("1000.00")
+ assertEquals(0, (principalAcumulado + somaEncargos).compareTo(somaCashEffective), ...)
```

O invariante antigo (`Σ cashEffective == paidAmount`) era válido apenas quando `paidAmount` acumulava encargos. O novo invariante correto é `Σ(cashEffective) = paidAmount_principal + interestAmount + fineAmount`, isto é, o total de caixa desembolsado deve igualar principal puro + encargos registrados em colunas separadas.

---

## 4. ReconciliationTest — natureza do teste e `cashEffective`

### O teste exercita código de produção?

**Parcialmente sim.** O teste chama `TransactionService.recordPayment` — o service real, não mockado. Os repositórios são mockados (via `mockk`), mas a lógica de acumulação, validação e resolução de status é executada no código de produção. O padrão `returnsMany` simula o estado persistido sem banco.

Não é um teste de integração (sem banco), mas também não é puramente in-memory: o service real, com suas guardas, validações e transições de estado, é exercitado.

### `cashEffective` em `src/main`

Definido em `TransactionPayment.kt:22`:

```kotlin
val cashEffective: Money
    get() = principalAmount + interestAmount + fineAmount - discountAmount
```

Usado em produção por:

| Arquivo | Contexto |
|---------|---------|
| `TransactionPaymentRepository.kt` | `sumCashEffectiveByAccountAndType`, `sumCashEffectiveForReversalOf`, `sumCashEffectiveTransferIn/Out`, `sumCashEffectiveInPeriod` — base do calculateBalance (M4) |
| `FinancialServices.kt:63` | `calculateBalance` — chama os métodos acima |
| `DashboardViewModel.kt:54` | KPIs de receita/despesa mensais |
| `ReportsExporter.kt:74,165` | Exportação de relatórios |
| `ReportsModels.kt:26` | `cashEffective` exposto como propriedade de linha de relatório |
| `StatementModels.kt:30` | Linha de extrato bancário |
| `BudgetItemRepository.kt:109,174` | Realizado de orçamento (D3b) |
| `TransactionDetailsPanel.kt:541,794` | Exibição de baixas no painel + total pago exibido |

`cashEffective` é o pilar do M4: **toda leitura de saldo, extrato e relatório usa `cashEffective` das baixas, não `paidAmount` do título.**

---

## 5. `sumPaid` e Parte B

### `sumPaid` — implementação atual

```kotlin
// TransactionRepository.kt:590
fun sumPaid(accountId: Int, type: TransactionType): Money = transaction {
    val sumExpr = FinancialTransactionsTable.amount.sum()
    FinancialTransactionsTable
        .select(sumExpr)
        .where {
            (FinancialTransactionsTable.accountId eq accountId) and
            (FinancialTransactionsTable.type eq type.name) and
            (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
            (FinancialTransactionsTable.isActive eq true)
        }
        .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
}
```

**Confirma: `sumPaid` ainda soma `SUM(amount)` — o valor de face do título, não `paid_amount`.** A função existe em `TransactionRepository` mas **`calculateBalance` não a chama mais desde o M4**. O cálculo de saldo migrou para `paymentRepository.sumCashEffectiveByAccountAndType` que lê da tabela `transaction_payments`.

### Parte B foi implementada?

**Não.** `calculateBalance` em `FinancialServices.kt:64` já usa `cashEffective` das baixas desde o M4. O que P0-5 chamava de "assimetria PARTIAL/PAID" e "juros invisíveis para PAID" já foi resolvido pelo M4 — não pelo trabalho desta sessão. Esta sessão (Parte A) corrigiu apenas `principalPaid`/`outstandingPrincipal` e a acumulação de `paidAmount` no service. Nenhuma mudança em `calculateBalance`, `openingBalance` ou queries de saldo foi feita nesta sessão.

---

## 6. `totalDiscount` e `discount_amount`

### Origem de `discount_amount`

**A coluna `discount_amount` NÃO existe em `financial_transactions`.** V27 (`V27__transaction_charges.sql`) adicionou apenas `interest_amount` e `fine_amount`:

```sql
-- V27__transaction_charges.sql
ALTER TABLE financial_transactions
    ADD COLUMN interest_amount DECIMAL(19, 2) NULL,
    ADD COLUMN fine_amount     DECIMAL(19, 2) NULL;
```

O comentário original de V27 dizia: *"interest_amount e fine_amount são informativos (breakdown do paidAmount). O paidAmount continua sendo o total real desembolsado."* — esse é o comentário que propagou a semântica P0-4.

**`discount_amount` existe apenas em `transaction_payments` (V33, linha 12):**

```sql
discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
```

### Fluxo de `totalDiscount` em `recordPayment`

```kotlin
// TransactionService.kt:478
val totalDiscount = if (!discountAmount.isZero()) {
    paymentRepository.sumDiscountByTransaction(id) + discountAmount
} else {
    discountAmount
}
val principalQuitado = newPaidAmount + totalDiscount
```

`sumDiscountByTransaction` lê `discount_amount` da tabela `transaction_payments` (não de `financial_transactions`). O desconto acumulado historicamente (D3a) é somado ao desconto desta baixa para determinar se o principal total + descontos quita o título. `totalDiscount` não é gravado em `financial_transactions` — apenas entra no cálculo de `resolveStatusAfterPayment` e na linha de `transaction_payments`.

---

## 7. Contagem de testes

### Antes desta sessão (HEAD = b5bebbe)

```
461 anotações @Test em 53 arquivos de teste
```

Contagem verificada via `git stash` do estado da sessão + grep.

### Após esta sessão

```
466 anotações @Test em 54 arquivos de teste
```

**Delta: +5 @Test** — todos em `PrincipalPaidTest.kt` (arquivo novo, não rastreado pelo git).

### Discrepância com "469 testes" do Gradle

Gradle reportou 464 testes (primeira execução desta sessão, com 2 falhas) e "BUILD SUCCESSFUL" nas demais. A contagem de 469 pode vir de uma execução anterior onde testes parametrizados geraram múltiplos casos. A diferença entre `@Test` annotations (466) e casos Gradle não é apurada aqui — pode haver `@ParameterizedTest` que expande para N casos.

### Arquivos de teste criados nas últimas 3 sessões (via git log)

```
b5bebbe  FindReconciliationDivergencesTest.kt   (C-19)
         TransactionSanitizeTest.kt              (T-21)
577c3c4  TextSanitizerTest.kt                    (T-19)
3091975  OptimisticLockTest.kt                   (R4.5)
83e10fb  PaymentEntriesTest.kt                   (M4b — extratos por baixa)
```

**Desta sessão (não commitado):**
```
PrincipalPaidTest.kt    — testes A1–A5 (Parte A, principalPaid)
```

---

## Sumário de riscos

| # | Risco | Severidade | Status |
|---|-------|------------|--------|
| R1 | V33 editada após aplicação ao banco dev — checksum Flyway diverge | **ALTO** | Não resolvido — requer `flyway repair` ou V37 |
| R2 | V30, V31 também editadas após aplicação (pré-existente à sessão) | **ALTO** | Não resolvido |
| R3 | V32 não foi aplicada ao banco — não há risco Flyway, mas a migração corretora não está ativa | Médio | Requer aplicação |
| R4 | `sumPaid`/`sumPartialPaid` em `TransactionRepository` — funções órfãs não usadas por `calculateBalance`; podem ser removidas ou documentadas como legado | Baixo | Sem impacto imediato |
| R5 | Invariante de ReconciliationTest agora hardcodeia valores de encargos (`Money.fromString("50.00")`) em vez de lê-los do objeto atualizado — frágil se o teste evoluir | Baixo | Aceitável para Parte A |
