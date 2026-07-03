# Análise da Planilha — Controle Finan.xlsm

**Arquivo:** `Controle Finan.xlsm`  
**Organização:** Associação Terapêutica Cannabis Medicinal Flor da Vida  
**Projeto/Convênio:** `501.145-0`  
**Criado por:** Luis Felipe | **Criado em:** 20/03/2024 | **Última modificação:** 30/03/2026  
**Ferramenta:** Microsoft Excel 16.03 (com macros VBA)

---

## Propósito Geral

Planilha de **controle financeiro de verbas públicas (subvenção / cofinanciamento)**, utilizada para registrar, organizar e prestar contas de todos os recursos recebidos e gastos dentro de um convênio com o poder público. Toda a estrutura segue as exigências do **TCESP** (Tribunal de Contas do Estado de São Paulo) e do sistema **AUDESP Fase V**.

---

## Estrutura — Abas e Funções

| # | Aba | Função |
|---|-----|--------|
| 1 | **Lançamentos** | Formulário individual de registro de cada despesa: CNPJ do credor, valor, tipo de documento (NF-e/RPA), data de pagamento, código de aquisição, parcelas, complemento histórico e descrição automática |
| 2 | **Livro Diario** | Livro-razão com todos os lançamentos consolidados (receitas e despesas) — base de dados central da planilha; colunas: ID projeto, processo, CPF/CNPJ, credor, tipo de despesa, valor, data, documento, parcelas, descrição |
| 3 | **Saldos** | Acompanhamento diário dos saldos da conta corrente e conta de aplicação bancária; inclui painel de conferência com cálculo de divergências |
| 4 | **Planejamento** | Orçamento previsto por categoria de despesa, separando recurso de cofinanciamento de contrapartida; cobre todo o período do convênio (data inicial/final) |
| 5 | **Demonstrativo** | Tabela de classificação contábil oficial (TCESP / AUDESP) de cada tipo de despesa — tabela de referência/dicionário do plano de contas |
| 6 | **Balancete** | Comparativo mensal/anual: **custo orçado × valor utilizado × saldo disponível** por categoria; inclui resumo lateral com subvenção mensal e anual |
| 7 | **Plan** | Demonstrativo financeiro consolidado — totais de receitas vs despesas com saldo final; agrupado por grande categoria |
| 8 | **BD_Fornecedores** | Cadastro de categorias de despesa e fornecedores recorrentes; funciona como base de dados para listas de seleção nas demais abas |
| 9 | **Consultas** | Painel de consulta com histórico de saldo diário da conta corrente e da conta de aplicação, organizado por data |
| 10 | **Classificação Despesas** | Tabela de referência com todos os códigos e descrições do plano de trabalho do convênio |

---

## Grupos de Categorias de Despesa Identificados

| Código | Grupo |
|--------|-------|
| 1 | Recursos Humanos (Salários, Férias, 13º, Aviso Prévio, TRCT) |
| 2 | Encargos Trabalhistas/Sociais (INSS, FGTS, IRRF, PIS, Sindicato) |
| 3 | Benefícios (Vale-Alimentação, Vale-Refeição, Vale-Transporte, Convênio Saúde/Odonto) |
| 4 | Despesas com Pessoal (Exame médico, Seguro de Vida, Reembolsos) |
| 5 | Materiais de Consumo (Gêneros alimentícios, material de expediente, limpeza, informática, uniforme) |
| 7 | Serviços de Terceiros (Manutenção, locação, vigilância, cópias, serviços profissionais) |
| 8 | Utilidades Públicas (Água, Energia elétrica, Telefonia/Internet) |
| 9 | Equipamentos e Material Permanente |

---

## Tipos de Documentos Fiscais Utilizados

- NF-e (Nota Fiscal Eletrônica)
- RPA (Recibo de Pagamento a Autônomo)
- Extrato bancário
- GUIA (FGTS, GRRF, INSS)

---

## Limitações da Planilha (que o SisgFin deve superar)

| Limitação | Impacto |
|-----------|---------|
| Sem CRUD real — dados inseridos manualmente célula a célula | Alto risco de erro humano e inconsistência |
| Sem validação de regras de negócio em tempo real | Valores incorretos só são detectados na conferência manual |
| Sem controle de acesso por usuário | Qualquer pessoa com acesso ao arquivo pode alterar qualquer dado |
| Sem histórico de auditoria (quem alterou o quê) | Impossível rastrear modificações |
| Sem alertas de vencimento | Pagamentos atrasados dependem de controle manual |
| Sem vinculação direta ao extrato bancário | Conciliação é feita manualmente comparando colunas |
| Sem gestão de parcelamento automático | Parcelas precisam ser lançadas uma a uma |
| Sem exportação estruturada para AUDESP | Relatórios precisam ser reformatados manualmente |
| Dados de fornecedores não normalizados | Mesmo CNPJ pode aparecer com grafias diferentes |
| Sem backup automático | Arquivo único, risco de perda total |
