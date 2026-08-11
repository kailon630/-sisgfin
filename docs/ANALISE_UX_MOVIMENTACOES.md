# Análise de UX — Tela de Movimentações Financeiras

**SisgFin v1.0.8 — Gerado em 2026-08-06**

---

## Sumário

- [Estrutura Visual](#estrutura-visual)
- [Fluxos do Usuário](#fluxos-do-usuário)
- [Inconsistências](#inconsistências)
- [Modelo Mental](#modelo-mental)
- [Painel Lateral](#painel-lateral)
- [Melhorias](#melhorias)

---

## Estrutura Visual

### Hierarquia da Tela

A tela é composta por um `Column` principal que ocupa toda a área disponível, organizado em três zonas verticais:

```
┌─────────────────────────────────────────────────────┐
│  CABEÇALHO: Título + subtítulo + botões de ação     │
├─────────────────────────────────────────────────────┤
│  BARRA DE FILTROS: Busca + chips de filtro          │
├─────────────────────────────────────────────────────┤
│  GRADE DE LANÇAMENTOS                               │
│  ┌─────────────────────────────────────────────┐    │
│  │ Cabeçalho fixo: TIPO | DESCRIÇÃO | VENC |   │    │
│  │                 VALOR | STATUS              │    │
│  ├─────────────────────────────────────────────┤    │
│  │ Corpo (LazyColumn):                         │    │
│  │  [Filtro "A pagar"] → agrupado por urgência │    │
│  │  [Demais filtros]   → lista plana           │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

Quando o usuário clica em uma linha, a aplicação abre um **painel lateral direito** (renderizado via `onShowRightPanel`) que empurra ou sobrepõe a lista principal.

---

### Cabeçalho (Toolbar)

Contém dois blocos em linha justificada (`SpaceBetween`):

**Bloco esquerdo:**
- Título: "Movimentações Financeiras" (`headlineMedium`)
- Subtítulo: "Contas a pagar e receber — ciclo operacional" (`bodyMedium`, cor secundária)

**Bloco direito (botões, da esquerda para direita):**
| Botão | Variante | Ação |
|---|---|---|
| Transferência | SECONDARY | Abre `TransferDialog` |
| Despesa | DANGER | Abre painel lateral com form de despesa |
| Receita | PRIMARY | Abre painel lateral com form de receita |
| (ícone Refresh) | ICON | Recarrega a lista |

Observação: não existe botão "Novo lançamento" genérico — o tipo já é pré-determinado pelo botão escolhido.

---

### Barra de Filtros

**Campo de busca:**
- Largura fixa: 280dp
- Placeholder: "Buscar... (Ctrl+F)"
- Ícone de lupa à esquerda
- Borda muda para `WsAccent` quando focado
- Busca dispara em tempo real (sem debounce explícito visível no código)

**Chips de filtro (linha abaixo da busca):**

| Chip | Filtro aplicado | Comportamento da lista |
|---|---|---|
| A pagar *(padrão)* | PENDING + OVERDUE + PARTIAL | Agrupada por urgência temporal |
| Todas | Sem filtro | Lista plana |
| Vence hoje | DueToday | Lista plana |
| Vencidas | Overdue | Lista plana |
| Pagas | Paid | Lista plana |
| Despesas | ByType(EXPENSE) | Lista plana |
| Receitas | ByType(INCOME) | Lista plana |
| 30 dias | DuePeriod(hoje, +30d) | Lista plana |

São 8 chips fixos, sem possibilidade de combinação (ex.: "Despesas vencidas").

---

### Grade de Lançamentos

**Cabeçalho fixo:**
```
TIPO | DESCRIÇÃO (maior peso) | VENCIMENTO | VALOR (alinhado à direita) | STATUS (centralizado)
```

**Linha (`TransactionRow`):**
- Barra lateral esquerda (3dp): colorida por status
  - OVERDUE: vermelho (`WsDanger`)
  - PARTIAL: amarelo (`WsWarning`)
  - PAID: verde desbotado (`WsSuccess α0.5`)
  - Demais: transparente
- Fundo da linha: selecionada = `WsAccent α0.1`, hover = `WsElevated`, normal = transparente
- Colunas: TypeLabel colorido | Descrição (ellipsis) | Data de vencimento + valor pago (quando há) | Valor (INCOME=verde, EXPENSE=primário, outros=secundário) | StatusBadge

**Interações da linha:**
- Clique simples: abre painel lateral
- Duplo clique: abre `TransactionQuickPopup` (edição rápida)
- Clique longo / botão direito: abre menu de contexto

---

### Agrupamentos Temporais (filtro "A pagar")

Quando o filtro é `ActionRequired`, a lista é dividida em grupos com cabeçalhos sticky:

| Grupo | Cor | Critério |
|---|---|---|
| Vencidos (N) | `WsDanger` | status == OVERDUE |
| Hoje (N) | `WsWarning` | status ativo + dueDate == hoje |
| Amanhã (N) | `WsAccent` | status ativo + dueDate == amanhã |
| Esta semana (N) | `WsTextSecondary` | status ativo + dueDate entre amanhã e domingo |
| Próximas (N) | `WsTextSecondary` | status ativo + dueDate após domingo |

Cada cabeçalho de grupo tem: ponto colorido (7dp, CircleShape) + label com contagem. Grupos vazios não aparecem.

---

### Menu de Contexto

Ativado por clique longo na linha:

| Item | Condição de exibição |
|---|---|
| Abrir detalhes | Sempre |
| Editar | Sempre |
| Quitar | status ≠ PAID e ≠ CANCELED |
| Duplicar | Sempre |
| Cancelar | status ≠ CANCELED |

---

### Atalhos de Teclado

| Atalho | Ação |
|---|---|
| Ctrl+F | Foca o campo de busca |
| Ctrl+D | Duplica o lançamento selecionado |
| Enter | Abre painel lateral do selecionado (ou primeiro da lista) |
| Delete | Cancela o lançamento selecionado |
| Escape | Fecha diálogo / painel lateral |

---

## Fluxos do Usuário

### Como Cria um Lançamento

1. Clica em "Despesa" (botão DANGER) ou "Receita" (botão PRIMARY) no cabeçalho
2. Painel lateral abre à direita com formulário em branco; tipo já pré-selecionado
3. Preenche os campos (seções: Dados gerais → Vínculos → Documento → Recorrência)
4. Clica em "Salvar" no footer do painel
5. Painel fecha; linha aparece na lista (se o filtro ativo incluir o novo status)

**Pontos de fricção:** o painel inteiro é uma coluna scrollable longa. Para lançamentos com parcelamento ou recorrência, o usuário precisa rolar pela seção de Vínculos e Documento antes de chegar à seção de Recorrência, que fica no final.

---

### Como Edita um Lançamento

**Caminho 1 — painel completo:**
1. Clica na linha → painel lateral abre
2. Rola até as seções editáveis (abaixo do Resumo e das Ações rápidas)
3. Edita os campos
4. Clica em "Salvar" no footer

**Caminho 2 — edição rápida:**
1. Duplo clique na linha → `TransactionQuickPopup` abre
2. Edita apenas descrição e valor
3. Clica em "Salvar"

**Caminho 3 — via painel + botão editar:**
1. Clica na linha → painel abre
2. Clica no ícone de lápis (Edit) nas ações rápidas → abre `TransactionQuickPopup`

Resultado: **três caminhos de edição com capacidades diferentes**, sem hierarquia clara entre eles.

---

### Como Paga um Lançamento

**Via painel lateral:**
1. Clica na linha → painel lateral abre
2. Localiza a seção "Ações rápidas" (acima do formulário)
3. Clica em "Quitar"
4. Modal `PaymentRecordDialog` abre com: valor pago (pré-preenchido com total), juros (opcional), multa (opcional), data de pagamento (pré-preenchida com hoje)
5. Ajusta os valores se necessário
6. Clica em "Confirmar"

**Via context menu:**
1. Clique longo na linha → menu de contexto
2. Clica em "Quitar"
3. Chama `markAsPaidFull()` diretamente — **sem modal, sem possibilidade de informar juros/multa**

Resultado: o caminho pelo context menu faz quitação total sem encargos. O caminho pelo painel permite registrar juros e multa. **Mesma ação, resultados diferentes dependendo do caminho.**

---

### Como Parcela um Lançamento

1. Clica em "Despesa" ou "Receita"
2. No formulário do painel, localiza o campo "PARCELAS" (ao lado do campo VALOR)
3. Digita o número de parcelas (ex.: "3")
4. Preenche os demais campos normalmente
5. Salva — o sistema gera N lançamentos filhos

**Pontos de fricção:**
- O campo "PARCELAS" é um simples `WsTextField` sem hint sobre o efeito colateral (geração de múltiplos lançamentos)
- Não há preview de "serão criadas 3 parcelas de R$ X cada uma"
- Ao salvar, a seção de "Recorrência" desaparece silenciosamente quando "PARCELAS" é preenchido — o usuário não percebe a limitação

---

### Como Estorna um Lançamento

1. Clica no lançamento PAID → painel lateral abre
2. Localiza a seção "Ações rápidas"
3. Clica em "Estornar" (botão WARNING com ícone Undo)
4. Modal `ReversalDialog` abre, exibindo: descrição e valor originais + aviso de irreversibilidade + textarea de justificativa (mínimo 10 caracteres)
5. Preenche a justificativa
6. Clica em "Confirmar estorno"

**O estorno não está disponível no menu de contexto** — o usuário só chega até ele pelo painel lateral.

---

### Como Consolida (Reconcilia OFX)

Este fluxo não existe na `TransactionsScreen`. A reconciliação OFX acontece exclusivamente na `OfxImportScreen` e só no momento imediato após a importação. Não há caminho para reconciliar lançamentos posteriormente a partir da tela de movimentações.

---

### Como Pesquisa

1. Pressiona Ctrl+F ou clica no campo de busca (280dp, canto superior esquerdo da barra de filtros)
2. Digita o texto — a busca dispara em tempo real
3. A lista filtra pelos resultados

**Pontos de atenção:**
- Busca e filtros coexistem, mas sem indicação clara de que ambos estão ativos simultaneamente
- Ao limpar a busca (campo vazio), a lista não retorna automaticamente ao filtro anterior — depende do chip selecionado
- Não há indicação de "N resultados encontrados"

---

### Como Filtra

1. Clica em um dos 8 chips na barra de filtros
2. Lista atualiza imediatamente
3. O chip selecionado muda visualmente (estado `selected`)

**Limitações:**
- Filtros são mutuamente exclusivos — não é possível combinar "Despesas" + "Vencidas"
- Não existe filtro por período personalizado na barra (o chip "30 dias" fixa sempre hoje+30)
- Não existe filtro por conta, fornecedor ou centro de custo

---

## Inconsistências

### 1. Dois "Cancelar" com Semânticas Opostas no Mesmo Painel

Na seção "Ações rápidas" existe `WsButton("Cancelar", variant = DANGER)` — que cancela o **lançamento financeiro** (ação irreversível de domínio).

No footer do `BaseCrudPanel` existe, presumivelmente, um botão "Cancelar" que descarta as **alterações do formulário** sem salvar.

São duas ações com o mesmo label, mesma posição relativa (painel lateral), semânticas opostas. O usuário que quiser fechar o painel sem salvar corre o risco de cancelar o lançamento financeiro.

---

### 2. Quitação Via Context Menu Ignora Juros e Multa

O item "Quitar" no menu de contexto chama `markAsPaidFull(id)` — quitação total, sem modal, sem encargos.

O botão "Quitar" no painel lateral abre `PaymentRecordDialog` — com campos de valor pago, juros e multa.

Mesma palavra, dois comportamentos. Para uma organização sujeita a TCESP/AUDESP, registrar juros e multa não é opcional em muitos casos. O atalho do context menu cria dados incompletos silenciosamente.

---

### 3. Três Caminhos de Edição com Capacidades Diferentes

| Caminho | Como Ativar | O Que Edita |
|---|---|---|
| Formulário inline no painel | Clique simples na linha, depois rola | Todos os campos |
| QuickPopup via duplo clique | Duplo clique na linha | Apenas descrição e valor |
| QuickPopup via botão no painel | Clique no ícone de lápis nas ações rápidas | Apenas descrição e valor |

O usuário não tem como saber antecipadamente qual caminho oferece qual capacidade.

---

### 4. "Estornar" Ausente no Menu de Contexto

O menu de contexto (clique longo) oferece: Abrir detalhes, Editar, Quitar, Duplicar, Cancelar.

O painel lateral oferece adicionalmente: **Estornar** e **Comprovante**.

Uma ação crítica (estorno) está disponível apenas em um dos dois pontos de entrada. Usuário acostumado a usar o context menu nunca encontra o estorno sem explorar o painel.

---

### 5. A Seção de Recorrência Desaparece Silenciosamente

A seção "Recorrência" só aparece quando:
- O lançamento é novo (id == 0)
- O campo "PARCELAS" está em branco

Se o usuário preenche "PARCELAS", a seção de Recorrência some do painel sem aviso algum. Não há tooltip, mensagem ou indicação de "parcelamento e recorrência são mutuamente exclusivos". O comportamento é correto do ponto de vista do domínio, mas invisível para o usuário.

---

### 6. BudgetBalanceBanner Distante do Campo de Valor

O banner de orçamento (Dotado / Realizado / Disponível / barra de progresso) aparece na seção **"Vínculos"**, que é a segunda seção editável do painel.

O campo **VALOR** fica na primeira seção editável, **"Dados gerais"**, acima.

O usuário digita o valor, depois rola para baixo, seleciona CC e Categoria, e só então vê a relação entre o valor digitado e o orçamento disponível. O `BudgetOverrunWarning` (que aparece perto do campo de valor) atenua o problema, mas o banner completo com dotação e realizado fica geograficamente distante.

---

### 7. Campo "CONTA" Muda de Formato com a Quantidade de Contas

Com ≤ 4 contas cadastradas: o campo "CONTA" é exibido como chips de filtro (`WsFilterChip`).

Com > 4 contas: é exibido como dropdown (`WsSelectField`).

A interface muda de formato conforme os dados, não conforme a intenção do usuário. Um usuário com 4 contas que cadastra a 5ª encontra o campo "CONTA" diferente na próxima vez que abrir o formulário.

---

### 8. Filtro "A pagar" Oculta a Maioria dos Dados Sem Indicação de Volume

O filtro padrão "A pagar" mostra apenas lançamentos com ação pendente. Todos os lançamentos PAID, CANCELED, DRAFT e SCHEDULED estão ocultos.

Não há indicador de "N lançamentos ocultos" ou "X% dos lançamentos estão visíveis". Um usuário que procura um lançamento pago recente pode pensar que ele não existe antes de descobrir que precisa trocar o filtro.

---

### 9. Botão "Comprovante" ao Lado de "Estornar" Sem Separação Visual

Para lançamentos PAID, as ações rápidas exibem: `Estornar` (WARNING, amarelo) + `Comprovante` (SECONDARY).

Uma ação de alto impacto e irreversível (estorno) fica imediatamente ao lado de uma ação inofensiva (download de PDF). A proximidade física sugere equivalência de peso. Não há separador visual, espaço adicional ou agrupamento que distinga ações destrutivas de ações de consulta.

---

### 10. Campo "PARCELAS" Sem Comunicação de Consequência

O campo "PARCELAS" é um input de texto livre ao lado de "VALOR (R$)". Não há:
- Placeholder explicativo (ex.: "Número de parcelas")
- Preview do valor por parcela
- Confirmação antes de salvar ("Criar 3 parcelas de R$ 333,33?")
- Indicação de que lançamentos adicionais serão gerados

O campo tem o mesmo peso visual que "VALOR (R$)", mas o seu efeito é ordens de magnitude maior (criar N registros no banco vs. alterar um número).

---

### 11. Busca e Filtros de Chip sem Estado Combinado Visível

Quando o usuário busca "Fornecedor X" com o filtro "Despesas" ativo, ambos são aplicados simultaneamente. Mas não há indicação visual de que dois critérios estão ativos ao mesmo tempo — a busca e o chip selecionado são elementos visuais separados sem integração de estado.

---

## Modelo Mental

### O que a Tela Transmite

A tela de Movimentações transmite predominantemente a sensação de uma **central operacional de contas a pagar e receber** — similar à fila de trabalho (workqueue) de um módulo AP/AR de ERP.

Os elementos que reforçam essa percepção:
- **Filtro padrão "A pagar"** com agrupamento temporal por urgência (Vencidos → Hoje → Amanhã → Esta semana) — foco em "o que precisa de ação agora"
- **Ações diretas na linha** (context menu com Quitar/Cancelar) — operação sem necessidade de navegar para outra tela
- **Título e subtítulo** explícitos: "Contas a pagar e receber — ciclo operacional"

Mas o painel lateral rompe essa coerência ao introduzir:
- **Formulário de cadastro** (Dados gerais, Vínculos, Documento) — sensação de tela de cadastro
- **Linha do tempo** — sensação de histórico/extrato
- **Banner de rubrica orçamentária** — sensação de painel financeiro

O resultado é uma tela que começa como central operacional e, ao se aprofundar em um lançamento, vira simultaneamente ficha cadastral, painel analítico e workspace de operações. **A identidade se fragmenta conforme o usuário avança.**

Para uma organização de terceiro setor com prestação de contas pública, isso não é necessariamente errado — o usuário precisa de todas essas informações. Mas a experiência teria mais coerência se a central operacional ficasse claramente separada da edição de dados e da análise de rubrica.

---

## Painel Lateral

### Inventário Completo de Tudo Que Aparece

O painel é implementado em `TransactionDetailsPanel.kt` como um `BaseCrudPanel` scrollable. O conteúdo varia pelo estado do lançamento:

#### Sempre visível (qualquer lançamento)

1. **Linha de status + tipo** — `TransactionStatusBadge` + `TransactionTypeLabel` em linha justificada
2. **Seção "Resumo"** (read-only, pares label/valor):
   - Valor total
   - Valor pago *(se houver)*
   - Juros *(se houver)*
   - Multa *(se houver)*
   - Emissão
   - Vencimento
   - Pagamento *(se houver)*
   - Conta
   - Fornecedor *(se houver)*
   - Centro de Custo *(se houver)*
   - Projeto *(se houver)*
   - Categoria *(se houver)*
   - Documento *(se houver tipo de documento)*
   - Parcela N/N *(se for parcela)*
3. **Seção "Linha do tempo"** *(somente quando há eventos)* — lista de eventos com data/hora, tipo e mensagem

#### Apenas para lançamentos existentes (id != 0)

4. **Seção "Ações rápidas"** — linha horizontal de botões:
   - `Quitar` *(somente se status permite pagamento)* — abre `PaymentRecordDialog`
   - `Cancelar` (DANGER) *(somente se não for terminal)* — cancela o lançamento diretamente
   - ícone `Duplicar` *(sempre)*
   - ícone `Editar` *(se canEdit e onOpenQuickEdit != null)* — abre `TransactionQuickPopup`
   - `Estornar` (WARNING) *(somente se PAID e com permissão)* — abre `ReversalDialog`
   - `Comprovante` (SECONDARY) *(somente se PAID)* — exporta PDF

#### Apenas quando canEdit (não-terminal ou novo)

5. **Seção "Dados gerais"** (formulário editável):
   - Campo DESCRIÇÃO
   - Linha: campo VALOR (R$) + campo PARCELAS
   - `BudgetOverrunWarning` *(se rubrica selecionada e valor excede)*
   - Linha: campo EMISSÃO + campo VENCIMENTO
   - Label "TIPO" + `WsTypeSelector`
   - Campo CONTA (chips se ≤4, dropdown se >4; aviso vermelho se nenhuma conta existe)

6. **Seção "Vínculos"** (formulário editável):
   - Dropdown CONTRATO *(somente para novos lançamentos, quando existem contratos ativos do mesmo tipo)*
   - Alerta amarelo de extrapolação de contrato *(se aplicável)*
   - Dropdown FORNECEDOR ou CLIENTE *(label dinâmico por tipo)*
   - Dropdown CENTRO DE CUSTO
   - Dropdown PROJETO (opcional)
   - Dropdown CATEGORIA
   - `BudgetBalanceBanner` *(quando CC + categoria selecionados: Dotado / Realizado / Disponível + barra de progresso)*

7. **Seção "Documento"**:
   - Linha: campo TIPO (NF, RPA...) + campo NÚMERO
   - Campo OBSERVAÇÕES

8. **Seção "Recorrência"** *(somente para novos lançamentos com campo Parcelas em branco)*:
   - Toggle Switch com label dinâmico
   - *(Se toggle ativo)* Label "INTERVALO" + 7 chips de intervalo (SEMANAL a ANUAL)
   - *(Se toggle ativo)* Campo DIA DO MÊS
   - *(Se toggle ativo)* Texto explicativo sobre o template de recorrência

#### Modais que abrem a partir do painel

- `PaymentRecordDialog` — valor pago + juros + multa + data
- `ReversalDialog` — exibição de dados originais + textarea de justificativa (mín. 10 caracteres)

---

### O Painel É Formulário ou Workspace?

O painel está tentando ser as duas coisas simultaneamente — e essa é sua principal limitação de UX.

**Como formulário:** as seções "Dados gerais", "Vínculos", "Documento" e "Recorrência" são campos editáveis com Save/Cancel no footer. O usuário preenche e salva. Funciona bem para criação.

**Como workspace:** as seções "Resumo", "Ações rápidas" e "Linha do tempo" são um dashboard de operações sobre o lançamento — o usuário lê, decide e age sem editar o cadastro. Funciona bem para operações.

O problema é que o painel mistura as duas identidades na mesma coluna scrollable, sem separação visual clara. O usuário que abriu o painel para "pagar" um lançamento precisa passar pelos campos editáveis de descrição, valor, conta, fornecedor, CC, categoria, documento e recorrência antes de chegar à linha do tempo — ou rolar para cima para encontrar as ações rápidas (que ficam entre o Resumo e o formulário).

**A ordem atual é:**
```
Status + Tipo
↓ Resumo (read-only)
↓ Ações rápidas (operar)
↓ Dados gerais (editar cadastro)
↓ Vínculos (editar cadastro)
↓ Documento (editar cadastro)
↓ Recorrência (editar cadastro)
↓ Linha do tempo (histórico)
```

O workspace (Ações rápidas) está intercalado entre o read-only (Resumo) e o cadastro (Dados gerais). A linha do tempo, que deveria estar próxima do Resumo (são ambas consultivas), está no final da fila, depois de todo o cadastro.

---

## Melhorias

### Alta Prioridade

**1. Separar ação destrutiva "Cancelar lançamento" do botão de fechar painel**

Renomear o botão de ação de domínio para "Anular" ou "Inativar", e garantir separação visual (espaço ou divider) em relação ao botão de fechar o formulário. Alternativa: mover o "Cancelar lançamento" para dentro de um dropdown de ações secundárias acessado por ícone `MoreVert`, tornando-o menos proeminente e menos suscetível a cliques acidentais.

**2. Unificar o comportamento de "Quitar" no context menu e no painel**

O item "Quitar" no menu de contexto deve abrir o mesmo `PaymentRecordDialog` que o botão do painel abre. Quitação silenciosa (sem chance de informar encargos) não deve existir como caminho padrão numa aplicação de prestação de contas pública.

**3. Adicionar preview de parcelamento antes de salvar**

Quando o campo "PARCELAS" for preenchido com N > 1, exibir inline (logo abaixo do campo) um preview calculado: "3 parcelas de R$ 333,33 (última: R$ 333,34) — vencimentos em 01/09, 01/10, 01/11". Confirmar no save com um step extra ou tooltip de impacto. Isso transforma um campo de texto silencioso num elemento com peso comunicativo proporcional ao seu efeito.

**4. Adicionar indicador de volume total filtrado**

Abaixo dos chips de filtro ou no cabeçalho da grade, exibir: "N lançamentos · R$ X total". Quando o filtro "A pagar" estiver ativo, indicar adicionalmente: "Y lançamentos ocultos (pagos/cancelados)". Isso elimina a percepção de lista incompleta.

**5. Mostrar "Estornar" no menu de contexto (para lançamentos PAID)**

O menu de contexto deve ser um espelho fiel das ações disponíveis no painel. Ocultar o estorno em um caminho secundário cria assimetria de capacidade dependente da rota de navegação.

---

### Média Prioridade

**6. Reorganizar a ordem das seções no painel lateral**

Proposta de ordem mais coerente com o fluxo mental do usuário:

```
[1] Status + Tipo (sempre)
[2] Resumo financeiro (sempre)
[3] Linha do tempo (sempre, quando existe)
[4] Ações disponíveis (agrupadas: [Operações] Quitar/Estornar | [Gestão] Duplicar/Editar | [Risco] Cancelar)
[5] Formulário de edição (apenas quando canEdit)
```

Isso coloca consulta (Resumo + Timeline) no topo, operações no meio, e edição de cadastro no final — respeitando a hierarquia de uso: a maioria das visitas ao painel é para consultar ou operar, não para editar dados cadastrais.

**7. Substituir a sobreposição de Quitar + Estornar + Comprovante por um grupo com hierarquia visual**

Para lançamentos PAID:

```
[PRIMÁRIO]    Comprovante (ação comum, não destrutiva)
[SECUNDÁRIO]  Estornar ▼ (abre dropdown com aviso antes de confirmar)
```

Isso evita que "Estornar" e "Comprovante" apareçam com pesos equivalentes.

**8. Tornar visível o motivo do desaparecimento da seção Recorrência**

Quando o usuário preenche "PARCELAS", exibir um texto informativo no lugar onde a seção de Recorrência estaria: _"Recorrência não disponível para lançamentos parcelados."_ Uma linha é suficiente. Elimina o comportamento mágico de desaparecimento silencioso.

**9. Mover o BudgetBalanceBanner para próximo do campo Valor**

O banner de rubrica (Dotado / Realizado / Disponível) deveria aparecer na seção "Dados gerais", logo abaixo do campo VALOR — não na seção "Vínculos". A relação entre valor digitado e orçamento disponível é imediata; a distância física entre eles obriga o usuário a manter dois elementos em memória de trabalho enquanto rola a tela.

**10. Adicionar contador de resultados de busca**

Quando a busca estiver ativa, exibir "X resultados para 'texto'" ao lado do campo de busca ou acima da grade. Elimina a ambiguidade entre "não encontrei" e "não existe".

**11. Fixar os filtros de chips mais usados e esconder os secundários**

Os chips "Todas", "Despesas" e "Receitas" disputam espaço com "A pagar", "Vence hoje", "Vencidas" e "Pagas". Uma reorganização por frequência de uso — com os 4 mais operacionais visíveis e os demais num dropdown "Mais filtros..." — reduziria a densidade visual da barra de filtros sem remover funcionalidade.

---

### Baixa Prioridade

**12. Adicionar possibilidade de combinar filtros**

"Despesas vencidas" e "Receitas pagas" são consultas frequentes em contextos operacionais. Permitir a combinação de filtros de tipo + status eliminaria a necessidade de usar a busca como substituto.

**13. Indicador visual de parcelas na linha da grade**

Lançamentos que fazem parte de um conjunto parcelado poderiam exibir um indicador discreto (ex.: `2/3` no canto da coluna TIPO ou após a descrição). Hoje o usuário só descobre que um lançamento é parcela ao abrir o painel e ler o campo "Parcela N/N" no Resumo.

**14. Separar "Duplicar" de "Cancelar" no menu de contexto**

Hoje o menu lista: Abrir detalhes / Editar / Quitar / Duplicar / Cancelar — na mesma lista contínua sem separadores. "Cancelar" (ação destructiva) fica imediatamente abaixo de "Duplicar" (ação segura). Um separador visual entre ações de gestão e ações destrutivas reduziria o risco de clique acidental.

**15. Dar ao toggle de Recorrência um contexto de custo/risco**

O texto atual ao ativar a recorrência é: "Um template de recorrência será criado. Os próximos lançamentos serão gerados automaticamente." Adicionar ao texto o número de lançamentos que serão gerados nos próximos meses (baseado no intervalo e data de vencimento) daria ao usuário uma noção concreta do comprometimento que está assumindo.

---

*Análise baseada em leitura estática do código-fonte de `TransactionsScreen.kt`, `TransactionDetailsPanel.kt`, `TransactionListFilter.kt` e `TransactionStatusStyle.kt`. Não inclui comportamentos de animação, responsividade ou acessibilidade que possam existir em componentes base (`BaseCrudPanel`, `WsButton`, etc.) não lidos nesta análise.*
