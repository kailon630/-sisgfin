# SisgFin — Reorganização: Operação × Consulta

> Substitui a tela única "Movimentações Financeiras" por superfícies separadas por **pergunta do usuário**.
> Base: R5 (arquitetura de informação atual), R1 (contraparte), R2 (remessa), R6 (classificação).
>
> **Pré-requisito bloqueante:** F0 (`TransactionQuery`). Nada de UI antes dele.

---

## 0. Princípio de organização

| Superfície | Pergunta | Eixo de data | Status | Ação principal |
|---|---|---|---|---|
| **Contas a Pagar** *(nova)* | o que preciso pagar | `dueDate` | PENDING, OVERDUE, PARTIAL | liquidar |
| **Contas a Receber** *(existe)* | quem me deve | `dueDate` | PENDING, OVERDUE, PARTIAL | baixar recebimento |
| **Lançamentos** *(ex-Movimentações)* | onde está aquele lançamento | selecionável | todos | localizar, conferir, exportar |
| **Extrato** *(existe)* | o que entrou e saiu da conta | `paymentDate` | PAID | conferir contra banco |
| **Painel de Saldos** *(existe)* | quanto eu tenho | — | — | — |
| **Fluxo de Caixa** *(existe)* | vou ter caixa | `dueDate` projetado | previsto | simular |

**Regra:** "consolidação" não é destino de navegação. É faixa de totais dentro de cada superfície — tiles no topo, barra de somas no rodapé.

**Diferença Extrato × Lançamentos** (para não virarem a mesma tela):
- **Extrato:** sempre de **uma conta**, ordenado por `paymentDate`, com **saldo acumulado linha a linha**. Só liquidado.
- **Lançamentos:** transversal a contas, ordenação e eixo configuráveis, **sem** saldo acumulado. Todos os status.

---

## F0 — `TransactionQuery` (pré-requisito)

**Problema:** `TransactionListFilter` é sealed class de presets fixos. Critérios não combinam; cada tela nova exige subtipo novo.

### Criar

```kotlin
enum class DateAxis { ISSUE, DUE, PAYMENT }

data class TransactionQuery(
    val types: Set<TransactionType> = emptySet(),      // vazio = todos
    val statuses: Set<TransactionStatus> = emptySet(), // vazio = todos
    val dateAxis: DateAxis = DateAxis.DUE,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val accountId: Int? = null,
    val supplierId: Int? = null,
    val employeeId: Int? = null,
    val costCenterId: Int? = null,
    val categoryId: Int? = null,
    val projectId: Int? = null,
    val contractId: Int? = null,
    val search: String? = null,
    val onlyActive: Boolean = true,
) {
    companion object {
        fun aPagar()   = TransactionQuery(
            types = setOf(TransactionType.EXPENSE),
            statuses = setOf(PENDING, OVERDUE, PARTIAL))
        fun aReceber() = TransactionQuery(
            types = setOf(TransactionType.INCOME),
            statuses = setOf(PENDING, OVERDUE, PARTIAL))
        fun extrato(accountId: Int, from: LocalDate, to: LocalDate) = TransactionQuery(
            statuses = setOf(PAID), dateAxis = DateAxis.PAYMENT,
            accountId = accountId, from = from, to = to)
    }
}
```

### Repositório

Um único método `find(query: TransactionQuery): List<Transaction>` montando o `WHERE` incrementalmente via Exposed DSL. Todos os `findX` atuais reescritos como chamadas a ele.

**Manter `TransactionListFilter` durante a transição** (expand/contract): converter cada variante em `TransactionQuery` e marcar `@Deprecated`. Remover só quando nenhuma tela referenciar.

### Aceite F0

- Suite atual (131+ testes) verde sem alteração de assertivas.
- Teste novo: `TransactionQuery.aPagar()` retorna exatamente o mesmo conjunto que `filterActionRequired` filtrado por EXPENSE.
- Teste novo: eixo `PAYMENT` com período retorna o mesmo que `findStatementEntries`.

---

## F1 — Componentes compartilhados

As três telas de lista devem ser configurações sobre **um** componente. Extrair de `TransactionsScreen.kt`:

| Componente | Responsabilidade | Parâmetros de configuração |
|---|---|---|
| `TransactionListView` | tabela, grupos, seleção, menu de contexto, atalhos | `query`, `grouping`, `selectable`, `columns`, `onRowAction` |
| `SummaryTileRow` | faixa de tiles clicáveis com **valor** | `List<SummaryTile(label, amount, count, queryOverlay, tone)>` |
| `TotalsFooter` | total exibido + total selecionado | `displayed: Money`, `selected: Money?`, `count` |
| `TransactionFilterBar` | chips + busca (já existe) | `query`, `availableChips` |

**Regra:** nenhuma das três telas pode ter cópia própria da renderização de linha. Divergência visual entre A Pagar e A Receber é bug.

---

## F2 — Tela **Contas a Pagar** (nova)

### Layout

```
┌──────────────────────────────────────────────────────────────┐
│ Contas a Pagar                        [+ Despesa] [↻]        │
├──────────────────────────────────────────────────────────────┤
│ ┌──────────┐┌──────────┐┌──────────┐┌──────────┐             │
│ │ VENCIDO  ││   HOJE   ││  SEMANA  ││ PRÓXIMAS │  ← tiles    │
│ │ R$ 42.310││ R$ 8.900 ││ R$ 31.200││ R$ 76.400│    clicáveis│
│ │  12 tít. ││  3 tít.  ││  9 tít.  ││  21 tít. │             │
│ └──────────┘└──────────┘└──────────┘└──────────┘             │
├──────────────────────────────────────────────────────────────┤
│ [chips: Todos · Fornecedor · Folha]        [busca  Ctrl+F]   │
├──────────────────────────────────────────────────────────────┤
│ ☐ │ BENEFICIÁRIO │ DESCRIÇÃO │ VENC. │ CC/CAT │ VALOR │ ST   │
│ ── Vencidos ────────────────────────────────────────────────  │
│ ☐ │ ...                                                       │
│ ── Hoje ────────────────────────────────────────────────────  │
├──────────────────────────────────────────────────────────────┤
│ 4 selecionados · R$ 12.400    [Quitar selecionados] [Remessa] │
│ Total exibido: R$ 158.810                                    │
└──────────────────────────────────────────────────────────────┘
```

### Especificação

- **Query base:** `TransactionQuery.aPagar()`. Tiles aplicam recorte de `dueDate` sobre ela.
- **Tiles:** valor em destaque, contagem em subtítulo. `VENCIDO` em `WsDanger`, `HOJE` em `WsWarning`. Clique alterna filtro (segundo clique limpa).
- **Grupos temporais:** reaproveitar `groupByTimeSection` (`TransactionsScreen.kt:52–72`) sem alteração de critério.
- **Coluna BENEFICIÁRIO:** resolve `supplierId` **ou** `employeeId` (ver F5). Hoje o nome só existe dentro da descrição.
- **Coluna CC/CAT:** exibir código do centro de custo e da categoria. Célula vazia deve ser visualmente marcada (ver F6) — é o gancho para o problema de classificação do R6/R7.
- **Seleção múltipla:** checkbox por linha + "selecionar todos do grupo".
- **Permissão:** botões de liquidação exigem `Permission.ConfirmPayment` (RN-12). Sem permissão, checkboxes ficam ocultos, não desabilitados.

### Ordenação padrão
`dueDate` ascendente dentro de cada grupo; desempate por valor descendente (paga-se primeiro o que pesa mais).

---

## F3 — **Contas a Receber** (alinhar)

A tela existe. Refazer sobre os mesmos componentes de F1 para que A Pagar e A Receber sejam espelhos.

- Trocar os 4 tiles de aging atuais (A vencer / 1-30 / 31-60 / 61+) por tiles com **valor**, mantendo as faixas.
- Adicionar seleção múltipla e baixa em lote.
- Manter a chamada a `syncOverdueStatuses()` antes de carregar (RN-15).

---

## F4 — **Lançamentos** (ex-Movimentações)

Reposicionada: deixa de ser tela de trabalho e vira tela de busca e conferência.

### Muda

| Item | Antes | Depois |
|---|---|---|
| Nome | Movimentações Financeiras | Lançamentos |
| Filtro default | `ActionRequired` (chip "A pagar", **mistura receita e despesa**) | `Todos`, período = mês corrente |
| Grupos temporais | sim | **não** (agrupamento é operacional) |
| Eixo de data | `dueDate` fixo | **seletor** Emissão / Vencimento / Pagamento |
| Totais | nenhum | rodapé: entradas, saídas, saldo do recorte |
| Criação | + Despesa / + Receita / Transferência | mantém, e passa a ser o único ponto de criação de `TRANSFER` e `ADJUSTMENT` |
| Exportação | não | XLSX/PDF do recorte atual |

### O seletor de eixo de data

Três chips no topo do filtro de período: `Emissão · Vencimento · Pagamento`. É o item de maior valor pedagógico da tela — torna competência × caixa explícito para o operador. Rótulo de ajuda ao lado: *"Pagamento mostra apenas lançamentos liquidados."*

Quando `PAYMENT` for selecionado, forçar `statuses = {PAID}` e sinalizar isso na UI.

### Chips de status
`Todos · Em aberto · Vencidos · Pagos · Cancelados` — sem os chips de tipo (que viram filtro no painel lateral de filtros avançados).

---

## F5 — Beneficiário unificado (destrava F2)

**Origem:** R1 achados 1 e 2 — o painel não resolve `employeeId` para nome; 4/4 lançamentos da base são só-funcionário.

### Camada de leitura

Criar `CounterpartyResolver` (ou método em `TransactionService`) que, dado um `Transaction`, devolve:

```kotlin
data class CounterpartyRef(
    val id: Int,
    val kind: CounterpartyKind,  // SUPPLIER | EMPLOYEE
    val name: String,
    val document: String?,
    val isActive: Boolean,
)
```

Regra: `employeeId` tem precedência sobre `supplierId` quando ambos existem. Nenhum dos dois → `null`, célula exibe `—`.

**Carregar em lote**, não por linha. Um `Map<Int, String>` de funcionários e outro de fornecedores, montados junto com `loadReferenceData()`.

### Camada de escrita

Campo único **BENEFICIÁRIO** no painel de detalhes, substituindo o atual `CLIENTE / FORNECEDOR`:
- busca digitando, com resultados de ambos os cadastros
- badge na opção indicando origem (`Fornecedor` / `Funcionário`)
- grava em `supplierId` **ou** `employeeId`, zerando o outro
- inativos não aparecem na busca, mas são exibidos (com marca) se já vinculados

**Atenção:** hoje `TransactionRepository.update` deliberadamente **não** atualiza `employeeId` (R1 §2.2). Ao permitir edição, esse comportamento precisa mudar — e a mudança deve ser explícita, com teste, porque hoje é o que protege os lançamentos de folha.

### Pendência de negócio (registrar, não resolver agora)
R7.3: não há validação de funcionário inativo nem data de desligamento. Com o campo unificado, o operador poderá vincular funcionário desligado. Adicionar validação análoga à RN-02 para `employeeId`.

---

## F6 — Sinalização de classificação ausente

**Origem:** R6 achado 4 e R7 achado 3 — `PayrollEngine` cria lançamentos sem centro de custo nem categoria, e o Balancete agrega por essas duas dimensões.

Não corrige a causa (isso é backend), mas torna o problema **visível** em vez de silencioso:

- Coluna `CC/CAT` em Contas a Pagar exibe `—` em `WsWarning` quando ausente.
- Tile adicional opcional em Lançamentos: `SEM CLASSIFICAÇÃO — R$ X · N títulos`, clicável.
- Ação em lote: selecionar vários → **Classificar selecionados** (define CC e categoria de uma vez).

Isso dá ao operador o caminho de correção em massa da folha gerada pelo Engine, que hoje só existiria refazendo a importação XLSX.

---

## F7 — Painel de detalhes: consulta × edição × liquidação

**Origem:** R5 achado 5 — painel de ~400dp, ~15 campos, seções não colapsáveis.

### Três modos, não um

| Modo | Quando | Largura | Conteúdo |
|---|---|---|---|
| **Consulta** | clique na linha | 400dp | resumo, ações rápidas, timeline. Somente leitura. |
| **Edição** | botão Editar | 640dp, 2 colunas | formulário; `Vínculos` e `Documento` **colapsados** por padrão |
| **Liquidação** | botão Quitar | dialog centrado, pequeno | data, conta, valor, juros, multa, desconto |

O modo Liquidação é a mudança de maior impacto no uso diário: é a ação mais frequente e hoje está dentro do formulário de 15 campos.

### Regras
- Status terminal (`PAID`, `CANCELED`) nunca abre modo Edição de campos financeiros — alinhado à guarda C2 já implementada no serviço.
- Campos não-financeiros permanecem editáveis em terminal, gerando evento de timeline (decisão do C2).
- `Esc` fecha o modo atual e volta ao anterior (Liquidação → Consulta → fechado).

---

## Faseamento (produção — sem big bang)

| Fase | Entrega | Risco | Valor percebido |
|---|---|---|---|
| **F0** | `TransactionQuery` + repositório unificado | médio (toca leitura de tudo) | zero visível |
| **F1** | Componentes extraídos | baixo | zero visível |
| **F-quick** | **Corrigir rótulo do chip "A pagar"** (hoje mistura INCOME e EXPENSE) + rodapé de totais na tela atual | muito baixo | **alto** |
| **F5** | Beneficiário unificado | baixo | alto (resolve o bug relatado) |
| **F2** | Contas a Pagar | baixo (tela nova, não quebra a existente) | alto |
| **F7** | Dialog de liquidação | baixo | alto |
| **F3** | Contas a Receber alinhada | baixo | médio |
| **F6** | Sinalização de classificação | baixo | médio |
| **F4** | Movimentações → Lançamentos | médio (muda hábito do operador) | médio |
| **F8** | Baixa em lote + remessa a fornecedor | alto (toca liquidação) | alto |

**F-quick antes de tudo.** O chip "A pagar" que inclui receitas é erro factual de rótulo e custa uma linha. Não espere F0 para isso.

**F8 por último e com espec própria.** Baixa em lote toca o caminho de liquidação, que é onde mora o dinheiro. Depende de decidir antes se a baixa vira entidade própria (`transaction_payments`) — sem isso, liquidação parcial em lote não é representável, porque `paidAmount` é campo único no título.

---

## Menu Financeiro proposto

```
Financeiro
├── Dashboard
├── ── Operação ──
├── Contas a Pagar          ← nova
├── Contas a Receber
├── ── Consulta ──
├── Lançamentos             ← ex-Movimentações
├── Extrato
├── Painel de Saldos
├── Fluxo de Caixa
├── ── Estrutura ──
├── Orçamento
├── Contratos
└── Recorrências
```

---

## Critérios de aceite globais

1. Nenhuma das três telas de lista tem código próprio de renderização de linha.
2. Todo filtro de qualquer tela é expressável como `TransactionQuery`.
3. Toda tela de lista exibe soma dos valores exibidos.
4. Nenhuma tela exibe um lançamento sem beneficiário identificável quando `supplierId` ou `employeeId` existe.
5. O eixo de data usado está sempre visível na tela (rótulo de coluna ou seletor) — o operador nunca precisa adivinhar se está vendo vencimento ou pagamento.
6. Suite de testes verde a cada fase, sem assertiva desativada.
