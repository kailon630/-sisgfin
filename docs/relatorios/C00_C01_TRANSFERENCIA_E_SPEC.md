# C-00 / C-01 — Leitura de Specs e Diagnóstico de Transferência

**Data:** 2026-08-11  
**Escopo:** somente leitura — nenhum arquivo de código alterado  
**Motivação:** consolidar leitura dos 4 documentos principais e diagnosticar se a transferência movimenta saldo corretamente.

---

## PARTE A — C-00 · Documentação existente

### A.1 — Documentos na íntegra

Os 4 documentos lidos integralmente:

- `docs/specs/SPEC_TRANSACTION_PAYMENTS.md`
- `docs/relatorios/R3_SALDO_E_ESTORNO.md`
- `docs/relatorios/R4_ENGINES_CONCORRENCIA.md`
- `docs/specs/SPEC_OPERACAO_CONSULTA.md`

Conteúdo completo de cada um encontra-se no histórico da sessão de 2026-08-11.

---

### A.2 — O SPEC de pagamentos

**Define schema de tabela?** Sim. DDL proposto em `SPEC_TRANSACTION_PAYMENTS.md:31–49`:

```sql
CREATE TABLE transaction_payments (
    id                  SERIAL PRIMARY KEY,
    transaction_id      INTEGER NOT NULL REFERENCES financial_transactions(id),
    payment_date        DATE NOT NULL,
    account_id          INTEGER NOT NULL REFERENCES financial_accounts(id),
    principal_amount    NUMERIC(19,4) NOT NULL CHECK (principal_amount > 0),
    interest_amount     NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (interest_amount >= 0),
    fine_amount         NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (fine_amount    >= 0),
    discount_amount     NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    reversed_by_id      INTEGER NULL REFERENCES transaction_payments(id),
    notes               TEXT NULL,
    created_by          INTEGER NULL REFERENCES users(id),
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_tp_transaction ON transaction_payments(transaction_id);
CREATE INDEX idx_tp_date_account ON transaction_payments(payment_date, account_id);
```

**Foi implementado?** Não. Nenhuma migração Flyway para `transaction_payments` existe. Migrações vão até `V29__transaction_reversed_type.sql`. Cabeçalho do spec: `"Não é para implementar ainda. Documento de decisão."` (`SPEC_TRANSACTION_PAYMENTS.md:3`).

**Define semântica de estorno?** Sim — D1 (`SPEC_TRANSACTION_PAYMENTS.md:83–92`): distingue "estornar uma baixa" (volta a PARTIAL/PENDING) de "estornar o título" (o que existe hoje). Propõe manter ambas.

**Define semântica de transferência?** Não diretamente. Mencionada apenas indiretamente na tabela de impactos (`SPEC_TRANSACTION_PAYMENTS.md:21`): "Título registrado na conta A, pago pela conta B: hoje impossível de representar." O `account_id` próprio na nova tabela resolve isso.

**Define idempotência?** Não explicitamente para `transaction_payments`. Para o título: `reversed_by_id IS NULL` para excluir estornos de baixa na soma.

**Datado/versionado?** Não tem data nem versão explícita no documento. Status declarado: `"Não é para implementar ainda. Documento de decisão. Depende do P0-4 concluído."` (`SPEC_TRANSACTION_PAYMENTS.md:3`). P0-4 está concluído (conforme `docs/relatorios/P0_4_LIQUIDACAO.md`), portanto o bloqueante declarado foi removido, mas o spec ainda não foi acionado.

---

### A.3 — Inventário de docs

```
docs/
├── 01 2026 Extrato65205011450.ofx        (arquivo OFX bruto — dado)
├── 02 2026 Extrato65205011450.ofx        (idem)
├── 03 2026 Extrato65205011450.ofx        (idem)
├── ANALISE_MODULO_MOVIMENTACOES.md
├── ANALISE_PLANILHA_CONTROLE_FINAN.md
├── ANALISE_UX_MOVIMENTACOES.md
├── ARQUITETURA_FINANCEIRO_AP_AR.md
├── AUDITORIA_REQUISITOS_VS_IMPLEMENTACAO.md
├── AUDITORIA_TECNICA_COMPLETA_SISGFIN.md
├── Espelho e resumo da folha (1).xlsx    (planilha de folha — dado)
├── Espelho e resumo da folha.xlsx        (idem)
├── FASE_2.5B_RELATORIO_ARQUITETURAL.md
├── FASE_3A.1_RELATORIO_TRANSACIONAL.md
├── FASE_3A.2_RELATORIO_WORKFLOW.md
├── GUIA_USUARIO.md
├── KANBAN.md
├── P0_4_LIQUIDACAO.md                    (duplicata de relatorios/)
├── REQUISITOS_SISGFIN.md
├── RETRATO_PROJETO.md
├── relatorios/
│   ├── DELTA_RETRATO.md
│   ├── F2_CONTAS_A_PAGAR.md
│   ├── F2_FIX_P0.md
│   ├── P0_4_LIQUIDACAO.md
│   ├── P0_5_ENCARGOS_NO_SALDO.md
│   ├── R1_CONTRAPARTE_LANCAMENTO.md
│   ├── R2_DADOS_BANCARIOS.md
│   ├── R3_SALDO_E_ESTORNO.md
│   ├── R4_ENGINES_CONCORRENCIA.md
│   ├── R5_IA_MOVIMENTACOES.md
│   ├── R6_CLASSIFICACAO.md
│   ├── R7_ORCAMENTO_E_OPERACAO.md
│   ├── R8_CREDOR_LIVRO_DIARIO.md
│   └── C00_C01_TRANSFERENCIA_E_SPEC.md  ← este arquivo
├── screenshots/
│   └── README.md
└── specs/
    ├── SPEC_OPERACAO_CONSULTA.md
    └── SPEC_TRANSACTION_PAYMENTS.md
```

**Tabela por arquivo (specs/ e relatorios/):**

| arquivo | tipo | assunto | status |
|---|---|---|---|
| `specs/SPEC_OPERACAO_CONSULTA.md` | spec | Reorganização Operação×Consulta: F0 TransactionQuery, F1–F8 telas, menu financeiro | Spec ativo — F0 existe em código; telas F2–F8 não |
| `specs/SPEC_TRANSACTION_PAYMENTS.md` | spec | Entidade de baixa `transaction_payments`: DDL, M1–M6 migração, D1–D4 decisões abertas | Spec pendente — "não implementar ainda"; bloqueante P0-4 concluído |
| `relatorios/DELTA_RETRATO.md` | diagnóstico | Reconciliação código real vs RETRATO_PROJETO.md de 2026-08-10; lista C2, R7 como deltas | Diagnóstico |
| `relatorios/F2_CONTAS_A_PAGAR.md` | relatório de execução | Implementação tela Contas a Pagar; 10/10 testes | Concluído |
| `relatorios/F2_FIX_P0.md` | relatório de execução | Correção P0 na tela F2; suite completa verde | Concluído |
| `relatorios/P0_4_LIQUIDACAO.md` | relatório de execução | Liquidação parcial acumulada e validação; 187 testes | Concluído |
| `relatorios/P0_5_ENCARGOS_NO_SALDO.md` | diagnóstico | Verificação pós-P0-4: encargos no saldo; somente leitura | Diagnóstico |
| `relatorios/R1_CONTRAPARTE_LANCAMENTO.md` | diagnóstico | Fornecedor×funcionário; painel não resolve employeeId; 4/4 txs só-funcionário | Diagnóstico; achado 1 parcialmente corrigido (T-block) |
| `relatorios/R2_DADOS_BANCARIOS.md` | diagnóstico | Dados bancários divergentes employees×suppliers; sem remessa para AP | Diagnóstico |
| `relatorios/R3_SALDO_E_ESTORNO.md` | diagnóstico | Fórmula calculateBalance; estorno; representação TRANSFER | Diagnóstico; achado 2 corrigido (C1) |
| `relatorios/R4_ENGINES_CONCORRENCIA.md` | diagnóstico | Idempotência engines sem UNIQUE; concorrência sem lock | Diagnóstico |
| `relatorios/R5_IA_MOVIMENTACOES.md` | diagnóstico | Arquitetura informação Movimentações; mistura AP+AR; sem totais | Diagnóstico |
| `relatorios/R6_CLASSIFICACAO.md` | diagnóstico | 0% classificação CC/categoria na base; folha sem CC; dimensões mortas | Diagnóstico |
| `relatorios/R7_ORCAMENTO_E_OPERACAO.md` | diagnóstico | Estorno dobra realizado; Demonstrativo REVERSAL como receita; folha sem CC | Diagnóstico; achados 1 e 2 corrigidos (C-11) |
| `relatorios/R8_CREDOR_LIVRO_DIARIO.md` | diagnóstico | CounterpartyResolver; credor no Livro Diário; vínculo Employee↔Fornecedor pós-V26 | Diagnóstico; parcialmente desatualizado (T-block corrigiu ReportsViewModel e TransactionsViewModel) |

---

### A.4 — Decisões vs diagnósticos

**Documentos com DECISÕES registradas:**

**1. `docs/specs/SPEC_TRANSACTION_PAYMENTS.md`** — 4 decisões abertas (D1–D4):

| Decisão | Assunto |
|---|---|
| D1 (`SPEC_TRANSACTION_PAYMENTS.md:83`) | Estorno de baixa × estorno de título: duas operações distintas; proposta de manter ambas |
| D2 (`SPEC_TRANSACTION_PAYMENTS.md:95`) | Baixa em conta diferente do título: modelo permite; recomendação = não expor na UI inicialmente |
| D3 (`SPEC_TRANSACTION_PAYMENTS.md:101`) | Desconto afeta o principal para quitação? `principalPago = Σ(principal_amount + discount_amount)`; "confirmar com o contador" |
| D4 (`SPEC_TRANSACTION_PAYMENTS.md:107`) | Fechamento de período: baixa com `payment_date` em período fechado bloqueada; sem guarda ainda |

Todas 4 são **decisões abertas** — documento pede resposta antes de escrever código.

**2. `docs/specs/SPEC_OPERACAO_CONSULTA.md`** — decisões de design registradas:

| Decisão | Localização |
|---|---|
| F0–F8: estrutura das telas, queries, componentes compartilhados | todo o documento |
| Campos não-financeiros editáveis em terminal geram evento de timeline | `SPEC_OPERACAO_CONSULTA.md:242`: "decisão do C2" |
| R7.3: funcionário inativo sem validação — registrar, não resolver agora | `SPEC_OPERACAO_CONSULTA.md:207–208` |
| F-quick antes de tudo (chip "A pagar" é erro factual) | `SPEC_OPERACAO_CONSULTA.md:262` |
| F8 por último, com spec própria, dependente de `transaction_payments` | `SPEC_OPERACAO_CONSULTA.md:264` |

**3. `docs/FASE_3A.1_RELATORIO_TRANSACIONAL.md:79`**:
> "Decisão arquitetural: não migrar/destruir legado nesta fase — evita quebrar dashboard até fase de unificação contábil."

**Todos os R-series (R1–R8) são diagnósticos puros** — identificam bugs, medem estado, listam achados, mas não especificam a solução nem registram uma escolha tomada.

---

### A.5 — Achados abertos consolidados

Nota: "corrigido?" verificado contra código atual (`FinancialServices.kt`, `BudgetItemRepository.kt`, `ReportsViewModel.kt`, `AccountBalanceFormula.kt`, `TransactionService.kt`).

| ID | Documento | Achado (1 linha) | Corrigido? | Evidência |
|---|---|---|---|---|
| R1.1 | R1_CONTRAPARTE_LANCAMENTO | Painel não exibe beneficiário quando só há `employeeId` | PARCIAL | T-block injetou CounterpartyResolver em TransactionsViewModel e ReportsViewModel; `TransactionDetailsPanel.kt` ainda usa `suppliers.find{supplierId}` |
| R1.2 | R1_CONTRAPARTE_LANCAMENTO | Formulário só edita `supplierId`; sem seletor de funcionário | NÃO | `TransactionDetailsPanel.kt` sem campo employeeId |
| R1.4 | R1_CONTRAPARTE_LANCAMENTO | RN-02 não cobre lançamentos de folha (só `supplierId`) | NÃO | `TransactionService.kt:699–706` |
| R2.1 | R2_DADOS_BANCARIOS | Modelos bancários divergentes (employees estruturado × suppliers texto livre) | NÃO | schema atual inalterado |
| R2.2 | R2_DADOS_BANCARIOS | Sem caminho de remessa para pagar fornecedor | NÃO | nenhum exporter Supplier |
| R2.3 | R2_DADOS_BANCARIOS | Funcionário sem dados bancários omitido silenciosamente da remessa | NÃO | `PayrollImportViewModel` idem |
| R3.1 | R3_SALDO_E_ESTORNO | Documentação RN-04 incompleta vs código | NÃO | `REQUISITOS_SISGFIN.md` não atualizado |
| R3.2 | R3_SALDO_E_ESTORNO | Estorno de INCOME aumenta saldo (REVERSAL somado positivo) | CORRIGIDO | C1: `AccountBalanceFormula` com `reversalDebit`; `FinancialServices.kt:67–84`; teste `C1-2` passa |
| R3.4 | R3_SALDO_E_ESTORNO | Serviço/API permitem update de campos em PAID; só UI bloqueia | CORRIGIDO | C2: guarda em `TransactionService.update:107–124` |
| R3.5 | R3_SALDO_E_ESTORNO | Tabela RN do RETRATO omite REVERSAL | NÃO | `RETRATO_PROJETO.md` não atualizado |
| R4.1 | R4_ENGINES_CONCORRENCIA | Idempotência engines sem UNIQUE no banco | NÃO | sem migration UNIQUE em `(employee_id, due_date)` |
| R4.2 | R4_ENGINES_CONCORRENCIA | Falhas Payroll/Recurrence no boot engolidas sem log | NÃO | `Main.kt:169–187` idem |
| R4.5 | R4_ENGINES_CONCORRENCIA | Sem lock otimista / FOR UPDATE em escritas financeiras | NÃO | codebase sem mudança |
| R5.1 | R5_IA_MOVIMENTACOES | Chip "A pagar" mistura receitas e despesas | NÃO | `filterActionRequired` sem filtro de tipo |
| R5.3 | R5_IA_MOVIMENTACOES | Sem totalizadores na listagem | NÃO | `TransactionsScreen` sem rodapé de totais |
| R5.4 | R5_IA_MOVIMENTACOES | Grupos usam `dueDate`; Extrato usa `paymentDate` | NÃO | eixos distintos sem seletor |
| R6.1 | R6_CLASSIFICACAO | Folha do Engine sem CC/categoria (0% classificação) | NÃO | `PayrollEngine.kt:49–59` idem |
| R7.1 | R7_ORCAMENTO_E_OPERACAO | Estorno dobra realizado orçamentário | CORRIGIDO | C-11: `BudgetItemRepository.kt:140–164` |
| R7.2 | R7_ORCAMENTO_E_OPERACAO | Demonstrativo classifica REVERSAL como receita | CORRIGIDO | C-11: `ReportsViewModel.kt:158–167` |
| R7.3 | R7_ORCAMENTO_E_OPERACAO | Engine folha sem CC/categoria | NÃO | idem R6.1 |
| R7.4 | R7_ORCAMENTO_E_OPERACAO | Inativar funcionário deixa PENDING futuros pagáveis | NÃO | `EmployeeService.toggleActive` sem cascata |
| R7.5 | R7_ORCAMENTO_E_OPERACAO | Sem single-instance; API em 0.0.0.0 sem TLS | NÃO | `KtorServer.kt` idem |
| R7.6 | R7_ORCAMENTO_E_OPERACAO | Retorno das engines descartado; sem log/tela de geração | NÃO | `Main.kt` idem |
| R7.7 | R7_ORCAMENTO_E_OPERACAO | Transferência PAID sem caminho de reversão | NÃO | `ReversalEligibility` bloqueia TRANSFER; `PAID → emptySet()` na state machine |
| R8.1 | R8_CREDOR_LIVRO_DIARIO | Livro Diário usava description como fallback de credor | CORRIGIDO | T-block: `ReportsViewModel` usa `counterpartyResolver.resolve(txs).nameFor(tx)`; fallback `"CREDOR NÃO IDENTIFICADO"` |
| R8.2 | R8_CREDOR_LIVRO_DIARIO | Parte CF [DOC] ausente para lançamentos de folha | NÃO | PayrollEngine e PayrollImportService não preenchem documentType/documentNumber |
| R8.3 | R8_CREDOR_LIVRO_DIARIO | CounterpartyResolver não alcançava ReportsViewModel | CORRIGIDO | T-01 injetou resolver em ReportsViewModel |
| R8.7 | R8_CREDOR_LIVRO_DIARIO | TransactionsScreen não passava counterparties ao TransactionListView | CORRIGIDO | T-06: `counterparties = counterparties` em `TransactionsScreen` |

---

## PARTE B — C-01 · Transferência movimenta saldo?

### B.1 — `sumPaidTransferIn()` e `sumPaidTransferOut()` na íntegra

`TransactionRepository.kt:433–461`:

```kotlin
// RN-04 (extensão): transferências que ENTRAM na conta (destino, tem parentId)
fun sumPaidTransferIn(accountId: Int): Money = transaction {
    val sumExpr = FinancialTransactionsTable.amount.sum()
    FinancialTransactionsTable
        .select(sumExpr)
        .where {
            (FinancialTransactionsTable.accountId eq accountId) and
            (FinancialTransactionsTable.type eq TransactionType.TRANSFER.name) and
            (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
            (FinancialTransactionsTable.isActive eq true) and
            (FinancialTransactionsTable.parentTransactionId.isNotNull())
        }
        .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
}

// RN-04 (extensão): transferências que SAEM da conta (origem, não tem parentId de transferência)
fun sumPaidTransferOut(accountId: Int): Money = transaction {
    val sumExpr = FinancialTransactionsTable.amount.sum()
    FinancialTransactionsTable
        .select(sumExpr)
        .where {
            (FinancialTransactionsTable.accountId eq accountId) and
            (FinancialTransactionsTable.type eq TransactionType.TRANSFER.name) and
            (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
            (FinancialTransactionsTable.isActive eq true) and
            (FinancialTransactionsTable.parentTransactionId.isNull())
        }
        .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
}
```

Ambas exigem `status = PAID`. Nenhuma coleta lançamentos PENDING, OVERDUE ou PARTIAL.

---

### B.2 — Existe cascata de quitação?

`recordPayment()` em `TransactionService.kt:364–433` não contém nenhuma referência a `TransactionType.TRANSFER`. Não há lógica especial para o tipo TRANSFER na quitação.

**NÃO EXISTE CASCATA.** Quitar a perna de origem não afeta a perna de destino, e vice-versa. Cada perna precisa ser quitada individualmente.

---

### B.3 — Alguma transferência nasce PAID?

Todos os pontos que mencionam `TransactionType.TRANSFER` no código de produção:

| Arquivo:linha | Contexto | Status inicial |
|---|---|---|
| `TransactionService.kt:269` | `type = TransactionType.TRANSFER` na perna source em `createTransfer()` | `status = TransactionStatus.PENDING` (`TransactionService.kt:270`) |
| dest (mesmo método) | `source.copy(...)` para perna destino | herda `status = PENDING` do source |
| `TransactionService.kt:224,227` | `cancel()` — verificação tipo para cascata | leitura |
| `TransactionRepository.kt:393–461` | `findTransferDestination`, `sumPaidTransferIn/Out` | leitura |
| `ReversalEligibility.kt` | bloqueia estorno de TRANSFER | leitura |
| `StatementRoutes.kt:42` | filtro API extrato | leitura |

**Nenhum caminho cria TRANSFER com status PAID.** OFX não cria TRANSFER. API REST não expõe `createTransfer` — único ponto de criação é o dialog de transferência em `TransactionsScreen.kt:237`, que chama `TransactionService.createTransfer()`, sempre com status `PENDING`.

---

### B.4 — Trace do cenário

**Fórmula ativa** — `AccountBalanceFormula.compute` (`AccountBalanceFormula.kt:29–31`):
```
saldo = initialBalance + income + incomePartial + adjustment + transferIn + reversalCredit
        - expense - expensePartial - transferOut - reversalDebit
```

`transferIn  = sumPaidTransferIn(accountId)`  → type=TRANSFER, status=PAID, parentId IS NOT NULL  
`transferOut = sumPaidTransferOut(accountId)` → type=TRANSFER, status=PAID, parentId IS NULL  

Setup: Bradesco `initialBalance=10.000`, Caixa `initialBalance=2.000`, transferência 5.000.  
`createTransfer(source=Bradesco, dest=Caixa, amount=5.000)` cria:
- source: `type=TRANSFER, status=PENDING, accountId=Bradesco, parentTransactionId=null`
- dest: `type=TRANSFER, status=PENDING, accountId=Caixa, parentTransactionId=sourceId`

| Momento | Bradesco | Caixa | Raciocínio |
|---|---|---|---|
| **a) logo após `createTransfer()`** | **10.000** | **2.000** | Ambas pernas PENDING. `sumPaidTransfer*` exige PAID → 0. Nenhum saldo movimentado. |
| **b) após quitar só a perna de origem** | **5.000** | **2.000** | Source PAID: `sumPaidTransferOut(Bradesco)=5.000`. Dest ainda PENDING: `sumPaidTransferIn(Caixa)=0`. Bradesco: 10.000−5.000=5.000. Caixa inalterada. |
| **c) após quitar só a perna de destino** | **10.000** | **7.000** | Source ainda PENDING: `sumPaidTransferOut(Bradesco)=0`. Dest PAID: `sumPaidTransferIn(Caixa)=5.000`. Bradesco: 10.000−0=10.000. Caixa: 2.000+5.000=7.000. |
| **d) após quitar as duas** | **5.000** | **7.000** | `sumPaidTransferOut(Bradesco)=5.000`, `sumPaidTransferIn(Caixa)=5.000`. Bradesco: 10.000−5.000=5.000. Caixa: 2.000+5.000=7.000. Total sistema: 12.000 (conservado). |

**Conclusão B.4:** Saldo só reflete a transferência após cada perna ser quitada individualmente. Momentos b) e c) produzem saldo assimétrico entre as contas — Bradesco reduzido sem Caixa aumentar (b), ou Caixa aumentada sem Bradesco reduzir (c).

---

### B.5 — Poluição das telas

**OverdueEngine — filtro literal** (`OverdueEngine.kt:12–16`):

```kotlin
fun shouldMarkOverdue(transaction: Transaction, today: LocalDate = LocalDate.now()): Boolean {
    if (transaction.status != TransactionStatus.PENDING) return false
    if (!transaction.isActive) return false
    return transaction.dueDate.toLocalDate().isBefore(today)
}
```

`syncOverdueStatuses()` chama `repository.findPendingActive()` — `status=PENDING AND isActive=true` **sem filtro de tipo**. Pernas TRANSFER com `dueDate` vencida e status PENDING **serão convertidas para OVERDUE**. Transição `PENDING → OVERDUE` permitida na state machine.

**`TransactionQuery.aPagar()` e `aReceber()`** — filtram por `types`:

```kotlin
fun aPagar()   = TransactionQuery(types = setOf(TransactionType.EXPENSE), statuses = setOf(PENDING, OVERDUE, PARTIAL))
fun aReceber() = TransactionQuery(types = setOf(TransactionType.INCOME),  statuses = setOf(PENDING, OVERDUE, PARTIAL))
```

TRANSFER não é EXPENSE nem INCOME — **não aparecem** em `aPagar()` nem `aReceber()`.

**`filterActionRequired()`** (`TransactionRepository.kt:161–174`) — **sem filtro de tipo**:

```kotlin
(FinancialTransactionsTable.status inList listOf(
    TransactionStatus.OVERDUE.name,
    TransactionStatus.PENDING.name,
    TransactionStatus.PARTIAL.name
))
```

Pernas TRANSFER PENDING/OVERDUE **aparecem** nessa query.

**Resumo por tela:**

| Tela / query | Pernas TRANSFER aparecem? |
|---|---|
| Movimentações (chip "A pagar", `filterActionRequired`) | **SIM** — sem filtro de tipo |
| Contas a Pagar (`TransactionQuery.aPagar()`, `PayablesViewModel`) | NÃO — type=EXPENSE excluiu |
| Contas a Receber (`TransactionQuery.aReceber()`) | NÃO — type=INCOME excluiu |
| OverdueEngine (conversão PENDING→OVERDUE) | **SIM** — sem filtro de tipo |

---

### B.6 — Direção pela nulidade de `parentTransactionId`

Expressão literal:

```kotlin
// sumPaidTransferIn — TransactionRepository.kt:443
(FinancialTransactionsTable.parentTransactionId.isNotNull())

// sumPaidTransferOut — TransactionRepository.kt:458
(FinancialTransactionsTable.parentTransactionId.isNull())
```

**Outras relações que usam `parentTransactionId`:**

| Relação | Onde | Como preenche |
|---|---|---|
| Parcelamento (filhos 2..N) | `TransactionService.generateInstallments:473–488` | `child.copy(parentTransactionId = parentId)` — aponta para parcela pai |
| Estorno | `TransactionService.reverseTransaction:339` | `parentTransactionId = originalId` — reversal aponta para o original |
| Duplicata | `TransactionService.duplicate:453` | `copy(parentTransactionId = source.id)` — cópia aponta para o original |
| TRANSFER destino | `TransactionService.createTransfer:290–294` | `source.copy(parentTransactionId = sourceId)` |

**Risco de contaminação:** `sumPaidTransferIn` filtra `type=TRANSFER AND parentId IS NOT NULL`. Uma duplicata de uma perna TRANSFER (type=TRANSFER, parentId preenchido) poderia ser contada como "entrada" erroneamente se o accountId bater. `allowsDuplicate` retorna `true` para qualquer status — nenhuma validação impede duplicar uma perna TRANSFER.

`findTransferDestination(sourceId)` usa `type=TRANSFER AND parentTransactionId=sourceId` — defesa adicional contra estorno/parcela com mesmo parentId, mas não contra duplicata de TRANSFER.

---

### B.7 — Cancelamento

`cancel()` em `TransactionService.kt:201–243` — trecho RN-21:

```kotlin
// RN-21: cancelar um lado de uma transferência cancela o par vinculado
if (existing.type == TransactionType.TRANSFER) {
    val counterpart = if (existing.parentTransactionId != null) {
        repository.findById(existing.parentTransactionId)
            ?.takeIf { it.type == TransactionType.TRANSFER }
    } else {
        repository.findTransferDestination(id)
    }
    counterpart?.let { other ->
        if (TransactionStateMachine.allowsCancel(other.status)) {
            repository.deactivate(other.id)
            addTimeline(other.id, TimelineEventType.CANCELED,
                "Cancelada em cascata — transferência vinculada #$id",
                null, other.status, TransactionStatus.CANCELED)
            audit(...)
        }
    }
}
```

**Cascata bidirecional existe:**
- Cancelar origem (`parentId=null`) → busca destino via `findTransferDestination(id)` → cancela.
- Cancelar destino (`parentId!=null`) → busca origem via `findById(parentTransactionId)?.takeIf{type=TRANSFER}` → cancela.

**Limitação crítica:** `allowsCancel(other.status)` retorna `!isTerminal(status)`. `isTerminal(PAID) = true`. Se qualquer perna já estiver PAID, a cascata **falha silenciosamente** — sem exceção, sem log. Uma transferência com perna de origem já quitada não pode ter a perna destino cancelada via cascata.

---

### B.8 — Cobertura de teste

Casos em `TransferAndReversalTest.kt` (26 testes):

```
RN-20 conta de origem igual ao destino deve ser rejeitado
RN-20 valor zero deve ser rejeitado
RN-20 valor negativo deve ser rejeitado
RN-20 valor positivo e contas distintas passam validacao
RN-21 transferencia PENDING permite cancelamento
RN-21 transferencia PAID nao permite cancelamento em cascata
RN-21 transferencia CANCELED nao repete cascata
RN-22 justificativa em branco deve ser rejeitada
RN-22 justificativa vazia deve ser rejeitada
RN-22 justificativa valida passa validacao
RN-23 estorno de PENDING lanca excecao
RN-23 estorno de CANCELED lanca excecao
RN-23 estorno de PAID passa validacao de status
RN-23 estorno de outro REVERSAL lanca excecao
C1-1 EXPENSE 1000 pago e estornado restaura saldo inicial
C1-2 INCOME 1000 recebido e estornado restaura saldo inicial
C1-3 ADJUSTMENT 500 e estornado restaura saldo inicial
C1-4 TRANSFER perna de saida nao pode ser estornada
C1-5 TRANSFER perna de entrada nao pode ser estornada
C1-6 estorno de estorno lanca excecao
C1-8 openingBalance com estorno de INCOME mesma semantica que calculateBalance
C1-BUG legado somar REVERSAL sempre positivo infla saldo apos estorno de INCOME
RN-04 saldo com transferencia saida reduz conta origem
RN-04 saldo com transferencia entrada aumenta conta destino
RN-04 saldo com estorno recupera valor da despesa
RN-04 saldo completo com todos os tipos
```

**Existe teste afirmando efeito da transferência no saldo das CONTAS?**

`RN-04 saldo com transferencia saida reduz conta origem` e `RN-04 saldo com transferencia entrada aumenta conta destino` testam cada perna em isolamento contra `AccountBalanceFormula.compute` com parâmetros diretos — testes de fórmula em memória, não de banco.

**Não existe** nenhum teste que:
- Crie o par via `createTransfer()` e verifique `calculateBalance()` nas duas contas simultaneamente
- Verifique os estados intermediários b) e c) do B.4 (saldo assimétrico entre as contas)
- Verifique que o saldo permanece inalterado enquanto ambas as pernas são PENDING (momento a)
- Verifique que quitar somente uma perna produz saldo assimétrico

Cobertura atual: validações de pré-condição (RN-20) e elegibilidade de estorno (C1-4/5). Lifecycle de saldo da transferência completa não é coberta.

---

## SÍNTESE

- **SPEC de pagamentos é aproveitável como base?** PARCIAL — DDL e M1–M6 usáveis; D1–D4 precisam de decisão antes de qualquer código; bloqueante P0-4 já concluído.
- **Decisões de negócio ainda abertas no E2:** mínimo 4 (D1–D4 do SPEC_TRANSACTION_PAYMENTS) + 3 do SPEC_OPERACAO_CONSULTA (F5 beneficiário unificado, R7.3 funcionário inativo, F8 dependente de `transaction_payments`).
- **Transferência movimenta saldo hoje?** Não enquanto as pernas permanecem PENDING — saldo só muda quando cada perna é quitada individualmente via `recordPayment()`, sem cascata entre elas; estados intermediários produzem saldo assimétrico entre as contas.
