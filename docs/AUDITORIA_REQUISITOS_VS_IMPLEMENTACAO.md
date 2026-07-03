# SisgFin — Auditoria: Requisitos vs Implementação

**Data:** 11/06/2026
**Base:** `REQUISITOS_SISGFIN.md` v2.0 (RN-01 a RN-33)
**Método:** leitura estática de ~102 arquivos Kotlin + migrations Flyway
**Cobertura:** 44 itens auditados

---

## Resultado Geral

| Status | Qtd |
|--------|-----|
| ✅ Implementado | 7 |
| ⚠️ Parcial / Stub | 3 |
| ❌ Ausente | 34 |
| **Total** | **44** |

**Cobertura: ~16% funcional** (os 7 implementados são infraestrutura e mecânica de estado — o domínio financeiro de negócio praticamente não existe ainda).

---

## Módulo 1 — Cadastros

### Fornecedores

| RN | Descrição | Status | Evidência |
|----|-----------|--------|-----------|
| RN-01 | CNPJ/CPF único | ❌ Ausente | Sem `UNIQUE` constraint na coluna `document` (V4 migration) e sem validação de unicidade no `SupplierService` |
| RN-02 | Fornecedor inativo bloqueado em novos lançamentos | ❌ Ausente | `TransactionValidator` não verifica `supplier.isActive` |
| RN-03 | Validação de dígitos verificadores CNPJ/CPF | ❌ Ausente | Campo tratado como string simples; sem algoritmo de validação |

### Contas Financeiras

| RN | Descrição | Status | Evidência |
|----|-----------|--------|-----------|
| RN-04 | Saldo calculado (nunca digitado) | ❌ Ausente | Só existe `initialBalance` na entidade; nenhum cálculo baseado em transações PAID |
| RN-05 | Conta só inativa com saldo zero | ❌ Ausente | `FinancialAccountService` herda `AuditedCrudService` sem override de validação de saldo |
| RN-06 | Conta APLICACAO com campos extras (aplicação, resgate, rendimentos) | ❌ Ausente | `FinancialAccountType` tem `BANK`, `CASH`, `SAVINGS` — sem `APLICACAO`; campos extras inexistentes |

### Projetos

| RN | Descrição | Status | Evidência |
|----|-----------|--------|-----------|
| RN-07 | Projeto com lançamentos não pode ser excluído | ❌ Ausente | `ProjectService` herda sem override; sem consulta de transações vinculadas antes do delete |
| RN-08 | Aviso ao lançar despesa fora do período do projeto | ❌ Ausente | `TransactionValidator` não valida datas contra `project.startDate` / `project.endDate` |

### Plano de Contas

| RN | Descrição | Status | Evidência |
|----|-----------|--------|-----------|
| RN-09 | Seed com categorias TCESP pré-carregadas | ✅ Implementado | `V10__seed_expense_categories.sql` contém ~100 categorias com código hierárquico, grupo, flag `is_income` |
| RN-10 | Categoria com lançamentos não pode ser excluída | ❌ Ausente | `ExpenseCategoryService` sem validação de dependência |

### Usuários

| RN | Descrição | Status | Evidência |
|----|-----------|--------|-----------|
| RN-11 | Toda escrita registra userId + timestamp | ✅ Implementado | `AuditedCrudService` registra INSERT/UPDATE/DELETE em `audit_logs` com `performedBy` + `createdAt`; `TransactionService` também audita |
| RN-12 | Perfis ADMIN e OPERADOR com controle real | ⚠️ Parcial | `UserRole` tem `ADMIN` e `USER` (não `OPERADOR`); `SessionManager.hasPermission()` existe mas nenhuma operação de negócio verifica role — controle de acesso não aplicado no domínio |

---

## Módulo 2 — Lançamentos Financeiros

| RN | Descrição | Status | Evidência |
|----|-----------|--------|-----------|
| RN-13 | PAGO e CANCELADO são terminais | ✅ Implementado | `TransactionStateMachine.isTerminal()` retorna `true` para `PAID`/`CANCELED`; `TransactionDetailsPanel` desabilita edição quando terminal |
| RN-14 | Correção de PAGO = ESTORNO vinculado | ❌ Ausente | Campo `parentTransactionId` existe na entidade mas nunca é populado em contexto de estorno; sem método `reverseTransaction()` |
| RN-15 | Job automático PENDENTE → VENCIDO | ✅ Implementado | `TransactionService.syncOverdueStatuses()` chamado em `listAll()`; `OverdueEngine.shouldMarkOverdue()` verifica `dueDate < hoje`; registra timeline e audit |
| RN-16 | dataPagamento ≥ dataEmissão | ❌ Ausente | `TransactionValidator.validatePayment()` não valida relação entre datas |
| RN-17 | Parcelamento: geração automática de filhos | ❌ Ausente | Campos `installmentCurrent` / `installmentTotal` existem na entidade; zero lógica de geração automática |
| RN-18 | Arredondamento na última parcela | ❌ Ausente | Depende de RN-17 |
| RN-19 | Cancelamento em cascata das parcelas | ❌ Ausente | Depende de RN-17 |
| RN-20 | Transferência = dois lançamentos atômicos | ❌ Ausente | Tipo `TRANSFER` existe; sem lógica de criar par débito/crédito |
| RN-21 | Cancelar transferência cancela ambos os lados | ❌ Ausente | Depende de RN-20 |
| RN-22 | Estorno exige justificativa obrigatória | ❌ Ausente | Campo `notes` é opcional sem validação específica para `REVERSAL` |
| RN-23 | Estorno cria lançamento negativo vinculado | ❌ Ausente | Sem método de estorno; `TransactionType.REVERSAL` existe como enum mas nunca é usado |

---

## Módulo 3 — Saldos e Extrato

| Funcionalidade | Status | Evidência |
|----------------|--------|-----------|
| Painel de saldos por conta com cálculo real | ❌ Ausente | Dashboard soma `initialBalance` das contas — não é cálculo de saldo via transações PAID |
| Extrato cronológico com saldo acumulado | ❌ Ausente | Nenhuma tela de extrato; `Screen.Reports` existe como enum mas `OtherScreens.kt` é stub genérico |

---

## Módulo 4 — Orçamento por Rubrica

| Funcionalidade | Status | Evidência |
|----------------|--------|-----------|
| Entidade / tabela `BudgetItem` | ❌ Ausente | Nenhuma classe ou migration com orçamento por projeto+rubrica |
| RN-24: Realizado calculado automaticamente | ❌ Ausente | Sem entidade de orçamento |
| RN-25/26/27: Saldo disponível em tempo real no formulário | ❌ Ausente | Sem orçamento, sem cálculo possível |

---

## Módulo 5 — Relatórios

| Relatório | Status | Evidência |
|-----------|--------|-----------|
| Livro Diário | ❌ Ausente | Nenhum serviço ou tela |
| Balancete | ❌ Ausente | Nenhum serviço ou tela |
| Demonstrativo Financeiro | ❌ Ausente | Nenhum serviço ou tela |
| Comprovante PDF (RN-31) | ❌ Ausente | Nenhuma dependência de geração de PDF no `build.gradle.kts` |

---

## Módulo 6 — Rastreabilidade

| RN | Descrição | Status | Evidência |
|----|-----------|--------|-----------|
| RN-32 | Audit log imutável | ✅ Implementado | Tabela `audit_logs` (V3 migration) com `entityType`, `entityId`, `action`, `oldValue`, `newValue`, `performedBy`, `createdAt`; `AuditRepository.insert()` sem mecanismo de exclusão |
| RN-33 | Timeline de transação no painel de detalhes | ✅ Implementado | `TransactionTimelineRepository` busca eventos por `transactionId`; `TransactionService.getTimeline()` expõe; `TransactionDetailsPanel` exibe a timeline |

---

## Módulo 7 — Dashboard

| Funcionalidade | Status | Evidência |
|----------------|--------|-----------|
| KPIs com dados reais | ⚠️ Parcial | `DashboardViewModel` calcula `totalBalance`, `totalIncomes`, `totalExpenses` dinamicamente — mas consome tabelas **legadas** (`accounts`, `transactions`), desconectadas do novo motor (`financial_transactions`). KPIs existem mas exibem dados do sistema antigo |

---

## Problemas Estruturais Identificados

### 1. Desconexão entre tabelas legadas e motor novo
O `DashboardViewModel` lê de `accounts` e `transactions` (legado). O motor financeiro real usa `financial_accounts` e `financial_transactions`. Os dois conjuntos de tabelas coexistem sem sincronização — qualquer KPI do dashboard hoje está errado em relação ao motor novo.

### 2. Controle de acesso declarado mas não aplicado
`SessionManager` existe e `UserRole` tem ADMIN/USER, mas nenhuma operação nos Services verifica o perfil do usuário logado. Qualquer usuário pode fazer qualquer coisa no domínio.

### 3. Validações de negócio ausentes no `TransactionValidator`
O validator existe e tem estrutura correta, mas cobre apenas: campos obrigatórios básicos e estado terminal. Todas as validações de negócio (datas, fornecedor ativo, período do projeto, orçamento) estão ausentes.

### 4. Nenhuma funcionalidade financeira de domínio entregue
Os 7 itens implementados são: seed de categorias, audit log, timeline de transação, state machine, e job de vencimento. São infraestrutura sólida — mas o produto ainda não faz nada que o cliente precise usar no dia a dia.

---

## Sequência de Implementação Recomendada

Ordem por dependência e valor para o cliente:

```
1. Corrigir validações no TransactionValidator       (RN-16, RN-02, RN-08)
2. Adicionar UNIQUE constraint em suppliers.document  (RN-01 — 1 linha de SQL)
3. Implementar validação CNPJ/CPF                    (RN-03)
4. Implementar saldo calculado por conta             (RN-04) ← desbloqueia dashboard real
5. Parcelamento automático                           (RN-17/18/19)
6. Transferência atômica                             (RN-20/21)
7. Estorno com justificativa                         (RN-14/22/23)
8. Proteções de delete (RN-05, RN-07, RN-10)
9. BudgetItem + cálculo de orçamento                 (RN-24/25/26/27)
10. Livro Diário                                     (RN-29/30)
11. Balancete e Demonstrativo                        (relatórios)
12. Comprovante PDF                                  (RN-31)
13. Controle de acesso real por perfil               (RN-12)
```
