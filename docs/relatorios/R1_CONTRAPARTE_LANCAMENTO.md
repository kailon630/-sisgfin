# R1 — Contraparte do lançamento (fornecedor × funcionário)

## 1. Resumo executivo

A contraparte na UI é exclusivamente `supplierId` (rótulo FORNECEDOR/CLIENTE). Folha e `PayrollEngine` gravam só `employeeId`, sem `supplierId`. Ao editar, `employeeId` é preservado (não entra no `copy` do formulário e o `update` do repositório não o sobrescreve). Na base atual, **4/4** lançamentos ativos são só-funcionário; o painel de detalhes **não exibe** o nome do funcionário — só o do fornecedor, quando houver.

## 2. Estado atual

### 2.1 Campos do formulário em `TransactionDetailsPanel.kt`

Ordem visual (seções editáveis quando `canEdit`; resumo é só leitura).

| # | Seção | Label UI | Campo `Transaction` | Obrigatório no form | Componente |
|---|-------|----------|---------------------|---------------------|------------|
| — | cabeçalho | badge status / tipo | `status` / `type` (exibição) | — | `TransactionStatusBadge`, `TransactionTypeLabel` |
| — | Resumo | Valor, pago, juros, multa, datas, conta, fornecedor*, CC, projeto, categoria, doc, parcela | vários (somente leitura) | — | `SummaryRow` |
| — | Ações rápidas | Quitar / Cancelar / Duplicar / Editar / Estornar / Comprovante | — | — | `WsButton` / `WsIconButton` |
| 1 | Dados gerais | DESCRIÇÃO | `description` | sim (validator) | `WsTextField` |
| 2 | Dados gerais | VALOR (R$) | `amount` | sim | `WsMoneyField` |
| 3 | Dados gerais | PARCELAS | `installmentTotal` | não | `WsTextField` |
| 4 | Dados gerais | EMISSÃO | `issueDate` | implícito | `WsDateField` |
| 5 | Dados gerais | VENCIMENTO | `dueDate` | implícito | `WsDateField` |
| 6 | Dados gerais | TIPO | `type` | sim | `WsTypeSelector` |
| 7 | Dados gerais | CONTA | `accountId` | sim | chips `WsFilterChip` (≤4 contas) ou `WsSelectField` (`nullable=false`) |
| 8 | Vínculos | CONTRATO (OPCIONAL) | `contractId` | não; só `item.id==0` | `WsSelectField` |
| 9 | Vínculos | CLIENTE / FORNECEDOR | `supplierId` | não (`WsSelectField` default `nullable=true`) | `WsSelectField` |
| 10 | Vínculos | CENTRO DE CUSTO | `costCenterId` | não | `WsSelectField` |
| 11 | Vínculos | PROJETO (opcional) | `projectId` | não | `WsSelectField` `nullable=true` |
| 12 | Vínculos | CATEGORIA | `categoryId` | não | `WsSelectField` |
| 13 | Documento | TIPO (NF, RPA...) | `documentType` | não | `WsTextField` |
| 14 | Documento | NÚMERO | `documentNumber` | não | `WsTextField` |
| 15 | Documento | OBSERVAÇÕES | `notes` | não | `WsTextField` |
| 16 | Recorrência | Switch + INTERVALO + DIA DO MÊS | (template, não coluna direta) | não; só novo | `Switch`, `WsFilterChip`, `WsTextField` |
| — | Linha do tempo | eventos | — | — | `TimelineSection` |

\* No Resumo, “Fornecedor” só aparece se `supplierName != null` (`TransactionDetailsPanel.kt:175`).

Não há campo de formulário para `employeeId`.

### 2.2 Seleção / preservação de `employeeId`

- O formulário **não** permite selecionar/alterar `employeeId`.
- No `onSave`, o `item.copy(...)` sobrescreve `supplierId` e demais campos listados, mas **não** passa `employeeId` — o valor original de `item` permanece (`TransactionDetailsPanel.kt:128–147`).
- `TransactionRepository.update` documenta e implementa: `employeeId` **não** é atualizado no UPDATE geral (`TransactionRepository.kt:218–219`).

Conclusão: editar um lançamento de folha **não** zera `employeeId` com `null`.

### 2.3 `PayrollImportService.confirm()` — resolução do beneficiário

Caminho atual: lookup por CPF no cadastro de **funcionários** no `import()`; no `confirm()` grava apenas `employeeId`. Não há `supplierId`, nem join em `employees.supplier_id`, nem match contra `suppliers.document`.

```76:128:src/main/kotlin/br/com/sisgfin/payroll/PayrollImportService.kt
    fun confirm(
        result: PayrollImportResult,
        accountId: Int,
        categoryId: Int,
        costCenterId: Int?,
        userId: Int
    ): Int {
        val monthLabel = result.referenceMonth.format(monthFmt).uppercase()
        var created = 0

        result.entries.filter { it.employeeFound }.forEach { entry ->
            transactionService.cancelPendingPayrollForMonth(entry.employeeId!!, result.referenceMonth)
            // ...
            transactionService.createFromPayrollImport(
                Transaction(
                    type = TransactionType.EXPENSE,
                    // ...
                    employeeId = entry.employeeId,
                    createdBy = userId
                )
            )
            // ...
        }
        return created
    }
```

Lookup prévio (`import`, linhas 38–55): `employeeRepository.findByCpf(raw.cpf)` → `employeeId = employee?.id`.

### 2.4 `PayrollEngine` — campos preenchidos

`generateForMonth` / `generateForEmployee` criam `Transaction` com: `type=EXPENSE`, `status=PENDING`, `description`, `amount=employee.salary`, `issueDate`, `dueDate`, `accountId` (primeira conta ativa), `employeeId=employee.id`.

`supplierId` **não** é preenchido (fica `null` do default).

```49:59:src/main/kotlin/br/com/sisgfin/employees/PayrollEngine.kt
                    transactionService.create(
                        Transaction(
                            type        = TransactionType.EXPENSE,
                            status      = TransactionStatus.PENDING,
                            description = "Pagamento ${employee.name} — $monthLabel",
                            amount      = employee.salary,
                            issueDate   = LocalDateTime.now(),
                            dueDate     = dueDate.atStartOfDay(),
                            accountId   = defaultAccountId,
                            employeeId  = employee.id
                        )
                    )
```

### 2.5 Validação `supplierId` / RN-02

- `TransactionValidator`: **não** exige `supplierId` para `EXPENSE`. Única menção a fornecedor: TRANSFER não deve ter `supplierId` (`TransactionValidator.kt:56–58`).
- `TransactionService.validateSupplier` (`TransactionService.kt:638–645`): se `supplierId == null`, **retorna sem validar**. RN-02 (fornecedor inativo) só roda quando há `supplierId` preenchido.
- Lançamento só com `employeeId`: RN-02 **não** se aplica.

### 2.6 Fluxo ponta a ponta (beneficiário)

1. **Importação folha:** XLSX → CPF → `employees.document` → `employeeId` na `Transaction`; `supplierId=null`.
2. **PayrollEngine (boot):** mesma gravação só com `employeeId`.
3. **Persistência:** colunas `employee_id` / `supplier_id` independentes e nullable.
4. **UI lista:** descrição costuma conter o nome (“Pagamento KAILON — …”); colunas não mostram contraparte tipada.
5. **UI detalhes:** resolve nome só via `suppliers` + `item.supplierId`. Sem `supplierId`, linha “Fornecedor” some; **não há** resolução de `employeeId` para nome.

Operador **não precisa** cadastrar funcionário como fornecedor para criar/pagar folha. Precisaria apenas se quisesse que o seletor/resumo de fornecedor mostrasse a contraparte.

## 3. Resultados das queries

**Contraparte (lançamentos ativos):**

| total | so_fornecedor | so_funcionario | ambos | nenhum |
|------:|--------------:|---------------:|------:|-------:|
| 4 | 0 | 4 | 0 | 0 |

**Overlap CPF funcionário × documento fornecedor** (coluna de CPF em `employees` = `document`):

| COUNT(*) |
|---------:|
| 0 |

**Detalhe dos 4 lançamentos:** todos `EXPENSE`/`PENDING`, `supplier_id=NULL`, `employee_id=1`, descrição com nome do funcionário; vencimentos 05 e 15 em ago/set 2026 (dois `paymentDays`).

## 4. Achados

| # | Severidade | Achado | Arquivo:linha | Impacto |
|---|-----------|--------|---------------|---------|
| 1 | ALTO | Painel de detalhes não exibe beneficiário quando só há `employeeId` | `TransactionDetailsPanel.kt:95,175` | 4/4 lançamentos ativos sem contraparte tipada na UI (nome só na descrição) |
| 2 | MÉDIO | Formulário só edita `supplierId`; sem seletor de funcionário | `TransactionDetailsPanel.kt:344–350` | Operador não consegue vincular/alterar funcionário pela tela de movimentações |
| 3 | BAIXO | Migração V24 (`employees.supplier_id`) removida na V26; código atual não depende dela | migrações V24/V26; `PayrollImportService.kt` | Modelo legado fornecedor↔funcionário abandonado; fluxo atual é só `employeeId` |
| 4 | MÉDIO | RN-02 não cobre lançamentos de folha (só `employeeId`) | `TransactionService.kt:638–645` | Inatividade de fornecedor irrelevante para folha; não há análogo para funcionário inativo neste ponto |

## 5. Incertezas

- Base local analisada tem volume baixo (4 txs, 1 funcionário, 1 fornecedor); proporções em produção podem diferir.
- Não foi auditado se outras telas (extrato, remessa, PDF) resolvem nome via `employeeId` fora deste painel.
- Não há evidência de lançamentos com `ambos` ou `nenhum` nesta base; comportamento de UI nesses casos foi inferido do código, não observado com dados.
