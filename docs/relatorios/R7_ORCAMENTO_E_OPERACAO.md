# R7 — Orçamento e operação (folha, topologia, observabilidade)

## 1. Resumo executivo

Estorno **infla** o realizado orçamentário (EXPENSE 1.000 + REVERSAL 1.000 = **2.000** na mesma rubrica) e no Demonstrativo entra como **receita**. Folha do Engine nasce sem CC/categoria; import XLSX exige categoria escolhida pelo operador. Inativar funcionário não cancela PENDING futuros. Duas instâncias do app no mesmo Postgres são permitidas; API em `0.0.0.0:8080` sem TLS, JWT 24h. Engines no boot descartam o retorno e não deixam log/tela de verificação.

## 2. Estado atual

### 2.1 Orçamento realizado × estorno (R7.1)

`BudgetItemRepository.sumRealized` (corpo):

```124:136:src/main/kotlin/br/com/sisgfin/budget/BudgetItemRepository.kt
    fun sumRealized(costCenterId: Int, categoryId: Int, year: Int): Money = transaction {
        val sumExpr = FinancialTransactionsTable.amount.sum()
        FinancialTransactionsTable
            .select(sumExpr)
            .where {
                (FinancialTransactionsTable.costCenterId eq costCenterId) and
                (FinancialTransactionsTable.categoryId eq categoryId) and
                (FinancialTransactionsTable.status eq TransactionStatus.PAID.name) and
                (FinancialTransactionsTable.isActive eq true) and
                (FinancialTransactionsTable.paymentDate.year() eq year)
            }
            .firstOrNull()?.get(sumExpr)?.toMoney() ?: Money.ZERO
    }
```

`TransactionRepository.sumRealizedByProject` (corpo):

```483:493:src/main/kotlin/br/com/sisgfin/financial/transactions/TransactionRepository.kt
    fun sumRealizedByProject(projectId: Int): Money = transaction {
        val sumExpr = FinancialTransactionsTable.paidAmount.sum()
        // WHERE projectId + status PAID + is_active — sem filtro de type
        ...
    }
```

- **Filtra por `type`?** Não. Qualquer PAID ativo com o CC×categoria (ou projeto) entra — inclusive `REVERSAL`.
- Estorno copia `costCenterId` e `categoryId` do original, nasce `PAID` com `amount` positivo (`TransactionService.reverseTransaction`). Original permanece PAID.
- **Resposta:** despesa 1.000 paga e estornada → realizado na rubrica = **2.000**.

**Balancete:** usa `sumRealized` / `sumRealizedMonth` → mesmo efeito (estorno **aumenta** realizado).

**Demonstrativo** (`ReportsViewModel`): `REVERSAL` e `ADJUSTMENT` são classificados como **receita**; `EXPENSE` como despesa. Neto da categoria pode zerar (income 1000 − expense 1000), mas a despesa **não é abatida**.

Teste documentando o comportamento atual (sem correção): `BudgetRealizedReversalBehaviorTest` — assertivas `realizado=2000` e Demonstrativo `income=1000` / `expense=1000`.

### 2.2 Classificação da folha (R7.2)

`PayrollEngine.generateForMonth` completo: cria `EXPENSE`/`PENDING` com `accountId` (primeira conta ativa), `employeeId`, `amount=salary` — **sem** `costCenterId` / `categoryId`.

Não há constante, seed ou preferência que defina CC/categoria padrão para o Engine. Seed V10 tem rubricas de RH, mas sem vínculo automático.

Import: `PayrollImportViewModel` — operador escolhe **categoria (obrigatória)** e **centro de custo (opcional)** a cada importação; defaults do state = `null`.

`cancelPendingPayrollForMonth(employeeId, month)`: cancela por **funcionário** + `dueDate` em `[mês, mês+2)` (mês de referência **e** mês seguinte). Cobre lançamentos do Engine do mês seguinte já criados no boot. Não cancela folha de outros funcionários.

**Sem import XLSX no mês — Engine entra no Balancete?** **Não.** Motivos: (1) Engine gera `PENDING` e Balancete só soma `PAID`; (2) mesmo pagos, CC/categoria null não batem nas linhas de orçamento.

### 2.3 Funcionário inativo (R7.3)

1. `generateForMonth` usa `getAllActive()`; `generateForEmployee` checa `employee.active` — **sim**, filtra ativos.
2. Análogo RN-02 para `employeeId`: **não existe**. Só `validateSupplier`.
3. `EmployeeService.toggleActive` na inativação: apenas `active=false`; **não** cancela PENDING futuros — continuam pagáveis.
4. Campo de data de desligamento: **não existe** em `employees`.

### 2.4 Topologia (R7.4)

1. `DbConfigStore`: arquivo `~/.sisgfin/db.properties` (`user.home`).
2. Nada impede duas instâncias no mesmo Postgres.
3. Sem file lock / PID / single-instance no boot.
4. Ktor: `embeddedServer(Netty, port = 8080)` → bind default **`0.0.0.0`**; sem TLS; JWT expira em **24 horas**; secret hardcoded; CORS `anyHost()`.

### 2.5 Observabilidade das engines (R7.5)

1. Sem log persistido de execução das engines (arquivo/tabela).
2. `generateForMonth` retorna `List<PayrollGenerationResult>`; no `Main.kt` o retorno é **descartado** dentro de `runCatching` sem log.
3. Sem tela de verificação mensal da folha; único feedback é snackbar ao salvar/reativar funcionário (`lastPayrollResult`).

### Pendência herdada de C1.4

`TransactionStateMachine`: `PAID → emptySet()` — **não** permite cancelar transferência já liquidada. Estorno de TRANSFER agora é bloqueado (C1.4). **Não existe hoje forma correta de reverter uma transferência PAID** (par). Pendência separada.

## 3. Resultados das queries

Sem queries SQL novas obrigatórias neste R7. Contexto da base (R1–R6): 0 estornos; 4 despesas PENDING de folha sem classificação.

**Teste R7.1 (comportamento atual):**

| Cenário | Resultado assertado |
|---------|---------------------|
| EXPENSE 1000 + REVERSAL 1000 mesma rubrica | realizado = **2000.00** |
| Demonstrativo após o par | expense=1000, income=1000, balance=0 |

## 4. Achados

| # | Severidade | Achado | Arquivo:linha | Impacto |
|---|-----------|--------|---------------|---------|
| 1 | CRÍTICO | Estorno dobra o realizado orçamentário (soma PAID sem nettar tipo) | `BudgetItemRepository.kt:124–136` | Balancete/orçamento incorretos pós-estorno |
| 2 | ALTO | Demonstrativo classifica REVERSAL como receita | `ReportsViewModel.kt:145–157` | Relatório TCESP distorce natureza da operação |
| 3 | ALTO | Engine de folha sem CC/categoria | `PayrollEngine.kt:50–59` | Folha automática fora do Balancete mesmo se paga |
| 4 | MÉDIO | Inativar funcionário deixa PENDING futuros pagáveis | `EmployeeService.kt:33–40` | Pagamentos a desligado possíveis |
| 5 | ALTO | Sem single-instance; API em 0.0.0.0 sem TLS | `KtorServer.kt`; `Main.kt` | Amplifica risco de race R4 e exposição de API |
| 6 | MÉDIO | Retorno das engines descartado; sem tela/log de geração | `Main.kt:169–177` | Falha silenciosa de folha mensal |
| 7 | ALTO | Transferência PAID sem caminho de reversão (estorno bloqueado + cancelamento impossível) | `TransactionStateMachine.kt:35`; C1.4 | Operação irreversível incorreta se erro em transferência liquidada |

## 5. Incertezas

- `sumRealizedByProject` usa `paidAmount` (não `amount`) e estorno não copia `projectId` — impacto em projetos financeiros pós-estorno não medido com dados reais.
- Não foi exercitado Balancete end-to-end com UI após estorno (apenas código + teste de fórmula).
- Comportamento de `sumRealizedMonth` idêntico a `sumRealized` quanto a tipo — confirmado no código, não re-testado isoladamente.
