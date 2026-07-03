# Relatório Técnico — Fase 3A.2 — Transaction Workflow & Financial Lifecycle

**Projeto:** SisgFin  
**Data:** 22/05/2026  
**Pré-requisito:** Fase 3A.1 (fundação transacional)  
**Build:** `./gradlew compileKotlin test` — **OK** (22/05/2026)

---

# 1. Workflow implementado

A fase 3A.2 transforma transações de **registros CRUD** em **objetos com ciclo de vida operacional**:

```
Criação (PENDING/DRAFT/SCHEDULED)
    ↓
[PENDING] ──(due < hoje)──► OVERDUE  (OverdueEngine, domínio)
    ↓
Quitação (recordPayment)
    ├─ paidAmount == amount  → PAID
    └─ paidAmount < amount   → PARTIAL
    ↓
Cancelamento → CANCELED (registro preservado, is_active=0)
```

**Duplicação:** nova transação `PENDING`, sem pagamento/timeline herdada, `parentTransactionId` aponta origem.

**Edição de dados:** descrição, valor, vencimento, conta — **sem** alteração livre de status na UI (status muda via ações e state machine).

---

# 2. State machine criada

**Arquivo:** `financial/transactions/workflow/TransactionStateMachine.kt`

Responsabilidades:

- Mapa `allowedTransitions` centralizado
- Conjunto `forbiddenExplicit` (CANCELED→PENDING, PAID→*, etc.)
- `canTransition` / `assertTransition`
- `allowsPayment`, `allowsCancel`, `isTerminal`
- `resolveStatusAfterPayment(total, paid)` → PAID ou PARTIAL

**Regra:** nenhum `if (status == …)` espalhado em telas para transição; serviço chama `assertTransition` antes de persistir mudança de status.

---

# 3. Regras de transição

## Permitidas (implementadas)

| De | Para |
|----|------|
| DRAFT | PENDING, CANCELED, SCHEDULED |
| SCHEDULED | PENDING, CANCELED |
| PENDING | PAID, OVERDUE, CANCELED, PARTIAL |
| OVERDUE | PAID, PARTIAL, CANCELED |
| PARTIAL | PAID, CANCELED |

## Proibidas (explícitas + ausência no mapa)

| De | Para | Motivo |
|----|------|--------|
| CANCELED | PENDING | Reversão futura apenas |
| PAID | DRAFT, PENDING, PARTIAL, OVERDUE | Estado terminal financeiro |
| Qualquer terminal | Qualquer não-terminal | PAID/CANCELED sem saída |

## Transições sistema

- `PENDING → OVERDUE` via `OverdueEngine` + `syncOverdueStatuses()` (com `assertTransition`)

---

# 4. Payment workflow

**Método:** `TransactionService.recordPayment(id, paymentDate, paidAmount: Money)`

### Regras

1. `TransactionValidator.validatePayment` — paid > 0, paid ≤ total  
2. `TransactionStateMachine.allowsPayment(currentStatus)`  
3. `resolveStatusAfterPayment` → PAID ou PARTIAL  
4. `assertTransition(from, newStatus)`  
5. Persistência: `paymentDate`, `paidAmount`, `status`  
6. `LedgerService.recordPayment()` — **stub** (gancho ledger)  
7. Timeline: `PAYMENT` ou `PARTIAL_PAYMENT`  
8. Audit: `TRANSACTION_PAID` ou `TRANSACTION_PARTIAL_PAYMENT` + `TRANSACTION_STATUS_CHANGED`

### Pagamento total vs parcial

| Condição | Status |
|----------|--------|
| `paidAmount >= amount` | PAID |
| `0 < paidAmount < amount` | PARTIAL |

**Não implementado:** múltiplos pagamentos, histórico de parcelas de pagamento, split.

**UI:** `PaymentRecordDialog` — valor pago + data obrigatórios.

---

# 5. Tratamento de overdue

**Arquivo:** `financial/transactions/workflow/OverdueEngine.kt`

```kotlin
shouldMarkOverdue: status == PENDING && isActive && dueDate < today
```

**Aplicação:** `TransactionService.syncOverdueStatuses()` chamado no início de `listAll()`:

- Persiste `OVERDUE` no banco (domínio, não só cor visual)
- Timeline `OVERDUE`
- Audit `TRANSACTION_OVERDUE` + `TRANSACTION_STATUS_CHANGED`

**Preparado para scheduler:** método isolado, pode ser invocado por job futuro sem passar pela UI.

**Limitação:** não rebaixa OVERDUE→PENDING se usuário alterar vencimento (update manual de dueDate sem regra de reclassificação).

---

# 6. Timeline operacional

## Tabela `transaction_timeline_events` (V7)

| Coluna | Uso |
|--------|-----|
| event_type | CREATED, UPDATED, STATUS_CHANGED, PAYMENT, … |
| message | Texto operacional humano |
| amount_value | Valor em pagamentos |
| status_from / status_to | Mudanças de estado |
| performed_by | Usuário sessão |

**Repositório:** `TransactionTimelineRepository`

**Distinção vs AuditLog:** timeline é **contextual da transação** (UX painel); audit é trilha global compliance.

**Exemplo UI:** `21/05 — 14:30` + label + mensagem no painel direito.

---

# 7. Melhorias de UX

## Painel direito evoluído (`TransactionDetailsPanel.kt`)

- Resumo financeiro (total, pago, vencimento, conta)
- Badge status + tipo
- Ações rápidas contextuais (Quitar, Cancelar, Duplicar, Editar)
- Formulário de edição (sem chips de status livres)
- Timeline integrada
- Dialog de pagamento com valor parcial

## Tabela

- Indicador lateral 3px (vermelho OVERDUE, âmbar PARTIAL, verde suave PAID)
- Hover funcional (`hoverable`)
- Texto atenuado para PAID
- Subtexto valor pago em PARTIAL

## Filtros (`TransactionFilterBar`)

- Todas, Vence hoje, Vencidas, Pagas, Pendentes, Despesas, Receitas, 30 dias
- Busca com foco Ctrl+F

---

# 8. Context menus implementados

**Gatilho:** long click / clique direito na linha (`onLongClick` em `combinedClickable`)

**Menu:**

- Abrir detalhes
- Editar (popup)
- Quitar (se aplicável)
- Duplicar
- Cancelar (se aplicável)

Implementado via `DropdownMenu` ancorado ao estado `contextMenuTx`.

---

# 9. Atalhos implementados

| Atalho | Ação | Escopo |
|--------|------|--------|
| ESC | Fecha popup + painel | `TransactionsScreen` onPreviewKeyEvent |
| Ctrl+F | Foco busca | `FocusRequester` |
| Ctrl+D | Duplica selecionada | |
| Enter | Abre edição rápida (popup) | |
| Delete | Cancela selecionada | |

**Não ligados globalmente no MainLayout** — apenas na tela Movimentações (correto para fase).

**Ctrl+S / F5:** não adicionados nesta tela (já definidos em `KeyboardShortcuts`, não wired globalmente).

---

# 10. Auditoria adicionada

| Ação | Quando |
|------|--------|
| TRANSACTION_STATUS_CHANGED | Qualquer mudança de status |
| TRANSACTION_PAID | Quitação total |
| TRANSACTION_PARTIAL_PAYMENT | Quitação parcial |
| TRANSACTION_DUPLICATED | duplicate() |
| TRANSACTION_OVERDUE | OverdueEngine |
| (+ anteriores CREATED, UPDATED, CANCELED) | |

**Formato `newValue`:** `status=PAID;from=PENDING;amount=Money(...)` — melhor que só descrição, ainda não JSON estruturado.

**Timeline** duplica informação para UX; audit permanece fonte compliance.

---

# 11. Preparação para ledger

```kotlin
// financial/ledger/LedgerService.kt
fun recordPayment(transaction, paidAmount, paymentDate) { /* no-op */ }
```

Chamado em `recordPayment()` **após** validação e **antes** de retornar à UI.

**Campos prontos:** `ledgerEntryId` na entidade; sem preenchimento nesta fase.

**Próximo passo ledger:** implementar posting, preencher `ledgerEntryId`, bloquear edição pós-post.

---

# 12. Riscos restantes

| Risco | Severidade |
|-------|------------|
| Status na 3A.1 ainda editável via `update()` se API chamada com status diferente | Média — UI bloqueia, service ainda aceita se caller passar |
| OVERDUE não reverte se vencimento prorrogado | Média |
| `syncOverdue` em todo `listAll()` — side effect em leitura | Média (padrão aceitável desktop, problemático em API REST futura) |
| Pagamento parcial único — segundo pagamento não soma | Alta para contas reais (esperado nesta fase) |
| Context menu via long click — comportamento pode variar por SO | Baixa |
| VM `markAsPaid` legado redireciona para `recordPayment` total | Baixa |

---

# 13. Limitações atuais

- Sem múltiplos pagamentos / histórico de pagamentos  
- Sem recorrência / parcelamentos  
- Sem projeção de fluxo de caixa  
- Sem conciliação bancária  
- Sem saldo automático de conta  
- Sem scheduler dedicado (overdue roda no load)  
- Período customizado só preset “30 dias”  
- Supplier/project não no painel  
- Testes automatizados ausentes para state machine e payment  

---

# 14. Problemas ainda não resolvidos

1. Dashboard legado (`LegacyTransaction`) vs motor novo  
2. Unificação de saldo de `FinancialAccount`  
3. Autorização por perfil nas ações financeiras  
4. Propagação de erros de `recordPayment` para snackbar (usa `operationError` no painel, parcial)  
5. Edição rápida ainda permite salvar descrição/valor em transação PAID (deveria ser read-only)  

---

# 15. Antes vs Depois

## Antes (3A.1)

- Status “enum decorativo” — usuário podia escolher PAID no painel sem validação forte  
- `markAsPaid` sempre PAID total  
- OVERDUE manual  
- Sem timeline contextual  
- Sem pagamento parcial  
- Filtros básicos  
- Hover sem menu  
- Sem atalhos na tela  

## Depois (3A.2)

- State machine central  
- Quitação com `paidAmount` + PAID/PARTIAL  
- OVERDUE automático no domínio  
- Timeline + audit semântico  
- Painel operacional com ações por status  
- Duplicação limpa  
- Filtros financeiros profissionais  
- Context menu + teclado + hierarquia visual  

---

# 16. Avaliação HONESTA

## O sistema agora possui ciclo operacional financeiro real?

**Sim, em nível operacional de contas a pagar/receber de desk workstation.**

Fluxos de criar → vencer → quitar parcial/total → cancelar → duplicar estão implementados com persistência, timeline e auditoria. Ainda **não** é contabilidade (sem ledger).

## O workflow está consistente?

**Majoritariamente sim.** State machine + service + overdue engine alinhados. Pequenas brechas: leitura com side-effect (`syncOverdue`), edição de PAID ainda possível via save de campos se não bloqueada na UI.

## O sistema já parece contas a pagar/receber profissional?

**Muito mais próximo.** Filtros “Vence hoje / Vencidas”, quitação com valor, timeline e menu contextual aproximam de software financeiro desktop. Ainda falta conciliação, extrato bancário e visão de fluxo para “profissional completo”.

## O que ainda falta para o ledger?

1. `LedgerService` real com partidas e idempotência  
2. Preencher `ledgerEntryId` e imutabilidade pós-post  
3. Saldo derivado de lançamentos, não de `initialBalance` estático  
4. TRANSFER com par contábil  
5. REVERSAL como evento contábil, não edição de status  
6. Unificar relatórios/dashboard com motor novo  

---

# Estrutura de arquivos (resumo)

```
financial/transactions/workflow/
  TransactionStateMachine.kt
  OverdueEngine.kt
financial/transactions/timeline/
  TimelineEventType.kt
  TransactionTimelineEvent.kt
  TransactionTimelineTable.kt
  TransactionTimelineRepository.kt
financial/ledger/
  LedgerService.kt
financial/transactions/
  TransactionListFilter.kt
  TransactionDetailsPanel.kt
  (+ alterações Transaction, Service, Repository, Screen, ViewModel)
db/migration/
  V7__transaction_workflow.sql
```

---

*Fim do relatório — Fase 3A.2 Transaction Workflow & Financial Lifecycle.*
