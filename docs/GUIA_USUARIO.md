# Guia do Usuário — SisgFin

> Sistema de Gestão Financeira para Organizações da Sociedade Civil  
> Conformidade TCESP / AUDESP

---

## Sumário

1. [Visão Geral do Sistema](#1-visão-geral-do-sistema)
2. [Configuração Inicial — O que fazer antes do primeiro lançamento](#2-configuração-inicial)
3. [Cadastro de Fornecedores e Clientes](#3-fornecedores-e-clientes)
4. [Registrando Lançamentos Financeiros](#4-lançamentos-financeiros)
5. [Liquidando um Lançamento (Quitar)](#5-liquidando-um-lançamento)
6. [Parcelamento Automático](#6-parcelamento-automático)
7. [Transferência entre Contas](#7-transferência-entre-contas)
8. [Lançamentos Recorrentes](#8-lançamentos-recorrentes)
9. [Contratos](#9-contratos)
10. [Contas a Receber](#10-contas-a-receber)
11. [Importação de Extrato OFX](#11-importação-de-extrato-ofx)
12. [Monitoramento: Dashboard, Fluxo de Caixa e Saldos](#12-monitoramento)
13. [Relatórios](#13-relatórios)
14. [Regras que o sistema aplica automaticamente](#14-regras-automáticas)
15. [Dicas e Boas Práticas](#15-boas-práticas)

---

## 1. Visão Geral do Sistema

O SisgFin é organizado em quatro grandes áreas:

```
CADASTROS          →  quem paga / quem recebe / onde classificar
FINANCEIRO         →  os lançamentos em si e seu ciclo de vida
GESTÃO             →  contratos e recorrências de longo prazo
RELATÓRIOS         →  visão consolidada para prestação de contas
```

O sistema segue uma **ordem lógica obrigatória**: antes de registrar qualquer lançamento, você precisa ter pelo menos uma **conta financeira** cadastrada. O restante dos cadastros (fornecedores, centros de custo, categorias) é opcional, mas quanto mais completo, mais rico será o controle.

---

## 2. Configuração Inicial

> **Faça isso antes de qualquer lançamento. O sistema bloqueia a criação de lançamentos sem uma conta financeira ativa.**

### Passo 1 — Cadastre as Contas e Caixas

**Caminho:** Sidebar → *Contas e Caixas*

Uma conta representa onde o dinheiro efetivamente fica: conta bancária, caixa físico, poupança ou aplicação. Cada lançamento **precisa** ser vinculado a uma conta.

1. Clique em **+ Nova Conta**.
2. Informe o nome (ex: "Conta Corrente Bradesco"), tipo (Conta Bancária, Caixa, Poupança ou Aplicação) e o **saldo inicial** — o valor que já existia antes do sistema entrar em operação.
3. Salve.

> **Atenção:** O saldo inicial é o ponto de partida histórico. Não registre as movimentações passadas como lançamentos — use o saldo inicial para isso.

---

### Passo 2 — Cadastre o Plano de Contas (Categorias)

**Caminho:** Sidebar → *Plano de Contas*

Categorias permitem classificar cada lançamento (ex: "Aluguel", "Material de Escritório", "Mensalidades Recebidas"). São necessárias para o controle de orçamento.

1. Clique em **+ Nova Categoria**.
2. Defina o nome e o tipo (Despesa ou Receita).
3. Salve.

---

### Passo 3 — Cadastre os Centros de Custo

**Caminho:** Sidebar → *Centros de Custo*

Centros de custo representam projetos, departamentos ou atividades da organização (ex: "Projeto Educação", "Administrativo"). Permitem filtrar e analisar gastos por área.

1. Clique em **+ Novo Centro de Custo**.
2. Informe o nome e, se desejar, uma descrição.
3. Salve.

---

### Passo 4 — Configure o Orçamento (Opcional, mas recomendado)

**Caminho:** Sidebar → *Orçamento*

O orçamento define um teto de gastos por categoria em cada mês. Quando um lançamento ultrapassa o limite, o sistema exibe um **alerta âmbar** no formulário — ele não bloqueia, mas avisa.

1. Selecione o mês e o ano.
2. Para cada categoria de despesa, informe o valor orçado.
3. Salve.

---

## 3. Fornecedores e Clientes

### Fornecedores

**Caminho:** Sidebar → *Fornecedores*

Represente qualquer empresa ou pessoa que presta serviço ou fornece material à organização.

1. Clique em **+ Novo Fornecedor**.
2. Informe nome, CPF/CNPJ, e-mail e telefone.
3. Na seção **Financeiro**, informe a chave PIX (se for pagar via PIX).
4. Salve.

> **Regra do sistema:** Fornecedores **inativos** não podem ser vinculados a novos lançamentos. Reative-os antes de usar.

---

### Clientes

**Caminho:** Sidebar → *Clientes*

Represente quem paga a organização — associados, empresas parceiras, ou qualquer fonte de receita.

1. Clique em **+ Novo Cliente**.
2. Informe os dados e escolha o papel:
   - **Cliente** — aparece apenas no selector de receitas
   - **Fornecedor e Cliente** — aparece em ambos os tipos de lançamento
3. Salve.

> O campo de contraparte no lançamento muda automaticamente o rótulo para **"Fornecedor"** em despesas e **"Cliente"** em receitas.

---

## 4. Lançamentos Financeiros

**Caminho:** Sidebar → *Movimentações*

Esta é a tela central do sistema. Aqui ficam todas as despesas e receitas da organização.

### 4.1 Criando um novo lançamento

1. Clique em **+ Nova Despesa** ou **+ Nova Receita** (os botões pré-selecionam o tipo).
2. Preencha os campos no painel lateral:

| Campo | Obrigatório | Dica |
|---|---|---|
| **Descrição** | Sim | Use algo descritivo: "Aluguel sede maio/2025" |
| **Valor** | Sim | Valor bruto do compromisso |
| **Tipo** | Sim | Despesa, Receita, Ajuste ou Estorno |
| **Conta** | Sim | De qual conta vai sair ou entrar o dinheiro |
| **Data de Emissão** | Sim | Data do documento (NF, boleto...) |
| **Data de Vencimento** | Sim | Quando deve ser pago/recebido |
| **Fornecedor / Cliente** | Não | Quem vai receber ou pagar |
| **Centro de Custo** | Não | Projeto ou área da organização |
| **Categoria** | Não | Rubrica do plano de contas |
| **Tipo de Documento** | Não | NF, RPA, Boleto, Recibo... |
| **Nº do Documento** | Não | Número da nota fiscal ou boleto |
| **Observações** | Não | Informações complementares |

3. Clique em **Salvar**.

> O lançamento é criado com status **Pendente** quando a data de vencimento é futura, e **Vencido** se já passou.

---

### 4.2 Ciclo de vida de um lançamento

Todo lançamento percorre os seguintes estados:

```
RASCUNHO ──→ PENDENTE ──→ PAGO
                  │
                  └──→ VENCIDO ──→ PAGO
                  │
                  └──→ PARCIAL ──→ PAGO
                  │
                  └──→ CANCELADO
```

| Status | Significado |
|---|---|
| **Rascunho** | Criado mas não confirmado |
| **Pendente** | Aguardando pagamento, dentro do prazo |
| **Vencido** | Prazo passou sem pagamento |
| **Parcial** | Pago parcialmente |
| **Pago** | Quitado integralmente |
| **Cancelado** | Encerrado sem pagamento |

> **Regra:** Lançamentos com status **Pago** ou **Cancelado** não podem ser cancelados novamente. O sistema bloqueia.

---

### 4.3 Editando um lançamento

Clique em qualquer linha da tabela para abrir o painel lateral. Enquanto o lançamento não estiver Pago ou Cancelado, todos os campos podem ser editados.

> Lançamentos **Pagos** só podem ser modificados via **Estorno** (veja seção 4.4).

---

### 4.4 Estornando um lançamento pago

Quando um pagamento foi registrado incorretamente:

1. Abra o lançamento já **Pago** no painel lateral.
2. Clique em **Estornar**.
3. Informe a **justificativa** (obrigatória — exigência para auditoria TCESP).
4. O sistema cria automaticamente um lançamento de estorno vinculado e reverte o saldo.

> Só lançamentos com status **Pago** podem ser estornados.

---

## 5. Liquidando um Lançamento

Liquidar significa registrar que o pagamento ou recebimento efetivamente aconteceu.

### Quitação total

1. Abra o lançamento no painel lateral (status Pendente, Vencido ou Parcial).
2. Clique no botão **Quitar**.
3. Confirme a **data de pagamento** e o **valor pago**.
4. Clique em **Confirmar**.
   - Se o valor pago = valor do lançamento → status vai para **Pago**.
   - Se o valor pago < valor do lançamento → status vai para **Parcial** (pode quitar o restante depois).

### Quitação parcial

Quando você paga parte de uma conta (ex: R$ 800 de uma nota de R$ 1.000):

1. Quitar com o valor parcial (R$ 800).
2. O lançamento fica como **Parcial**.
3. Na próxima vez, quitar com o restante (R$ 200).
4. O lançamento vai para **Pago**.

---

## 6. Parcelamento Automático

Quando uma compra é feita em parcelas (ex: 3x no cartão), o sistema pode gerar todas as parcelas automaticamente.

### Como parcelar

1. Na criação do lançamento, preencha o campo **Número de Parcelas**.
2. Informe o valor **total** (não o valor de cada parcela).
3. Salve.

O sistema cria o lançamento principal (parcela 1/N) e gera automaticamente os lançamentos das parcelas seguintes, cada um com vencimento um mês depois.

> **Regra de arredondamento:** O sistema usa piso para calcular o valor de cada parcela. A **última parcela** absorve o arredondamento para garantir que a soma bata exatamente no total. Por exemplo: R$ 100 em 3x → R$ 33,33 + R$ 33,33 + R$ **33,34**.

### Cancelamento em cascata

Se você cancelar o lançamento **pai** (a primeira parcela), o sistema cancela automaticamente todas as parcelas filhas que ainda estejam **Pendentes** ou **Rascunho**. Parcelas já **Pagas** não são afetadas.

---

## 7. Transferência entre Contas

Transferência é o movimento de dinheiro entre duas contas da própria organização (ex: do caixa para a conta bancária).

**Caminho:** Tela Movimentações → botão **Transferência**

1. Clique em **Transferência**.
2. Selecione a **conta de origem** e a **conta de destino**.
3. Informe o **valor** e a **data**.
4. Confirme.

O sistema cria automaticamente dois lançamentos vinculados: uma **Despesa** na conta de origem e uma **Receita** na conta de destino.

> **Regra:** Origem e destino não podem ser a mesma conta.  
> **Regra:** Cancelar um lado cancela automaticamente o outro.

---

## 8. Lançamentos Recorrentes

Use recorrências para despesas e receitas que acontecem toda mês (ou em outro intervalo fixo): aluguel, assinaturas, mensalidades de associados, etc.

O sistema gera os lançamentos automaticamente, sem precisar registrar um a um.

### Opção A — Criar diretamente na tela de Recorrências

**Caminho:** Sidebar → *Recorrências* → **+ Recorrência**

1. Preencha: descrição, valor, tipo, conta, intervalo e dia do mês.
2. Defina a **data de início**. Se quiser um encerramento, defina a **data de fim** (opcional).
3. Salve.

O sistema gera imediatamente os lançamentos dos próximos 2 meses.

### Opção B — Tornar recorrente ao criar um lançamento

Na tela de Movimentações, ao criar um **novo** lançamento:

1. Ative o **toggle "Tornar recorrente"**.
2. Escolha o intervalo (Mensal, Bimestral, Semestral...) e o dia do mês.
3. Salve normalmente.

O sistema cria o lançamento atual **e** um template de recorrência para o futuro.

### Intervalos disponíveis

| Intervalo | Frequência |
|---|---|
| Semanal | Toda semana (mesmo dia da semana) |
| Quinzenal | A cada 14 dias |
| Mensal | Uma vez por mês |
| Bimestral | A cada 2 meses |
| Trimestral | A cada 3 meses |
| Semestral | A cada 6 meses |
| Anual | Uma vez por ano |

> **Ajuste de meses curtos:** Se o dia do mês for 31 e o mês só tiver 30 (ou 28/29 em fevereiro), o sistema ajusta automaticamente para o último dia do mês.

### Pausar ou encerrar uma recorrência

1. Abra o template na tela *Recorrências*.
2. **Pausar** — desativa o template, mantém todos os lançamentos já gerados intactos.
3. **Cancelar Futuras** — desativa o template e cancela todos os lançamentos futuros ainda **Pendentes**. Lançamentos já **Pagos** são preservados.

---

## 9. Contratos

Use contratos quando a organização tiver um acordo formal com um fornecedor ou cliente — com valor total definido, prazo de vigência e execução parcelada ao longo do tempo.

**Caminho:** Sidebar → *Contratos*

### Criando um contrato

1. Clique em **+ Novo Contrato**.
2. Preencha:
   - **Número** — identificador do contrato (ex: "CT-001/2025")
   - **Objeto** — descrição resumida do que foi contratado
   - **Contratado** — o fornecedor ou cliente
   - **Tipo** — Despesa (contrato de fornecimento) ou Receita (contrato de serviço prestado)
   - **Valor Total** — o teto do contrato
   - **Início e Fim de Vigência**
3. Salve.

### Vinculando lançamentos ao contrato

Ao criar ou editar um lançamento em Movimentações:

1. No campo **Contrato (opcional)**, selecione o contrato.
2. O sistema auto-preenche o fornecedor com o contratado do contrato.
3. Salve.

### Acompanhando a execução

No painel do contrato você vê:
- **Valor Total** — o que foi contratado
- **Consumido** — soma dos pagamentos já realizados
- **Saldo Restante** — quanto ainda pode ser gasto
- **Barra de progresso** — fica amarela quando passa de 80% e vermelha quando atinge 100%

> **Aviso de extrapolação:** Se um lançamento ultrapassar o valor total do contrato, o sistema exibe um **alerta âmbar** no formulário. O lançamento pode ser salvo mesmo assim — o sistema alerta, mas não bloqueia.

---

## 10. Contas a Receber

**Caminho:** Sidebar → *Recebíveis*

Tela dedicada para acompanhar todas as receitas ainda não recebidas (Pendentes, Vencidas ou Parciais).

### O que você vê

**Tiles de aging** no topo mostram o total em aberto dividido por faixa de atraso:

| Tile | O que significa |
|---|---|
| A vencer | Ainda dentro do prazo |
| 1–30 dias | Vencidos há até 30 dias |
| 31–60 dias | Vencidos entre 31 e 60 dias |
| 61+ dias | Vencidos há mais de 2 meses |

A tabela abaixo mostra os lançamentos agrupados por cliente, com a coluna **Atraso** indicando quantos dias já passaram do vencimento.

> Linhas com fundo avermelhado representam recebíveis **Vencidos** — priorize a cobrança deles.

### Como quitar um recebível

1. Acesse Movimentações e localize o lançamento.
2. Clique nele para abrir o painel lateral.
3. Clique em **Quitar** e informe o valor e a data de recebimento.

---

## 11. Importação de Extrato OFX

A importação permite trazer automaticamente as movimentações do banco para o sistema, evitando digitação manual.

**Caminho:** Sidebar → *Importar OFX*

### Exportando o extrato do banco

1. Acesse o internet banking da sua instituição.
2. Procure a opção de exportar o extrato no formato **OFX** ou **OFC**.
3. Escolha o período desejado e faça o download do arquivo.

### Importando no SisgFin

1. Na tela *Importar OFX*, clique em **Selecionar Arquivo** e escolha o arquivo `.ofx` baixado.
2. Selecione a **conta** correspondente ao extrato.
3. Revise o resumo (total de registros, novos e duplicatas detectadas).
4. Clique em **Importar**.

> **Deduplicação automática:** O sistema detecta lançamentos já importados anteriormente usando o identificador único do OFX (`FitId`). Duplicatas são ignoradas automaticamente.

Os lançamentos importados chegam com status **Pago** e podem ser editados para adicionar categorias, fornecedores e centros de custo.

---

## 12. Monitoramento

### Dashboard

**Caminho:** Sidebar → *Dashboard*

A tela principal de visão geral. Os tiles no topo mostram:

| Tile | O que mostra |
|---|---|
| Saldo Consolidado | Soma dos saldos de todas as contas ativas |
| Receita do Mês | Total recebido no mês corrente |
| Despesa do Mês | Total pago no mês corrente |
| Vencidos | Despesas não pagas com prazo vencido |
| A Vencer (7 dias) | Compromissos que vencem na próxima semana |
| Recebíveis Vencidos | Receitas em atraso |

Na coluna lateral direita, você encontra:
- **Saldos por conta** — valor atualizado de cada conta
- **Vencidos** — lista dos lançamentos mais urgentes
- **Recebíveis vencidos** — receitas em atraso
- **Projeção dos próximos dias** — previsão de caixa considerando os compromissos agendados

---

### Fluxo de Caixa

**Caminho:** Sidebar → *Fluxo de Caixa*

Mostra a projeção dia a dia das entradas e saídas programadas. Linhas em **vermelho** indicam dias em que o saldo projetado ficará negativo — sinal de que será necessário providenciar recursos.

Use os filtros **7 / 14 / 30 dias** para ampliar ou reduzir a janela de projeção, e o filtro de conta para ver apenas uma conta específica.

---

### Painel de Saldos

**Caminho:** Sidebar → *Painel de Saldos*

Visão detalhada do saldo de cada conta, calculado com base em:  
`Saldo = Saldo Inicial + Receitas Pagas − Despesas Pagas`

---

### Extrato

**Caminho:** Sidebar → *Extrato*

Histórico cronológico de todos os lançamentos liquidados de uma conta, com saldo acumulado após cada movimentação — equivalente ao extrato bancário.

---

## 13. Relatórios

**Caminho:** Sidebar → *Relatórios*

Gera consolidações financeiras por período. Úteis para prestação de contas ao TCESP/AUDESP e para reuniões de diretoria.

Selecione o **período** (mês/ano de início e fim) e clique em **Gerar**. O relatório mostra:

- Receitas e despesas por categoria
- Saldo por conta
- Evolução mensal

---

## 14. Regras Automáticas

O sistema aplica as seguintes regras sem intervenção do usuário:

### Regras de cadastro

| Situação | O que acontece |
|---|---|
| Tentar inativar uma conta com saldo ≠ 0 | **Bloqueado** — transfira ou zere o saldo primeiro |
| Tentar vincular fornecedor inativo a um lançamento | **Bloqueado** — reative o fornecedor antes |
| Dois fornecedores com o mesmo CPF/CNPJ | **Bloqueado** — documento deve ser único |

### Regras de lançamentos

| Situação | O que acontece |
|---|---|
| Data de vencimento ultrapassada e não pago | Status muda automaticamente para **Vencido** |
| Cancelar lançamento parcelado (parcela 1) | Cancela em cascata todas as parcelas Pendentes/Rascunho |
| Cancelar um lado de uma transferência | Cancela automaticamente o lançamento vinculado |
| Estorno | Exige justificativa; só possível para lançamentos Pagos |
| Lançamento ultrapassa orçamento da categoria | **Alerta âmbar** — não bloqueia, mas avisa |
| Lançamento ultrapassa valor total do contrato | **Alerta âmbar** — não bloqueia, mas avisa |

### Regras de arredondamento

| Situação | O que acontece |
|---|---|
| Parcelamento com valor não divisível exatamente | Parcelas usam o piso; última parcela absorve a diferença |
| Dia 31 em mês com menos dias | Ajustado automaticamente para o último dia do mês |

---

## 15. Boas Práticas

### Ordem recomendada para novos usuários

```
1. Cadastre as Contas e Caixas (obrigatório)
2. Cadastre o Plano de Contas
3. Cadastre os Centros de Custo
4. Cadastre os Fornecedores e Clientes
5. Configure o Orçamento do mês
6. Comece a registrar os lançamentos
```

### Rotina mensal sugerida

| Momento | Ação |
|---|---|
| **Início do mês** | Configure o orçamento do mês; revise os lançamentos recorrentes gerados automaticamente |
| **Ao longo do mês** | Registre as novas despesas e receitas à medida que ocorrem |
| **Ao pagar/receber** | Quite o lançamento correspondente com a data e valor corretos |
| **Semanalmente** | Acesse o Dashboard e verifique os vencidos e recebíveis |
| **Final do mês** | Importe o extrato OFX do banco; concilie os lançamentos; gere o relatório |

### Dicas importantes

- **Nunca use o saldo inicial para registrar histórico anterior.** Se o sistema está entrando em operação em julho, o saldo inicial de julho já incorpora tudo que aconteceu antes.

- **Sempre preencha Categoria e Centro de Custo.** Eles não são obrigatórios, mas lançamentos sem classificação não aparecem nos relatórios por rubrica — o que pode ser um problema para prestação de contas ao TCESP.

- **Use contratos para despesas continuadas.** Serviços de limpeza, segurança, manutenção predial — vincule ao contrato desde o início para monitorar o consumo.

- **Configure recorrências para despesas fixas.** Aluguel, internet, telefone — um template de recorrência mensal elimina o risco de esquecer de lançar.

- **Revise os vencidos toda semana.** O tile "Vencidos" no Dashboard nunca deve crescer sem atenção — cada lançamento vencido representa um compromisso não cumprido ou um recebimento não cobrado.

- **Importe o extrato OFX mensalmente.** A conciliação bancária é a melhor forma de garantir que o que está no sistema bate com o que está na conta.

---

*SisgFin — Sistema de Gestão Financeira | Versão 1.0.0*
