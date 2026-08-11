# R5 — Arquitetura de informação da tela Movimentações

## 1. Resumo executivo

Movimentações é a tela operacional mista (pagar e receber). Abre no filtro **“A pagar”** (`ActionRequired`: PENDING+OVERDUE+PARTIAL), com grupos temporais por **`dueDate`**. Não há barra de totais nem tela dedicada de Contas a Pagar (existe Contas a Receber). Quitar/Estornar exigem `Permission.ConfirmPayment` (ADMIN). O painel direito de detalhes é o mesmo para despesa/receita; transferência usa dialog separado; PAID/REVERSAL terminais não editam formulário.

## 2. Estado atual

### 2.1 Layout de cima para baixo (`TransactionsScreen.kt`)

1. **Cabeçalho:** título “Movimentações Financeiras” + subtítulo “Contas a pagar e receber — ciclo operacional”.
2. **Toolbar:** Transferência / Despesa / Receita / Refresh.
3. **Barra de filtros + busca** (`TransactionFilterBar`): chips + campo de busca (Ctrl+F).
4. **Tabela** (borda): cabeçalho fixo TIPO | DESCRIÇÃO | VENCIMENTO | VALOR | STATUS.
5. **Corpo:**
   - Se filtro `ActionRequired` → lista **agrupada** (`groupByTimeSection`).
   - Demais filtros → `LazyColumn` plana.
6. **Overlays:** quick edit popup, dialog de transferência, menu de contexto.

**Fontes de dados:** `TransactionsViewModel` → `TransactionService` / repositório (`filterActionRequired`, filtros por status/tipo/período); referências (contas, fornecedores, CC, categorias, projetos) via `loadReferenceData()` no `LaunchedEffect`.

**Totalizadores / consolidação:** **não há** na tela (nenhuma soma de valores no layout).

### 2.2 Grupos temporais

Função `groupByTimeSection` (`TransactionsScreen.kt:52–72`). Eixo: **`dueDate`** (não `paymentDate` nem `issueDate`).

| Grupo | Critério |
|-------|----------|
| Vencidos | `status == OVERDUE` |
| Hoje | PENDING/PARTIAL/SCHEDULED e `dueDate == today` |
| Amanhã | idem + `dueDate == tomorrow` |
| Esta semana | `dueDate` após amanhã até domingo da semana corrente |
| Próximas | `dueDate` após o fim da semana |

Só ativos quando o filtro é `ActionRequired`.

### 2.3 Ações disponíveis

| Ação | Onde | Exige ADMIN (`ConfirmPayment`)? |
|------|------|----------------------------------|
| Transferência | toolbar | não |
| Despesa / Receita | toolbar | não |
| Refresh | toolbar | não |
| Chips de filtro + busca | barra | não |
| Abrir detalhes (clique / Enter) | lista | não |
| Quick edit (duplo clique / menu Editar) | lista / menu | não (status não terminal) |
| Duplicar (Ctrl+D / menu) | lista | não |
| Cancelar (Delete / menu) | lista | não (status permite) |
| Detalhes (menu) | menu | não |
| Quitar / MarkPaid (menu / painel) | menu / painel | **sim** |
| Estornar (painel) | painel | **sim** |
| Comprovante PDF | painel se PAID | não (só status) |
| Esc fecha painel/dialog | atalho | não |

`getAvailableActions` condiciona MarkPaid e Reverse a `service.canConfirmPayment()` (`TransactionsViewModel.kt:321–332`). `SessionManager`: `ConfirmPayment` não é `BasicOperation` — só ADMIN (RN-12).

### 2.4 `TransactionListFilter` e default

```5:15:src/main/kotlin/br/com/sisgfin/financial/transactions/TransactionListFilter.kt
sealed class TransactionListFilter {
    data object All
    data object ActionRequired   // PENDING + OVERDUE + PARTIAL
    data class ByStatus(...)
    data class ByType(...)
    data object DueToday / Overdue / Paid
    data class DuePeriod(from, to)
}
```

Default ao abrir: `ActionRequired` (`TransactionsViewModel.kt:83,108`). Chip UI: **“A pagar”**.

Chips na barra (`TransactionsScreen.kt:376–418`): A pagar | Todas | Vence hoje | Vencidas | Pagas | Despesas | Receitas | 30 dias.

### 2.5 Comparativo com outras telas

| Tela | Data eixo principal | Statuses / escopo |
|------|---------------------|-------------------|
| **Movimentações** | `dueDate` (lista, grupos, filtros de período) | Default: PENDING+OVERDUE+PARTIAL (ambos tipos); outros filtros por status/tipo |
| **Extrato** (`findStatementEntries`) | `paymentDate` (filtro e ordenação) | só **PAID** |
| **Painel de Saldos** | saldo RN-04 (não é lista por data); cards usam movimentos PAID (+PARTIAL `paidAmount`) | mistos: saldo liquidado + indicadores PENDING/OVERDUE |
| **Contas a Receber** | `dueDate` | só **INCOME** com PENDING, OVERDUE, PARTIAL (`findReceivables`) |

### 2.6 Contas a Pagar dedicada?

**Não.** Menu Financeiro tem “Contas a Receber”, não “Contas a Pagar” (`Main.kt:126–133`).

Caminho do operador para “o que pagar esta semana”:

1. Abrir **Movimentações** (já em “A pagar”).
2. Olhar grupo **“Esta semana”** (e Vencidos / Hoje / Amanhã).
3. Alternativas: chip Despesas / Vence hoje / 30 dias.

### 2.7 `TransactionDetailsPanel`

| Aspecto | Valor |
|---------|--------|
| Largura | painel direito: inicial `min(400.dp, 60% da área)`; redimensionável 250.dp–85% (`MainLayout.kt:152–154, 271–285`) |
| Seções | Resumo, Ações rápidas, Dados gerais, Vínculos, Documento, Recorrência (só novo), Linha do tempo — `DetailSection` **não colapsáveis** |
| Campos editáveis | ~15 (descrição, valor, parcelas, emissão, vencimento, tipo, conta, contrato*, cliente/fornecedor, CC, projeto, categoria, tipo/nº doc, observações, recorrência*) |
| Variações | Mesmo painel despesa/receita (rótulo CLIENTE vs FORNECEDOR). Transferência: `TransferDialog` separado. Status terminal (PAID/CANCELED): sem formulário editável. REVERSAL: tipicamente terminal/PAID |

## 3. Resultados das queries

Este relatório é estrutural (UI/código). Não há query SQL obrigatória no enunciado R5.

Contexto da base (R1): 4 despesas PENDING de folha — todas cairiam no filtro “A pagar” e nos grupos por `dueDate`.

## 4. Achados

| # | Severidade | Achado | Arquivo:linha | Impacto |
|---|-----------|--------|---------------|---------|
| 1 | MÉDIO | Chip “A pagar” mistura receitas e despesas em aberto | `TransactionListFilter.ActionRequired` + `filterActionRequired` | Operador de AP vê também AR na mesma lista |
| 2 | MÉDIO | Sem tela/filtro salvo dedicado a Contas a Pagar | `Main.kt:126–133` | Fluxo semanal depende de grupos temporais da Movimentações |
| 3 | BAIXO | Sem totalizadores na listagem | `TransactionsScreen.kt` | Consolidação visual inexistente |
| 4 | MÉDIO | Grupos usam `dueDate`; Extrato usa `paymentDate` | `TransactionsScreen.kt:59` vs `findStatementEntries` | Mesmo lançamento “aparece” em eixos de data diferentes entre telas |
| 5 | BAIXO | Seções do painel não colapsam; painel estreito (~400dp) com muitos campos | `MainLayout.kt:154`; `TransactionDetailsPanel` | Densidade alta / scroll |

## 5. Incertezas

- Comportamento de `markAsPaidFull` no menu vs dialog de pagamento parcial no painel não foi detalhado operacionalmente.
- Não foi medido tempo/scroll real com volume grande de lançamentos.
