# SisgFin — Requisitos e Regras de Negócio

**Versão:** 2.0 (revisada — escopo focused)
**Data:** 11/06/2026
**Stack:** Kotlin + Compose Desktop + SQLite + Exposed + Flyway + Koin
**Perfil do produto:** sistema financeiro pequeno/médio, competente e específico para organizações do terceiro setor que prestam contas de verbas públicas (TCESP/AUDESP). Não é ERP. Um desenvolvedor. Um cliente.

---

## Princípio de Escopo

> Fazer poucas coisas com excelência é melhor do que fazer muitas coisas pela metade.

O SisgFin resolve um problema bem definido: a organização recebe subvenção pública, precisa registrar cada centavo gasto, controlar quanto sobrou em cada rubrica do convênio, e gerar os relatórios exigidos pelo TCE — sem depender de planilha Excel frágil e sem rastreabilidade.

**O que o sistema faz:**
1. Registra receitas e despesas com os dados obrigatórios para prestação de contas
2. Controla o saldo de cada conta bancária em tempo real
3. Mostra quanto foi orçado vs quanto foi gasto por rubrica
4. Gera os relatórios: Livro Diário, Balancete, Demonstrativo Financeiro, Comprovante

**O que o sistema não faz (fora de escopo):**
- Conciliação bancária automática via OFX/CSV — o operador confere manualmente pelo extrato
- Múltiplos projetos complexos simultâneos — o cliente opera um ou dois convênios por vez
- Folha de pagamento — despesas com RH são lançamentos como qualquer outro
- Módulo de compras ou estoque
- Integração com sistemas externos (AUDESP aceita exportação manual)

---

## 1. Cadastros

Entidades mestras que alimentam os lançamentos. Todos com CRUD completo (criar, editar, inativar) e auditoria de quem fez o quê.

### 1.1 Fornecedores / Credores

Qualquer pessoa física ou jurídica que recebe pagamento da organização.

**Campos:** CNPJ/CPF, razão social, nome fantasia, e-mail, telefone, Pix, banco/agência/conta, observações, ativo/inativo.

- **RN-01:** CNPJ/CPF é único — o sistema bloqueia duplicata antes de salvar.
- **RN-02:** Fornecedor inativo não aparece em novas seleções de lançamento, mas o histórico fica preservado.
- **RN-03:** CNPJ e CPF são validados (dígitos verificadores) antes de salvar.

### 1.2 Contas Financeiras

As contas bancárias da organização.

**Tipos:** `CORRENTE`, `APLICACAO`, `CAIXA`.
**Campos:** nome descritivo, banco, agência, número da conta, tipo, saldo inicial.

- **RN-04:** O saldo atual nunca é digitado — é sempre calculado: `saldo_inicial + Σreceitas_pagas − Σdespesas_pagas`.
- **RN-05:** Conta só pode ser inativada se o saldo calculado for zero.
- **RN-06:** A conta do tipo `APLICACAO` tem campos adicionais por lançamento: aplicação, resgate e rendimentos — para refletir a estrutura da aba Saldos/Consultas da planilha.

### 1.3 Projetos / Convênios

O convênio com o poder público que origina a verba.

**Campos:** código (ex: `501.145-0`), nome, descrição, data de início, data de fim, situação (`ATIVO` / `ENCERRADO` / `SUSPENSO`).

- **RN-07:** Projeto com lançamentos vinculados não pode ser excluído — apenas inativado.
- **RN-08:** Ao criar um lançamento de despesa com data fora do período do projeto, o sistema exibe um aviso (não bloqueio, pois ajustes pós-encerramento existem).

### 1.4 Plano de Contas (Categorias)

As rubricas do convênio — estrutura padronizada pelo TCESP.

**Campos:** código hierárquico (ex: `1.1`), nome, grupo pai, classificação TCESP, classificação AUDESP Fase V (grupo de despesa + categoria), tipo (`DESPESA` / `RECEITA`), ativo/inativo.

- **RN-09:** O plano de contas é pré-carregado via seed com as categorias padrão; o operador pode adicionar subcategorias personalizadas mas não alterar os códigos da estrutura oficial.
- **RN-10:** Categoria com lançamentos vinculados não pode ser excluída.

### 1.5 Usuários

Sistema simples com dois perfis práticos.

**Perfis:**
- `ADMIN` — acesso total, incluindo inativação e configurações
- `OPERADOR` — cria e edita lançamentos; não pode excluir nem configurar o sistema

- **RN-11:** Toda operação de escrita registra `userId + timestamp`. Isso é inegociável para um sistema que lida com verba pública.
- **RN-12:** Não há perfil de "somente leitura" por ora — o ADMIN pode ver tudo, e uma futura tela de relatórios pode ser aberta sem login se necessário (decisão futura).

---

## 2. Lançamentos Financeiros

O coração do sistema. Toda movimentação de dinheiro nasce aqui.

### 2.1 Campos de um Lançamento

| Campo | Obrigatório | Observação |
|-------|-------------|------------|
| Tipo | Sim | `RECEITA`, `DESPESA`, `TRANSFERENCIA`, `ESTORNO` |
| Status | — | Controlado pela state machine |
| Descrição / Identificação | Sim | O que é esse gasto/recebimento |
| Valor total | Sim | BigDecimal, > 0 |
| Data de emissão | Sim | Data do documento |
| Data de vencimento | Sim | ≥ data de emissão |
| Data de pagamento | Não* | Preenchido ao confirmar o pagamento |
| Conta financeira | Sim | Onde o dinheiro sai ou entra |
| Fornecedor / Credor | Sim para DESPESA | |
| Projeto / Convênio | Sim para DESPESA | |
| Categoria (rubrica) | Sim para DESPESA | |
| Tipo de documento | Não | `NF-e`, `RPA`, `EXTRATO`, `GUIA`, `RECIBO`, `OUTRO` |
| Número do documento | Não | |
| Parcela atual / total | Não | Preenchido automaticamente no parcelamento |
| Observações | Não | |

### 2.2 State Machine de Status

```
RASCUNHO ──► PENDENTE ──► PAGO      (terminal)
    │             │
    │             ├──► VENCIDO ──► PAGO / PARCIAL / CANCELADO
    │             ├──► PARCIAL ──► PAGO / CANCELADO
    │             └──► CANCELADO  (terminal)
    └──► CANCELADO
```

- **RN-13:** `PAGO` e `CANCELADO` são estados terminais — o lançamento não pode mais ser editado diretamente.
- **RN-14:** Para corrigir um lançamento `PAGO`, o usuário cria um `ESTORNO` — novo lançamento com valor negativo vinculado ao original, com justificativa obrigatória.
- **RN-15:** O sistema roda um job na abertura do app (ou diariamente) que move `PENDENTE → VENCIDO` para todos os lançamentos com `dataVencimento < hoje`.
- **RN-16:** Ao registrar pagamento, `dataPagamento` não pode ser anterior à `dataEmissão`.

### 2.3 Parcelamento

- **RN-17:** Ao criar um lançamento com `totalParcelas > 1`, o sistema gera automaticamente N lançamentos filhos com vencimentos mensais sequenciais, todos referenciando o `idTransacaoPai`.
- **RN-18:** Valor de cada parcela = `valorTotal / totalParcelas`; a última parcela absorve a diferença de arredondamento.
- **RN-19:** Cancelar a transação-pai cancela automaticamente todas as parcelas filhas ainda `PENDENTE` ou `RASCUNHO`. Parcelas já `PAGAS` permanecem intactas.

### 2.4 Transferência entre Contas

- **RN-20:** Uma transferência gera dois lançamentos atomicamente (em uma transação de banco): `DESPESA` na conta de origem e `RECEITA` na conta de destino, ambos com `tipo = TRANSFERENCIA`, vinculados por `idTransacaoPai`.
- **RN-21:** Cancelar uma transferência cancela ambos os lançamentos na mesma transação de banco.

### 2.5 Estorno

- **RN-22:** Estorno exige justificativa (campo `observacoes` obrigatório).
- **RN-23:** O estorno gera um lançamento do tipo `ESTORNO` com valor negativo e referência ao lançamento original — nunca altera o original.

---

## 3. Saldos e Extrato

### 3.1 Painel de Saldos

- Saldo atual de cada conta (calculado, nunca digitado — RN-04)
- Data do último lançamento por conta
- Total de lançamentos pendentes e vencidos (valor + quantidade)

### 3.2 Extrato por Conta

- Lista cronológica de todos os lançamentos `PAGO` de uma conta em um período
- Colunas: data, descrição, credor, tipo, documento, valor (+ / −), saldo acumulado
- Filtros: período, tipo, projeto, categoria
- Exportável para Excel e PDF

---

## 4. Orçamento por Rubrica (Planejamento)

Cada projeto tem um orçamento associado com valores por rubrica do plano de contas.

**Campos por rubrica:** categoria, valor mensal orçado, valor anual orçado (calculado ou digitado).

- **RN-24:** O **realizado** é calculado automaticamente: soma dos lançamentos `PAGO` vinculados ao projeto + categoria no período.
- **RN-25:** O **saldo disponível** = valor anual orçado − realizado.
- **RN-26:** Ao criar um lançamento de despesa, o sistema exibe o saldo disponível da rubrica em tempo real no formulário.
- **RN-27:** Se o lançamento ultrapassar o saldo disponível da rubrica, o sistema exibe um aviso claro. Não bloqueia — o operador decide — mas o alerta é obrigatório.
- **RN-28:** Orçamento de projeto encerrado é somente leitura.

---

## 5. Relatórios

Os quatro relatórios que a planilha tentava gerar e o SisgFin entregará prontos, sem reformatação.

### 5.1 Livro Diário

Lista de todos os lançamentos `PAGO` de um período, no formato exigido pelo TCESP:

Colunas: ID do projeto, identificação do processo, CPF/CNPJ do contratado, credor, tipo de despesa, valor total, data de pagamento, tipo de documento, número do documento, parcela atual/total, valor da parcela, **descrição automática**.

**Descrição automática** gerada pelo sistema:
- Despesa: `"PAGO A, [CREDOR] CF [TIPO_DOC] [NÚMERO]"`
- Receita: `"RECEBI VALOR REF., [DESCRIÇÃO] CF [TIPO_DOC]"`

- **RN-29:** A descrição é gerada no momento do pagamento e armazenada — não recalculada depois, pois precisa ser imutável para prestação de contas.
- Exportação: PDF e Excel.

### 5.2 Balancete

Comparativo por rubrica para um período selecionado:

| Rubrica | Custo Mensal | Custo Anual | Valor Utilizado | Saldo |
|---------|-------------|-------------|-----------------|-------|

Com resumo ao lado: subvenção mensal, valor anual total a receber, valor total utilizado.

- **RN-30:** O balancete é calculado dinamicamente — não armazenado. O snapshot para entrega é a exportação.

### 5.3 Demonstrativo Financeiro

Relatório consolidado receita × despesa para um período:
- Subtotais por grupo de rubrica
- Saldo do período
- Saldo acumulado (saldo do período + saldo do período anterior)
- Classificação AUDESP ao lado de cada rubrica

### 5.4 Comprovante de Lançamento

Reproduz o formulário da aba **Lançamentos** da planilha em PDF para arquivo físico ou envio:

- ID, identificação/descrição, CNPJ/CPF, contratado, código de aquisição, item, parcela, valor, data de pagamento, tipo e número do documento, histórico descritivo automático.
- **RN-31:** Comprovante só pode ser gerado para lançamentos com status `PAGO`.

---

## 6. Rastreabilidade (Não Opcional)

O cliente lida com verba pública. Auditoria não é feature — é requisito.

- **Audit log** (`audit_log`): toda operação de escrita registra entidade, ID, operação (CREATE/UPDATE/DELETE), campo alterado, valor anterior, valor novo, `userId`, `timestamp`.
- **Timeline de transação:** histórico cronológico de cada lançamento — criação, mudanças de status, pagamentos parciais, estornos — com autor e timestamp.
- **RN-32:** Registros de auditoria são imutáveis. Sem DELETE ou UPDATE na tabela `audit_log`.
- **RN-33:** A timeline fica acessível no painel de detalhes de cada lançamento.

---

## 7. Dashboard

Painel de abertura — visão rápida do que importa.

**KPIs:**
- Saldo atual de cada conta
- Total recebido no mês
- Total gasto no mês
- Lançamentos vencidos: count + valor total (badge vermelho se > 0)
- Lançamentos a vencer em 7 dias: count + valor

**Lista:**
- Últimas 10 movimentações com status e valor

Sem gráficos complexos na primeira entrega — KPIs simples e a lista já resolvem o problema.

---

## 8. Regras de Negócio — Tabela Consolidada

| Código | Regra |
|--------|-------|
| RN-01 | CNPJ/CPF único entre fornecedores |
| RN-02 | Fornecedor inativo bloqueado em novos lançamentos |
| RN-03 | CNPJ e CPF validados por dígitos verificadores |
| RN-04 | Saldo de conta é calculado, nunca digitado |
| RN-05 | Conta só inativa com saldo zero |
| RN-06 | Conta APLICACAO tem campos: aplicação, resgate, rendimentos |
| RN-07 | Projeto com lançamentos não pode ser excluído |
| RN-08 | Aviso ao lançar despesa fora do período do convênio |
| RN-09 | Plano de contas pré-carregado; códigos-raiz imutáveis |
| RN-10 | Categoria com lançamentos não pode ser excluída |
| RN-11 | Toda escrita registra userId + timestamp |
| RN-12 | Perfis: ADMIN (acesso total) e OPERADOR |
| RN-13 | PAGO e CANCELADO são estados terminais — sem edição direta |
| RN-14 | Correção de lançamento PAGO = criar ESTORNO vinculado |
| RN-15 | Job automático: PENDENTE → VENCIDO ao abrir o app |
| RN-16 | Data de pagamento ≥ data de emissão |
| RN-17 | Parcelamento gera lançamentos filhos automaticamente |
| RN-18 | Último filho absorve diferença de arredondamento |
| RN-19 | Cancelar pai cancela filhos PENDENTE/RASCUNHO; respeita PAGO |
| RN-20 | Transferência = dois lançamentos atômicos vinculados |
| RN-21 | Cancelar transferência cancela ambos os lados atomicamente |
| RN-22 | Estorno exige justificativa obrigatória |
| RN-23 | Estorno cria lançamento negativo; nunca altera o original |
| RN-24 | Realizado = soma automática de lançamentos PAGO por rubrica |
| RN-25 | Saldo disponível = orçado − realizado |
| RN-26 | Saldo da rubrica exibido em tempo real no formulário de lançamento |
| RN-27 | Aviso ao ultrapassar orçamento de rubrica; não bloqueia |
| RN-28 | Orçamento de projeto encerrado é somente leitura |
| RN-29 | Descrição do Livro Diário gerada e armazenada no pagamento |
| RN-30 | Balancete calculado dinamicamente; exportação = snapshot |
| RN-31 | Comprovante PDF apenas para lançamentos PAGO |
| RN-32 | Audit log imutável — sem DELETE/UPDATE |
| RN-33 | Timeline de transação acessível no painel de detalhes |

---

## 9. Fases de Entrega

### Fase 1 — Cadastros e Auth ✅ (concluída em grande parte)
- [x] Login com sessão
- [x] Usuários (ADMIN / OPERADOR)
- [x] Fornecedores
- [x] Contas Financeiras
- [x] Projetos
- [x] Funcionários
- [x] Plano de Contas (Categorias)

### Fase 2 — Motor Transacional (em andamento)
- [x] Modelo `Transaction` com state machine
- [x] `OverdueEngine` (job automático PENDENTE → VENCIDO)
- [x] `TransactionTimeline`
- [ ] Tela de lançamentos: listagem com filtros e painel de detalhes
- [ ] Formulário de criação/edição com validação de todas as RNs
- [ ] Parcelamento automático (geração de filhos — RN-17/18/19)
- [ ] Modal de baixa de pagamento (rápido, sem abrir o formulário completo)
- [ ] Transferência atômica entre contas (RN-20/21)
- [ ] Estorno com justificativa (RN-22/23)

### Fase 3 — Saldos e Orçamento
- [ ] Painel de saldos por conta (calculado — RN-04)
- [ ] Extrato por conta com saldo acumulado
- [ ] Campos extras de conta APLICACAO (aplicação, resgate, rendimentos — RN-06)
- [ ] Orçamento por rubrica vinculado ao projeto
- [ ] Saldo disponível em tempo real no formulário de lançamento (RN-26)
- [ ] Alertas de orçamento no dashboard (RN-27)

### Fase 4 — Relatórios
- [ ] Livro Diário com descrição automática e exportação PDF/Excel
- [ ] Balancete (orçado × realizado × saldo)
- [ ] Demonstrativo Financeiro com classificação AUDESP
- [ ] Comprovante individual em PDF (RN-31)

### Fase 5 — Produção
- [ ] Dashboard com KPIs reais (sem dados fictícios)
- [ ] Backup automático do arquivo SQLite
- [ ] Instalador (MSI / DEB)
