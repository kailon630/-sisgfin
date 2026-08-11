# R6 — Dimensões de classificação (uso real)

## 1. Resumo executivo

Nenhuma das dimensões `project_id` (centro de custo), `category_id`, `financial_project_id` ou `contract_id` é NOT NULL no banco nem obrigatória no formulário/validator. Nos últimos 12 meses desta base, **0%** de preenchimento em todas. Relatórios TCESP principais usam sobretudo **centro de custo + categoria** (Balancete) e **categoria** (Demonstrativo); Livro Diário não lista essas dimensões nas colunas do exporter. O mapeamento Kotlin `costCenterId` ↔ coluna SQL `project_id` é consistente no domínio; `projectId` Kotlin aponta para `financial_project_id`.

## 2. Estado atual

### 2.1 Obrigatoriedade form × banco

| Dimensão | Kotlin | Coluna SQL | DB NOT NULL? | Form UI |
|----------|--------|------------|--------------|---------|
| Centro de custo | `costCenterId` | **`project_id`** | NULL (YES) | opcional (`WsSelectField` default `nullable=true`) |
| Categoria | `categoryId` | `category_id` | NULL | opcional |
| Projeto financeiro | `projectId` | `financial_project_id` | NULL | “PROJETO (opcional)” |
| Contrato | `contractId` | `contract_id` | NULL | “CONTRATO (OPCIONAL)”, só novo lançamento |

`TransactionValidator` exige descrição, conta e valor > 0 — **não** exige nenhuma dimensão acima.

Nullability confirmada em `information_schema` (todas `is_nullable = YES`).

### 2.2 Consumo nos relatórios TCESP (`ReportsExporter.kt` / ViewModel)

| Relatório | Dimensões consumidas |
|-----------|----------------------|
| **Livro Diário** | período/conta por movimentos PAID (`paymentDate`); exporter **não** colunas de CC/categoria/projeto/contrato |
| **Balancete** | **`costCenterId` + `categoryId`** (rubrica orçamentária) |
| **Demonstrativo** | **`categoryId`** (agrega por categoria) |
| Comprovante PDF (auxiliar) | `categoryCode/Name`, `costCenterName` |
| Aba/consulta de projetos | `sumRealizedByProject` usa `FinancialTransactionsTable.projectId` (= `financial_project_id`) |

**Não usados** pelos três exporters principais (Livro Diário / Balancete / Demonstrativo) como eixo: `financial_project_id`, `contract_id`.  
`contract_id` também não aparece no comprovante PDF listado.

### 2.3 Mapeamento `project_id` → `costCenterId`

```18:18:src/main/kotlin/br/com/sisgfin/financial/transactions/FinancialTransactionsTable.kt
    val costCenterId = integer("project_id").nullable()
```

```38:38:src/main/kotlin/br/com/sisgfin/financial/transactions/FinancialTransactionsTable.kt
    val projectId            = integer("financial_project_id").nullable()
```

Tabela Exposed de centros de custo mapeia a tabela SQL histórica **`projects`** (legado de nomenclatura). Comentário da V28: `project_id` existente = Centro de Custo; `financial_project_id` = projeto real.

#### Pontos de referência no código (semântica)

| Local | Uso | Semântica |
|-------|-----|-----------|
| `FinancialTransactionsTable.kt:18` | `costCenterId` ↔ `project_id` | Centro de custo — **correto** |
| `FinancialTransactionsTable.kt:38` | `projectId` ↔ `financial_project_id` | Projeto financeiro — **correto** |
| `Transaction.kt` | campos `costCenterId` / `projectId` | alinhados ao Table |
| `TransactionRepository` insert/update/row map | grava ambos | alinhado |
| `BudgetItemsTable.kt:9` | `costCenterId = integer("project_id")` | Orçamento por CC — **correto** (mesmo legado de nome) |
| `BudgetItemRepository` `sumRealized*` | filtra `FinancialTransactionsTable.costCenterId` | CC — **correto** |
| `ReportsViewModel` Balancete | `item.costCenterId` | CC — **correto** |
| `findStatementEntries` | filtros `costCenterId` e `projectId` | CC vs projeto — **correto** |
| `sumRealizedByProject` | `FinancialTransactionsTable.projectId` | projeto financeiro — **correto** |
| `RecurrenceTemplatesTable` | `cost_center_id` + `financial_project_id` | nomes SQL distintos (mais claros que FT) |
| `TransactionService.warnIfOutsideCostCenterPeriod` | usa `costCenterId` + repositório de CC | trata como CC/período de convênio — **correto** no modelo atual |
| `TransactionDetailsPanel` | labels “CENTRO DE CUSTO” / “PROJETO” | UI alinhada ao mapeamento Kotlin |

Não foi encontrado ponto que trate a coluna SQL `financial_transactions.project_id` como “Projeto” no sentido financeiro novo; o risco de confusão é **nomenclatura** (nome da coluna vs significado).

## 3. Resultados das queries

Preenchimento (ativos, `created_at >= now() - 12 months`):

| total | pct_centro_custo (`project_id`) | pct_categoria | pct_projeto (`financial_project_id`) | pct_contrato |
|------:|--------------------------------:|--------------:|-------------------------------------:|-------------:|
| 4 | 0.0 | 0.0 | 0.0 | 0.0 |

Nullability das colunas (confirmado):

| column_name | is_nullable | data_type |
|-------------|-------------|-----------|
| category_id | YES | integer |
| contract_id | YES | integer |
| financial_project_id | YES | integer |
| project_id | YES | integer |
| supplier_id | YES | integer |
| employee_id | YES | integer |

## 4. Achados

| # | Severidade | Achado | Arquivo:linha | Impacto |
|---|-----------|--------|---------------|---------|
| 1 | ALTO | 0% de classificação nas dimensões nos últimos 12 meses (base atual) | query R6; lançamentos do PayrollEngine sem CC/categoria | Balancete/Demonstrativo/orçamento sem eixo preenchido |
| 2 | MÉDIO | Coluna SQL `project_id` significa centro de custo — nomenclatura legada | `FinancialTransactionsTable.kt:18`; V28 | Risco de leitura errada em SQL ad-hoc / novos devs |
| 3 | MÉDIO | `financial_project_id` e `contract_id` existem mas não alimentam Livro Diário/Balancete/Demonstrativo principais | `ReportsExporter.kt` | Dimensões “mortas” para TCESP clássico |
| 4 | MÉDIO | Folha (`PayrollEngine` / import) não preenche CC/categoria por padrão no Engine; import aceita CC/categoria no confirm mas Engine não | `PayrollEngine.kt:49–59`; `PayrollImportService.confirm` | Folha automática nasce sem classificação |
| 5 | BAIXO | Form e DB alinhados em opcionalidade — sem enforcement | `TransactionValidator.kt`; schema | Classificação permanece voluntária |

## 5. Incertezas

- Base local mínima (4 txs de folha); percentuais de produção podem ser outros.
- Não foi auditado se a UI de Orçamento/Projetos força preenchimento em outros fluxos além de Movimentações.
- “Livro Diário não usa dimensão” refere-se às colunas do exporter; eventuais filtros pré-export no ViewModel não mudam o fato de as linhas exportadas não trazerem CC/categoria.
