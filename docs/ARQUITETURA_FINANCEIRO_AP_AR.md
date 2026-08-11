# Análise Arquitetural — Motor Financeiro (AP/AR)
**SisgFin — Sistema de Gestão Financeira**
**Data da análise:** 2026-07-28
**Documento gerado por:** Claude (Sonnet 4.6) no papel de arquiteto de software especialista em sistemas financeiros AP/AR

---

## Papel assumido

Durante esta análise, o assistente assumiu o papel de **arquiteto de software especialista em módulos financeiros de contas a pagar e a receber (AP/AR)**, com base em padrões de mercado de ERPs e sistemas de gestão financeira voltados ao segmento brasileiro. O objetivo não foi corrigir pontualmente, mas fazer uma leitura crítica de toda a estrutura do motor transacional e questionar se o que foi implementado representa a forma correta e robusta de se construir esse tipo de módulo.

---

## Referências utilizadas

| Sistema | Segmento | O que foi referenciado |
|---|---|---|
| **ContaAzul** | PME brasileiro | Separação AP/AR, painel de resumo, filtros por período |
| **Omie** | PME/ERP brasileiro | Modelo de parceiro de negócio unificado, múltiplos pagamentos |
| **Totvs Protheus (FINA)** | Enterprise brasileiro | FINA010/FINA020, status REVERSED, campo discriminador de relacionamento |
| **Sienge** | Construção civil / terceiro setor | Controle de convênios, centros de custo por projeto |
| **QuickBooks** | PME internacional | Bills (AP) vs Invoices (AR), estrutura de pagamento acumulativo |
| **Xero** | PME internacional | Partial payment model, reconciliation flow |
| **Nibo** | Micro/pequeno negócio BR | Tela unificada com filtros — caso de uso de tela consolidada bem feita |
| **Conta Simples** | Startups/fintech BR | Modelo unificado simplificado, boa distinção visual entrada/saída |
| **Padrão BPMN AP/AR** | Arquitetura geral | Ciclo de vida de títulos, workflow de aprovação de pagamento |

---

## Contexto do sistema

- **Sistema:** SisgFin
- **Cliente:** Associação Terapêutica Cannabis Medicinal Flor da Vida (terceiro setor)
- **Stack:** Kotlin + Compose Desktop + Exposed ORM + PostgreSQL
- **Módulos presentes:** Funcionários, Folha de Pagamento, Movimentações Financeiras, OFX, Orçamento, Projetos/Convênios, Contratos, Relatórios
- **Escala:** ~80 funcionários, um operador financeiro

---

## Arquivos do núcleo financeiro analisados

| Arquivo | Papel |
|---|---|
| `Transaction.kt` | Modelo de dados da transação |
| `FinancialTransactionsTable.kt` | Mapeamento ORM para o banco |
| `TransactionStatus.kt` | Enum de status |
| `TransactionType.kt` | Enum de tipo |
| `TransactionService.kt` | Regras de negócio (criação, cancelamento, pagamento, estorno, parcelamento, transferência, recorrência, OFX, folha) |
| `TransactionRepository.kt` | Acesso a dados — queries e aggregations |
| `TransactionValidator.kt` | Validações de domínio |
| `TransactionStateMachine.kt` | Máquina de estados de status |
| `OverdueEngine.kt` | Motor de vencimento |
| `LedgerService.kt` | Gancho para livro contábil (não-op atual) |
| `TransactionsViewModel.kt` | ViewModel da tela unificada |
| `TransactionDetailsPanel.kt` | Painel lateral de detalhe/criação |
| `TransactionsScreen.kt` | Tela principal de movimentações |
| `PayrollEngine.kt` | Motor de geração de lançamentos de folha |
| `EmployeeService.kt` | Serviço de funcionários (integração com folha) |

---

## Parte 1 — Diagnóstico arquitetural: os 10 problemas identificados

### Problema 1 — Contraparte: estrutura incompleta para funcionários
**Gravidade: Alta | Categoria: Estrutural**

O campo `supplierId` assumiu o papel de contraparte única para todos os lançamentos. O problema é que o negócio tem três naturezas de contraparte distintas:

| Lançamento | Contraparte esperada | Situação atual |
|---|---|---|
| Despesa com fornecedor | `supplierId` | ✓ funciona |
| Receita de cliente | `supplierId` (entityType=CLIENTE) | ✓ funciona |
| Pagamento de folha automático | `employeeId` (gravado pelo PayrollEngine) | ✗ **invisível na UI** |
| Pagamento avulso a funcionário | `employeeId` | ✗ **impossível vincular** |
| Encargo sem contraparte (tarifa, IOF) | Nenhum | ✓ funciona |

O campo `employeeId` existe na tabela (`employee_id`) e é corretamente gravado pelo `PayrollEngine`, mas o `TransactionDetailsPanel` nunca o exibe nem permite selecioná-lo. O `TransactionsViewModel` não carrega a lista de funcionários. O usuário que abre um lançamento de folha gerado automaticamente não vê o nome do funcionário em lugar nenhum.

Para lançamentos manuais de adiantamento, vale ou reembolso a funcionários, o usuário é obrigado a deixar o campo de fornecedor vazio ou criar o funcionário como fornecedor — ambos incorretos estruturalmente.

**Referência:** Omie e Protheus usam o conceito de **parceiro de negócio** (_business partner_) que unifica clientes, fornecedores e funcionários sob um único ponto de referência. O SisgFin partiu com tabelas separadas sem um campo unificado, gerando esse ponto cego.

**Caminho de correção sugerido:**
- Adicionar ao `TransactionsViewModel` um `StateFlow<List<Employee>>`
- No `TransactionDetailsPanel`, quando `type == EXPENSE`, exibir um campo "CONTRAPARTE" que aceita seleção de fornecedor **ou** funcionário, com discriminador `counterpartType`
- O `employeeId` torna-se editável (atualmente bloqueado em `update()`)

---

### Problema 2 — Pagamento parcial: modelo não acumulativo
**Gravidade: Alta | Categoria: Estrutural**

`TransactionService.recordPayment` **substitui** `paidAmount`, não acumula. O fluxo atual com uma despesa de R$ 1.000:

1. Despesa de R$ 1.000 → PENDING
2. Usuário paga R$ 400 → `paidAmount = 400`, status = PARTIAL ✓
3. Usuário quer pagar o restante e digita R$ 600
4. Sistema calcula: `600 < 1000` → **continua PARTIAL**, `paidAmount = 600` (os R$ 400 anteriores são perdidos)
5. Para fechar o título, o usuário precisa digitar R$ 1.000 (o total original), não R$ 600 (o restante)

Além disso, `findUnpaid` para o fluxo de caixa projeta o lançamento PARTIAL pelo `amount` total (R$ 1.000), não pelo saldo restante (R$ 600). O **fluxo de caixa está inflado** para lançamentos com pagamento parcial.

**Referência:** ContaAzul, QuickBooks e Xero trabalham com **múltiplos recebimentos/pagamentos** em uma tabela associativa separada, onde cada ato de pagamento cria um registro próprio e o status PARTIAL é resolvido pela soma acumulada.

**Caminho de correção sugerido:**
- Criar tabela `transaction_payments(id, transaction_id, paid_amount, payment_date, interest, fine, created_by, created_at)`
- `paidAmount` no modelo principal passa a ser calculado (soma dos registros de `transaction_payments`)
- `recordPayment` insere em `transaction_payments` e recalcula o status
- `findUnpaid` projeta `amount - sum(paid_amount)` como saldo restante

---

### Problema 3 — Colisão de nome de coluna no banco
**Gravidade: Alta | Categoria: Naming / Banco de dados**

```kotlin
// FinancialTransactionsTable.kt
val costCenterId = integer("project_id").nullable()         // coluna "project_id" → é o centro de custo
val projectId    = integer("financial_project_id").nullable() // coluna "financial_project_id" → é o projeto
```

A coluna `project_id` no banco de dados é o **centro de custo**, não o projeto. Isso é resultado de uma renomeação de conceito que não foi acompanhada por uma migration de rename da coluna. Consequências:

- Qualquer SQL ad hoc, exportação, BI ou auditoria direta no banco lê `project_id` como projeto e está lendo centro de custo
- Novo desenvolvedor que ler o schema sem o código Kotlin vai mapear errado
- Dificulta onboarding e manutenção futura

**Caminho de correção sugerido:**
- Migration: `ALTER TABLE financial_transactions RENAME COLUMN project_id TO cost_center_id`
- Atualizar o mapeamento em `FinancialTransactionsTable.kt`
- Garantir que nenhuma query SQL raw ou view no banco use o nome antigo

---

### Problema 4 — SCHEDULED nunca transiciona para OVERDUE
**Gravidade: Média | Categoria: Lógica de negócio**

`OverdueEngine.shouldMarkOverdue` verifica exclusivamente `status == PENDING`:

```kotlin
fun shouldMarkOverdue(transaction: Transaction, today: LocalDate): Boolean {
    if (transaction.status != TransactionStatus.PENDING) return false  // SCHEDULED nunca entra aqui
    return transaction.dueDate.toLocalDate().isBefore(today)
}
```

A state machine também não tem a transição `SCHEDULED → OVERDUE`. Um lançamento agendado cujo vencimento passou fica em SCHEDULED indefinidamente — não aparece como ação necessária, não alerta o usuário, é silenciado.

**Caminho de correção sugerido:**
- Adicionar `SCHEDULED → OVERDUE` na `TransactionStateMachine`
- Atualizar `OverdueEngine.shouldMarkOverdue` para incluir `SCHEDULED`
- Ou: definir que SCHEDULED vira PENDING no dia do vencimento (mais correto semanticamente — a data chegou, deixou de ser agendado) e aí PENDING → OVERDUE se não pago

---

### Problema 5 — `parentTransactionId` com quatro significados distintos
**Gravidade: Média | Categoria: Estrutural**

O campo `parentTransactionId` é usado para representar quatro relacionamentos diferentes:

| Contexto | Pai | Filho |
|---|---|---|
| Parcelamento | Parcela 1 (pai) | Parcelas 2..N (filhos) |
| Transferência | Conta origem (pai) | Conta destino (filho com `type=TRANSFER`) |
| Estorno | Lançamento original (pai) | Registro de estorno (filho com `type=REVERSAL`) |
| Duplicação | Lançamento copiado (pai) | Duplicata (filho) |

Isso gera ambiguidade real: `findActiveChildrenOf(parentId)` foi escrito para buscar parcelas filhas canceláveis, mas retorna qualquer registro com aquele `parentTransactionId` que seja PENDING/DRAFT — incluindo uma duplicata ou o destino de uma transferência. A lógica de cascata de cancelamento (RN-19) pode cancelar uma duplicata criada intencionalmente, confundindo os relacionamentos.

**Caminho de correção sugerido:**
- Campos separados: `installmentParentId`, `transferPairId`, `reversalOfId`
- Ou: campo discriminador `parentRelationType` (INSTALLMENT / TRANSFER / REVERSAL / DUPLICATE)
- Migration necessária para separar os registros existentes por tipo

---

### Problema 6 — Estorno não altera o status do lançamento original
**Gravidade: Média | Categoria: Lógica de negócio**

`reverseTransaction` cria um novo registro `type=REVERSAL, status=PAID`, mas o lançamento original **permanece como PAID**. Do ponto de vista de relatórios e do usuário, ambos aparecem como pagamentos realizados — o estorno não neutraliza visualmente o original.

Em qualquer relatório de totais (sum de PAID por período, por projeto, por categoria), o original PAID e o REVERSAL PAID somam dois valores positivos sem compensação, inflando o total.

**Referência:** Totvs Protheus adiciona o status `REVERSED` ao lançamento original após um estorno. ContaAzul marca o original como "estornado" e exclui ambos do saldo líquido.

**Caminho de correção sugerido:**
- Adicionar `REVERSED` ao `TransactionStatus` (não é terminal para quitação, mas é informativo)
- Após criar o REVERSAL, marcar o original com `status=REVERSED`
- Relatórios filtram: soma de PAID exclui REVERSED (líquido zero = correto)
- Ou: o REVERSAL usa valor negativo (abordagem contábil) — mais complexo mas mais puro

---

### Problema 7 — `sumRealizedByProject` usa `paidAmount` que pode ser nulo
**Gravidade: Média | Categoria: Integridade de dados**

```kotlin
fun sumRealizedByProject(projectId: Int): Money = transaction {
    val sumExpr = FinancialTransactionsTable.paidAmount.sum()  // paidAmount pode ser null em PAID
    ...
}
```

Lançamentos criados via `createFromOfx` são inseridos com `status=PAID` mas `paidAmount=null` (a transação OFX tem `amount` preenchido mas não necessariamente `paidAmount`). A soma do realizado por projeto retorna zero para esses lançamentos — o relatório de projetos subtrai o que foi pago via importação bancária.

O mesmo problema potencialmente afeta `sumConsumedByContract`.

**Caminho de correção sugerido:**
- Na query: usar `COALESCE(paid_amount, amount)` para lançamentos PAID
- Ou: garantir que `createFromOfx` sempre copie `amount` para `paidAmount` quando `status=PAID`
- Opção mais limpa: regra de domínio — um lançamento PAID **sempre** tem `paidAmount` preenchido

---

### Problema 8 — `DRAFT` e `ADJUSTMENT` são tipos órfãos
**Gravidade: Baixa | Categoria: Dead code / Feature incompleta**

**DRAFT:** Existe no enum, na state machine (`DRAFT → PENDING/CANCELED/SCHEDULED`) e na lógica de `create()`, mas `openNewExpense/openNewIncome` criam diretamente com `status=PENDING`. Não há botão "Salvar rascunho" na UI. Nenhum caminho funcional de uso.

**ADJUSTMENT:** Existe em `TransactionType`, é contabilizado no `openingBalance` como entrada aditiva, mas:
- Não há UI para criar um ajuste
- Não há regra de negócio definindo quando usar (quem autoriza? que natureza tem?)
- Não há separação visual de um `INCOME` positivo
- Um ajuste negativo (estorno de saldo, correção a menor) não teria comportamento definido

**Caminho de correção sugerido:**
- DRAFT: implementar "Salvar rascunho" no painel, ou remover o status e simplificar
- ADJUSTMENT: definir a regra de negócio (caso de uso real no contexto da Flor da Vida) ou remover do enum para evitar confusão

---

### Problema 9 — Lançamentos cancelados são invisíveis — sem trilha de auditoria para o usuário
**Gravidade: Média | Categoria: UX / Compliance**

`repository.deactivate()` seta `isActive=false` + `status=CANCELED`. O `baseActiveQuery()` filtra `isActive=true`. O `filterByStatus(CANCELED)` também adiciona `isActive=true`. Resultado: **nenhuma query da UI retorna lançamentos cancelados**.

O usuário não tem como ver o que foi cancelado, quem cancelou, quando ou por quê. A tabela `audit_log` registra esses eventos, mas não há tela que a consulte.

Para uma organização do terceiro setor sujeita a prestação de contas e auditorias externas (TCE, convênios federais, etc.), rastreabilidade de cancelamentos é crítica.

**Caminho de correção sugerido:**
- Remover `isActive=true` do `filterByStatus(CANCELED)` — cancelados com `isActive=false` passam a ser visíveis quando o usuário filtra por "Cancelados"
- Adicionar filtro "Cancelados" nas telas de movimentações
- Tela de histórico de auditoria consultando `audit_log` por entidade

---

### Problema 10 — `syncOverdueStatuses` executado a cada `listAll()`
**Gravidade: Baixa | Categoria: Performance / Efeito colateral**

```kotlin
override fun listAll(): List<Transaction> {
    syncOverdueStatuses()  // atualiza todos os PENDING no banco a cada carregamento
    ...
}
```

Cada abertura da tela de movimentações ou troca de filtro executa uma varredura de todos os lançamentos PENDING, avalia o vencimento e potencialmente persiste updates. Com poucos registros é imperceptível, mas é uma operação O(n) com efeito colateral de escrita embutida em uma leitura.

**Referência:** O padrão correto é um **scheduler** (tarefa agendada) que executa uma vez por dia — no startup do app ou via job interno — sem acoplar a lógica de atualização de status à listagem.

**Caminho de correção sugerido:**
- Mover `syncOverdueStatuses()` para um `ScheduledTask` executado no boot do app (`Main.kt`) e opcionalmente repetido a cada 24h
- Remover o `syncOverdueStatuses()` do `listAll()`

---

## Parte 2 — Tela unificada vs. separação AP / AR

### Como os sistemas de referência tratam

Todos os sistemas enterprise (ContaAzul, Omie, Totvs FINA, Sienge, QuickBooks, Xero) separam em dois módulos distintos:

| Módulo | Nome | O que entra |
|---|---|---|
| AP — Accounts Payable | Contas a Pagar | EXPENSE: fornecedores, folha, encargos, contratos |
| AR — Accounts Receivable | Contas a Receber | INCOME: clientes, convênios, doações, mensalidades |

A separação não é cosmética — reflete fluxos de trabalho, responsáveis e perguntas diferentes:
- **AP:** "O que eu devo e quando preciso pagar?" → tesoureiro/financeiro
- **AR:** "O que me devem e o que está em atraso?" → faturamento/cobrança

### Por que a tela unificada do SisgFin é defensável

Sistemas voltados a micro/pequenas organizações como **Nibo** e **Conta Simples** adotam o modelo unificado com filtros, e funcionam bem quando:

- Uma única pessoa gerencia tudo (comum em ONGs e associações pequenas)
- O volume não justifica telas separadas
- A divisão EXPENSE/INCOME é suficiente via filtro

O SisgFin se enquadra nesse perfil: a Flor da Vida tem um operador financeiro único gerenciando folha + fornecedores + receitas de convênio.

### Onde a implementação atual falha mesmo sendo unificada

O problema não é ter uma tela só. É que a **tela não foi desenhada para a unificação**:

1. **Filtro padrão `ActionRequired`** mistura "fornecedor aguardando pagamento" com "convênio aguardando recebimento" sem distinção visual na lista
2. **Sem painel de resumo AP × AR** — o usuário não sabe o saldo líquido (a pagar vs. a receber) ao abrir a tela
3. **Filtros nomeados como "Despesas" e "Receitas"** em vez de "A Pagar" e "A Receber" — o usuário de negócio pensa no fluxo, não no tipo contábil
4. **Sem ícone/cor forte de entrada vs. saída na linha da lista** — mistura visualmente os dois sentidos

---

## Parte 3 — Arquitetura proposta para separação AP / AR

### Estrutura de navegação

```
Sidebar
├── Dashboard
├── Contas a Pagar          ← novo (substitui parte do Movimentações)
├── Contas a Receber        ← novo (substitui parte do Movimentações)
├── Transferências          ← extraído do unificado (ou dentro de Contas Bancárias)
├── Conciliação OFX         ← já existe separado ✓
└── [Movimentações pode ser mantido como "Visão Geral" somente leitura]
```

### Layout: Contas a Pagar

```
┌──────────────────────────────────────────────────────────────────┐
│  Contas a Pagar                              [+ Nova Despesa]    │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  VENCIDO     │  │  A VENCER    │  │  PAGO (mês)  │          │
│  │  R$ 8.400    │  │  R$ 32.100   │  │  R$ 21.500   │          │
│  │  3 títulos   │  │  11 títulos  │  │  18 títulos  │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                  │
│  [Vencidos] [Hoje] [Próx. 7 dias] [Próx. 30 dias] [Todos]      │
│  [Folha] [Fornecedores] [Encargos] [Contratos]                  │
│                                                                  │
│  VENCIDO   15/07   Folha João Silva     Funcionário  R$ 3.200   │
│  VENCIDO   18/07   NF XPTO Ltda        Fornecedor   R$ 1.800   │
│  PENDENTE  05/08   Folha Maria Costa   Funcionário  R$ 2.900   │
│  PENDENTE  10/08   Aluguel agosto      —            R$ 4.500   │
│  PARCIAL   01/08   Contrato Limpeza    Fornecedor   R$ 1.200   │
└──────────────────────────────────────────────────────────────────┘
```

**Exclusivo de AP:**
- Campo contraparte aceita Fornecedor **ou Funcionário** (corrige Problema 1)
- Filtro por origem: Folha, Fornecedor, Encargo, Contrato
- Aprovação de pagamento (quitar, parcial)
- Integração com folha importada

### Layout: Contas a Receber

```
┌──────────────────────────────────────────────────────────────────┐
│  Contas a Receber                           [+ Nova Receita]    │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  VENCIDO     │  │  A RECEBER   │  │  RECEBIDO    │          │
│  │  R$ 2.000    │  │  R$ 48.000   │  │  R$ 15.000   │          │
│  │  1 título    │  │  5 títulos   │  │  6 títulos   │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                  │
│  [Vencidos] [Hoje] [Próx. 7 dias] [Próx. 30 dias] [Todos]      │
│  [Convênios] [Doações] [Mensalidades] [Outros]                  │
│                                                                  │
│  VENCIDO   10/07   Cota associado Jul  Cliente      R$ 2.000   │
│  PENDENTE  15/08   Convênio Municipal  —            R$28.000   │
│  PENDENTE  20/08   Doação recorrente   Cliente      R$ 5.000   │
│  PAGO      05/07   Repasse projeto X   —            R$15.000   │
└──────────────────────────────────────────────────────────────────┘
```

**Exclusivo de AR:**
- Campo contraparte: apenas Clientes/Pagantes
- Filtro por origem: Convênio, Doação, Mensalidade, Projeto
- Futuramente: geração de boleto, envio de lembrete de cobrança

### O que é compartilhado (não há duplicação de código)

```
Core compartilhado (sem mudança)
├── Transaction.kt                  — mesmo modelo
├── TransactionService.kt           — mesmo serviço
├── TransactionRepository.kt        — mesmo repositório
├── TransactionStateMachine.kt      — mesma máquina de estados
├── TransactionValidator.kt         — mesmo validador
├── TransactionDetailsPanel.kt      — mesmo painel, parametrizado
└── RecordPaymentDialog             — mesmo dialog de quitação

Específico AP
├── AccountsPayableScreen.kt
├── AccountsPayableViewModel.kt     — filtro fixo: EXPENSE
└── Filtros de origem (folha, fornecedor...)

Específico AR
├── AccountsReceivableScreen.kt
├── AccountsReceivableViewModel.kt  — filtro fixo: INCOME
└── Filtros de origem (convênio, doação...)
```

A separação ocorre **somente na camada de apresentação**. Modelo, serviço e repositório não mudam. Os dois ViewModels são instâncias do mesmo `TransactionsViewModel` com filtro de tipo pré-aplicado (ou subclasses mínimas).

### Comportamento do `TransactionDetailsPanel` por contexto

```
AP abre o painel →
  type = EXPENSE (fixo, não editável)
  label contraparte: "FORNECEDOR / FUNCIONÁRIO"
  lista contraparte: fornecedores ativos + funcionários ativos
  campo employeeId tornase selecionável

AR abre o painel →
  type = INCOME (fixo, não editável)
  label contraparte: "CLIENTE / PAGANTE"
  lista contraparte: apenas clientes ativos
```

O tipo da transação **não seria editável** dentro do painel quando aberto de um módulo específico. Uma despesa não vira receita — é cancelada e uma receita é criada separadamente.

### O que muda no banco de dados

**Nada.** A tabela `financial_transactions` continua idêntica. A separação é 100% na apresentação. O campo `type` já está gravado em cada registro — é apenas um filtro diferente por tela.

O único ajuste de modelo necessário é para resolver o **Problema 1** (contraparte funcionário), que é independente da separação de telas.

### Estimativa de trabalho para separação

| Componente | Esforço estimado |
|---|---|
| `AccountsPayableScreen.kt` | ~1 dia (~80% reaproveitado de `TransactionsScreen`) |
| `AccountsReceivableScreen.kt` | ~4h (~80% reaproveitado) |
| `TransactionDetailsPanel` — parametrizar `lockedType` e contraparte | ~4h |
| Sidebar / navegação — 2 novos itens | ~1h |
| `AccountsPayableViewModel` / `AccountsReceivableViewModel` | ~2h |
| Ajuste de testes | ~2h |
| **Total estimado** | **~2-3 dias** |

---

## Parte 4 — Priorização e roteiro sugerido

### Ordem recomendada de correção

| Prioridade | Problema | Justificativa |
|---|---|---|
| 🔴 1 | Colisão `project_id` / `financial_project_id` (Prob. 3) | Risco de dado lido errado em qualquer SQL externo. Migration simples. |
| 🔴 2 | `sumRealizedByProject` com `paidAmount` nulo (Prob. 7) | Relatório de projetos está mostrando valores incorretos hoje. |
| 🔴 3 | Contraparte funcionário invisível (Prob. 1) | Lançamentos de folha sem identificação do funcionário. |
| 🟡 4 | SCHEDULED nunca vai para OVERDUE (Prob. 4) | Títulos agendados vencidos ficam silenciados. |
| 🟡 5 | Cancelados invisíveis / sem trilha na UI (Prob. 9) | Compliance para terceiro setor. |
| 🟡 6 | Estorno não marca o original (Prob. 6) | Relatórios inflam soma de PAID. |
| 🟡 7 | `syncOverdueStatuses` no `listAll()` (Prob. 10) | Baixo impacto hoje, bomba em produção com volume. |
| 🟢 8 | `parentTransactionId` com 4 significados (Prob. 5) | Refatoração estrutural — alta complexidade, baixo risco imediato. |
| 🟢 9 | Pagamento parcial não acumulativo (Prob. 2) | Mudança de modelo significativa — nova tabela. |
| 🟢 10 | DRAFT e ADJUSTMENT órfãos (Prob. 8) | Limpeza / decisão de negócio. |
| 🔵 11 | Separação AP / AR (Parte 2-3) | Decisão de UX — pode ser feita a qualquer momento sem impacto no modelo. |

### Onde paramos nesta sessão

- ✅ Bugs de `EmployeeService.save()` corrigidos (duplicação de lançamentos ao editar funcionário)
- ✅ `generateForEmployee` corrigido para processar somente o funcionário alvo (não todos)
- ✅ Campo "PROJETO (opcional)" com placeholder adequado no `TransactionDetailsPanel`
- ✅ Diagnóstico arquitetural completo dos 10 problemas
- ✅ Análise e proposta de separação AP / AR
- ⏳ **Nenhuma correção dos 10 problemas foi implementada** — diagnóstico apenas, aguardando decisão de prioridade

---

## Próximos passos pendentes de decisão

1. **Separar AP / AR ou manter unificado com melhorias visuais?** (decisão de produto)
2. **Corrigir `project_id` → `cost_center_id` no banco?** (requer migration + deploy coordenado)
3. **Implementar `transaction_payments` para pagamento acumulativo?** (mudança de modelo — requer migration e reescrita de `recordPayment`)
4. **Definir caso de uso real de DRAFT e ADJUSTMENT** ou removê-los
5. **Implementar tela de cancelados/auditoria** para compliance

---

*Documento gerado para continuidade da análise arquitetural do SisgFin.*
*Retomar a partir da seção "Próximos passos pendentes de decisão".*
