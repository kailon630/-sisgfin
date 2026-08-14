# T-16: Auditoria da Suíte de Testes

Data: 2026-08-14 | Total: 218 testes (213 base + 5 adicionados no BLOCO 1)

## Categorias

- **REAL** — instancia ou chama código de `src/main/` e verifica o resultado
- **PURO** — testa função pura de `src/main/` (AccountBalanceFormula, validators, parsers etc.) — conta como real
- **ESTADO** — verifica apenas enum/state machine/constante, sem lógica de negócio
- **FALSO** — implementa a lógica dentro do próprio teste, ou o nome promete o que o corpo não testa

---

## 1. Tabela completa

### BudgetRealizedReversalBehaviorTest.kt (7 testes)

| Teste | Categoria | Observação |
|---|---|---|
| EXPENSE 1000 PAID estornado integralmente resulta em realizado zero | PURO | Espelha `BudgetItemRepository.sumRealized` com função local |
| Demonstrativo REVERSAL de EXPENSE reduz despesa nao conta como receita | PURO | Espelha lógica de classificação do Demonstrativo |
| EXPENSE 1000 PAID sem estorno resulta em realizado 1000 | PURO | Função local idêntica à de produção |
| EXPENSE 1000 PAID com REVERSAL parcial 600 resulta em realizado 400 | PURO | Função local idêntica à de produção |
| INCOME PAID e seu REVERSAL nao afetam realizado de despesa | PURO | Função local idêntica à de produção |
| REVERSAL orfao sem reversed_type nao subtrai do realizado | PURO | Função local idêntica à de produção |
| rubrica sem lancamento tem realizado zero | PURO | Trivial |

> **Nota:** os testes espelham `sumRealized` localmente em vez de importar `BudgetItemRepository`. São PURO e não REAL porque não chamam o código de produção diretamente. Se `sumRealized` mudar sem atualizar o espelho, os testes passarão em falso.

### DocumentValidatorTest.kt (18 testes)

| Teste | Categoria | Observação |
|---|---|---|
| CPF valido sem mascara | PURO | Chama `DocumentValidator.isValidCpf()` |
| CPF valido com mascara | PURO | |
| CPF invalido digito verificador errado | PURO | |
| CPF invalido todos digitos iguais | PURO | |
| CPF invalido tamanho errado | PURO | |
| CNPJ valido sem mascara | PURO | Chama `DocumentValidator.isValidCnpj()` |
| CNPJ valido com mascara | PURO | |
| CNPJ invalido digito verificador errado | PURO | |
| CNPJ invalido todos digitos iguais | PURO | |
| CNPJ invalido tamanho errado | PURO | |
| validate nao lanca para CPF valido | PURO | Chama `DocumentValidator.validate()` |
| validate nao lanca para CNPJ valido | PURO | |
| validate lanca para CPF invalido | PURO | |
| validate lanca para CNPJ invalido | PURO | |
| validate lanca para tamanho errado | PURO | |
| normalize remove mascara CPF | PURO | Chama `DocumentValidator.normalize()` |
| normalize remove mascara CNPJ | PURO | |
| normalize ja sem mascara retorna igual | PURO | |

### MoneyTest.kt (6 testes)

| Teste | Categoria | Observação |
|---|---|---|
| test arithmetic operations | PURO | Chama `Money.+`, `-`, `*`, `/` |
| test precision and rounding | PURO | |
| test percentage | PURO | |
| test formatting | PURO | Chama `MoneyFormatter.format/formatAccounting` |
| test compact formatting | PURO | |
| test sum extension | PURO | |

### EncargosNoSaldoTest.kt (4 testes)

| Teste | Categoria | Observação |
|---|---|---|
| Q5a titulo PAID baixa unica - calculateBalance usa amount nao paidAmount - juros invisiveis | PURO | Chama `AccountBalanceFormula.compute()` |
| Q5b statement mostra paidAmount 1050 mas calculateBalance ve amount 1000 - divergencia 50 | PURO | Opera sobre `Money` diretamente, não `AccountBalanceFormula` |
| Q5c titulo PARTIAL - calculateBalance usa paidAmount que inclui juros - encargos visiveis | PURO | Chama `AccountBalanceFormula.compute()` |
| Q5d assimetria PARTIAL-PAID - juros da primeira baixa somem ao quitar status | PURO | Chama `AccountBalanceFormula.compute()` duas vezes |

### InstallmentCalculatorTest.kt (15 testes)

| Teste | Categoria | Observação |
|---|---|---|
| RN-17 totalInstallments 1 nao deve gerar filhos | FALSO | Corpo é `assertEquals(1, 1)` — não testa nada |
| RN-17 slice calculado corretamente para valor divisivel | PURO | Usa helper local `sliceAndLast()` — espelha `create()` |
| RN-17 slice calculado corretamente para 12 parcelas valor divisivel | PURO | |
| RN-18 100 reais em 3 parcelas - slice 33-33 e ultima 33-34 | PURO | |
| RN-18 10 reais em 3 parcelas - slice 3-33 e ultima 3-34 | PURO | |
| RN-18 100 reais em 12 parcelas - soma reconstruida bate no total | PURO | |
| RN-18 soma de todas as parcelas sempre iguala o total | PURO | |
| RN-18 valor 0-03 em 2 parcelas - slice piso e ultima absorve diferenca | PURO | |
| RN-04 saldo inicial mais receita menos despesa | FALSO | Opera sobre `Money` diretamente, não `AccountBalanceFormula` |
| RN-04 saldo sem lancamentos retorna saldo inicial | FALSO | Opera sobre `Money` diretamente |
| RN-04 saldo pode ser negativo quando despesas superam receitas | FALSO | Opera sobre `Money` diretamente |
| RN-04 saldo zero quando receitas igualam despesas mais saldo inicial | FALSO | Opera sobre `Money` diretamente |
| RN-19 PENDING pode ser cancelado pela maquina de estados | ESTADO | Chama `TransactionStateMachine.allowsCancel()` |
| RN-19 PAID nao e cancelavel - allowsCancel retorna false | ESTADO | |
| RN-19 CANCELED nao e cancelavel - allowsCancel retorna false | ESTADO | |

### PartialBalanceTest.kt (11 testes)

| Teste | Categoria | Observação |
|---|---|---|
| T1 expense partial paidAmount 400 reduz saldo em 400 | PURO | Chama `AccountBalanceFormula.compute()` |
| T2 income partial paidAmount 400 aumenta saldo em 400 | PURO | |
| T3 expense paid amount 1000 reduz saldo em 1000 | PURO | |
| T4 income paid amount 1000 aumenta saldo em 1000 | PURO | |
| T5 expense paid e reversal credit restauram saldo original | PURO | |
| T6a estado PARTIAL paidAmount 400 impacta saldo em -400 | PURO | |
| T6b estado PAID apos completar pagamento impacta saldo em -1000 sem double counting | PURO | |
| T6c verificacao explicita de ausencia de double counting | PURO | |
| T7 expense partial 400 e expense paid 1000 na mesma conta impactam -1400 | PURO | |
| T8 formula de openingBalance com PARTIAL antes do periodo tem mesma semantica | PURO | |
| cenario completo saldo nao alterado por PENDING depois -400 por PARTIAL depois -1000 por PAID | PURO | |

### PartialPaymentAccumulationTest.kt (9 testes)

| Teste | Categoria | Observação |
|---|---|---|
| T1 duas baixas 300 mais 700 produzem PAID com paidAmount acumulado de 1000 | PURO | Espelha `TransactionService.recordPayment` com função local |
| T2 duas baixas 300 mais 300 produzem PARTIAL com paidAmount acumulado de 600 | PURO | |
| T3 segunda baixa de 800 com saldo devedor de 700 e rejeitada com mensagem de saldo | PURO | |
| T4 quitacao com encargos - PAID principalPaid 1000 paidAmount 1050 saldo 1050 | PURO | |
| T5 baixa integral 1000 produz PAID sem regressao | PURO | |
| T6 tres baixas 400 mais 400 mais 200 produzem PAID com paidAmount de 1000 | PURO | |
| T7 saldo com duas baixas parciais acumuladas reflete total correto | PURO | Chama `AccountBalanceFormula.compute()` |
| T8a baixa de valor zero e rejeitada | PURO | |
| T8b baixa de valor negativo e rejeitada | PURO | |

> **Nota:** `applyPayment()` é um espelho local de `TransactionService.recordPayment()`. Mudanças no serviço sem atualizar o espelho passam despercebidas — mesmo problema de BudgetRealizedReversalBehaviorTest.

### TerminalImmutabilityTest.kt (7 testes)

| Teste | Categoria | Observação |
|---|---|---|
| C2 update de amount em PAID lanca excecao | FALSO | `assertFinancialImmutability()` implementa a guarda localmente — não chama `TransactionService.update()` |
| C2 update de accountId em PAID lanca excecao | FALSO | |
| C2 update de paymentDate em PAID lanca excecao | FALSO | |
| C2 update de notes em PAID e permitido | FALSO | |
| C2 update de categoryId em PAID e permitido | FALSO | |
| C2 update de amount em PENDING e permitido pela guarda de terminalidade | FALSO | |
| C2 PUT API usa o mesmo servico — guarda nao depende da UI | FALSO | Comentário promete que a guarda está no serviço; corpo usa `assertFinancialImmutability()` local |

### TransactionPanelStateTest.kt (9 testes)

| Teste | Categoria | Observação |
|---|---|---|
| openNewExpense - selectedItem tem id zero | REAL | Instancia `TransactionsViewModel` via mockk e chama `openNewExpense()` |
| openNewExpense - selectedItem tem tipo EXPENSE | REAL | |
| openNewExpense - selectedItem tem status PENDING | REAL | |
| openNewExpense - selectedItem tem description vazia | REAL | |
| openNewExpense - selectedItem tem amount de centavo | REAL | |
| openNewExpense - selectedItem tem accountId zero | REAL | |
| openNewExpense - selectedItem tem installmentTotal nulo | REAL | |
| openNewExpense - selectedItem tem paidAmount nulo | REAL | |
| openNewExpense - selectedItem tem paymentDate nulo | REAL | |

### TransactionQueryTest.kt (12 testes)

| Teste | Categoria | Observação |
|---|---|---|
| defaults — tipos e status vazios, eixo DUE, onlyActive true | PURO | Testa `TransactionQuery()` factory |
| aPagar() filtra somente EXPENSE | PURO | |
| aPagar() equivale a filterActionRequired filtrado por EXPENSE | PURO | |
| aPagar() nao inclui PAID nem CANCELED | PURO | |
| aReceber() filtra somente INCOME | PURO | |
| aReceber() usa os mesmos status que aPagar() | PURO | |
| aPagar e aReceber tipos disjuntos | PURO | |
| extrato() equivale a findStatementEntries com eixo PAYMENT | PURO | |
| extrato() nao inclui status em aberto | PURO | |
| extrato() eixo PAYMENT implica paymentDate como criterio de data | PURO | |
| query com search e costCenterId | PURO | |
| query com onlyActive false inclui inativos | PURO | |

### TransactionValidatorTest.kt (17 testes)

| Teste | Categoria | Observação |
|---|---|---|
| RN-16 pagamento na mesma data de emissao e valido | PURO | Chama `TransactionValidator.validate()` |
| RN-16 pagamento apos emissao e valido | PURO | |
| RN-16 pagamento anterior a emissao e invalido em PAID | PURO | |
| RN-16 pagamento anterior a emissao e invalido em PARTIAL | PURO | |
| RN-16 validatePayment lanca quando pagamento anterior a emissao | PURO | Chama `TransactionValidator.validatePayment()` |
| RN-16 validatePayment aceita pagamento na data de emissao | PURO | |
| RN-08 dueDate dentro do periodo retorna null | PURO | Chama `TransactionValidator.checkProjectPeriod()` |
| RN-08 dueDate anterior ao inicio do projeto retorna aviso | PURO | |
| RN-08 dueDate posterior ao fim do projeto retorna aviso | PURO | |
| RN-08 dueDate igual ao inicio retorna null | PURO | |
| RN-08 dueDate igual ao fim retorna null | PURO | |
| RN-08 sem periodo definido retorna null | PURO | |
| RN-08 com apenas startDate definido fora do range retorna aviso | PURO | |
| descricao em branco gera erro | PURO | |
| valor zero gera erro | PURO | |
| PAID sem paymentDate gera erro | PURO | |
| PARTIAL com paidAmount maior que total gera erro | PURO | |

### TransferAndReversalTest.kt (26 testes)

| Teste | Categoria | Observação |
|---|---|---|
| RN-20 conta de origem igual ao destino deve ser rejeitado | FALSO | Executa o `if` diretamente no corpo do teste |
| RN-20 valor zero deve ser rejeitado | FALSO | Executa o `if` diretamente no corpo do teste |
| RN-20 valor negativo deve ser rejeitado | FALSO | Executa o `if` diretamente no corpo do teste |
| RN-20 valor positivo e contas distintas passam validacao | FALSO | Verifica valores locais sem chamar `createTransfer()` |
| RN-21 transferencia PENDING permite cancelamento | ESTADO | Chama só `TransactionStateMachine.allowsCancel()` |
| RN-21 transferencia PAID nao permite cancelamento em cascata | ESTADO | Não toca `TransactionService.cancel()` |
| RN-21 transferencia CANCELED nao repete cascata | ESTADO | |
| RN-22 justificativa em branco deve ser rejeitada | FALSO | Implementa o `if (justification.isBlank()) throw...` no corpo |
| RN-22 justificativa vazia deve ser rejeitada | FALSO | |
| RN-22 justificativa valida passa validacao | FALSO | Chama `assertFalse(justification.isBlank())` — não toca `reverseTransaction()` |
| RN-23 estorno de PENDING lanca excecao | PURO | Chama `ReversalEligibility.assertCanReverse()` |
| RN-23 estorno de CANCELED lanca excecao | PURO | |
| RN-23 estorno de PAID passa validacao de status | PURO | |
| RN-23 estorno de outro REVERSAL lanca excecao | PURO | |
| C1-1 EXPENSE 1000 pago e estornado restaura saldo inicial | PURO | Chama `AccountBalanceFormula.compute()` |
| C1-2 INCOME 1000 recebido e estornado restaura saldo inicial | PURO | |
| C1-3 ADJUSTMENT 500 e estornado restaura saldo inicial | PURO | |
| C1-4 TRANSFER perna de saida nao pode ser estornada | PURO | Chama `ReversalEligibility.assertCanReverse()` |
| C1-5 TRANSFER perna de entrada nao pode ser estornada | PURO | |
| C1-6 estorno de estorno lanca excecao | PURO | |
| C1-8 openingBalance com estorno de INCOME mesma semantica que calculateBalance | PURO | |
| C1-BUG legado somar REVERSAL sempre positivo infla saldo apos estorno de INCOME | PURO | Documenta comportamento legado — não é regressão |
| RN-04 saldo com transferencia saida reduz conta origem | PURO | Chama `AccountBalanceFormula.compute()` |
| RN-04 saldo com transferencia entrada aumenta conta destino | PURO | |
| RN-04 saldo com estorno recupera valor da despesa | PURO | |
| RN-04 saldo completo com todos os tipos | PURO | |

### OverdueEngineTest.kt (6 testes)

| Teste | Categoria | Observação |
|---|---|---|
| T1 PENDING dueDate ontem deve marcar OVERDUE | PURO | Chama `OverdueEngine.shouldMarkOverdue()` |
| T2 PENDING dueDate amanha nao deve marcar OVERDUE | PURO | |
| T3 PENDING dueDate hoje nao deve marcar OVERDUE | PURO | |
| T4 PAID dueDate ontem nao deve marcar OVERDUE | PURO | |
| T5 CANCELED dueDate ontem nao deve marcar OVERDUE | PURO | |
| T6 PENDING isActive false dueDate ontem nao deve marcar OVERDUE | PURO | |

### TransactionWorkflowTest.kt (3 testes)

| Teste | Categoria | Observação |
|---|---|---|
| test state machine transitions | ESTADO | Chama `TransactionStateMachine.assertTransition()` |
| test overdue engine | PURO | Chama `OverdueEngine.shouldMarkOverdue()` e `applyOverdueStatus()` |
| test partial payment status logic in service would be here but we test the math | FALSO | Corpo é aritmética de `Money >= Money` — não toca `recordPayment()`. Nome admite que não testa o serviço |

### OfxParserTest.kt (12 testes)

| Teste | Categoria | Observação |
|---|---|---|
| DEP positivo e parseado como entrada com isInflow true | REAL | Instancia `OfxParser` e chama `parse()` |
| DEBIT negativo e parseado como saida com isInflow false | REAL | |
| XFER positivo e tratado como entrada | REAL | |
| XFER negativo e tratado como saida | REAL | |
| encoding windows-1252 preserva acentuacao em MEMO | REAL | Escreve arquivo OFX em disco e parseia |
| FITID duplicado no mesmo arquivo e incluido duas vezes pelo parser | REAL | |
| transacao com FITID vazio e ignorada silenciosamente | REAL | |
| transacao com TRNAMT ausente e ignorada silenciosamente | REAL | |
| metadados bankId acctId e periodo sao parseados corretamente | REAL | |
| tag com fechamento XML opcional e parseada corretamente | REAL | |
| arquivo OFX real janeiro 2026 parseia 1241 transacoes | REAL | Carrega arquivo de 337 KB do disco |
| CHECKNUM ausente resulta em null | REAL | |

### CounterpartyResolverTest.kt (7 testes)

| Teste | Categoria | Observação |
|---|---|---|
| nameFor retorna nome do fornecedor quando supplierId presente | PURO | Chama `CounterpartyMap.nameFor()` |
| nameFor retorna nome do funcionario quando apenas employeeId presente | PURO | |
| nameFor prefere supplierId quando ambos presentes | PURO | |
| nameFor retorna null quando ids ausentes | PURO | |
| nameFor retorna null quando id nao encontrado no mapa | PURO | |
| EMPTY retorna null para qualquer transacao | PURO | |
| mapa com multiplos fornecedores resolve corretamente cada id | PURO | |

### PayablesUiStateTest.kt (7 testes)

| Teste | Categoria | Observação |
|---|---|---|
| ALL mostra todos os itens | PURO | Chama `PayablesUiState.items` via `tileFilter` |
| OVERDUE mostra apenas status OVERDUE | PURO | |
| TODAY mostra apenas dueDate igual a hoje | PURO | |
| THIS_WEEK inclui hoje e dias ate domingo | PURO | |
| summary total inclui todos os itens carregados | PURO | Opera sobre `PayablesSummary` — construtora direta |
| EMPTY summary tem zeros | PURO | |
| estado inicial tem tileFilter ALL e lista vazia | PURO | |

### PayablesViewModelWriteTest.kt (4 testes)

| Teste | Categoria | Observação |
|---|---|---|
| P0-1 recordPayment lanca excecao - errorMessage preenchido | REAL | Instancia `PayablesViewModel` com mocks e chama `markAsPaidFull()` |
| P0-2 cancel lanca excecao - errorMessage preenchido | REAL | Chama `cancelTransaction()` |
| P0-3 duplicate lanca excecao - errorMessage preenchido | REAL | Chama `duplicateTransaction()` |
| P0-6 PARTIAL 1000 com 300 pagos - markAsPaidFull envia saldo restante 700 | REAL | Verifica argumento capturado via `slot<Money>()` |

### PayrollXlsxParserTest.kt (8 testes)

| Teste | Categoria | Observação |
|---|---|---|
| arquivo real folha 06-2026 parseia 80 funcionarios | REAL | Chama `PayrollXlsxParser.parse()` em arquivo real |
| adriana bispo martins - cpf adiantamento e liquido corretos | REAL | |
| fernanda grandchamp - adiantamento zero e liquido de ferias | REAL | |
| arquivo real adiantamento 07-2026 parseia funcionarios corretamente | REAL | |
| adiantamento - alex costa safra - valor correto | REAL | |
| adiantamento anomalo e zerado com warning | REAL | |
| dois blocos liquido acumulam valor com warning de ferias | REAL | |
| funcionario sem cpf valido gera warning | REAL | |

### RecurrenceEngineTest.kt (17 testes)

| Teste | Categoria | Observação |
|---|---|---|
| mensal dia 15 gera tres datas de jan a mar | PURO | Chama `RecurrenceDateCalculator.nextDueDates()` |
| mensal gera exatamente uma data quando from e horizon no mesmo mes | PURO | |
| mensal nao gera nada quando dia do mes ficou antes de from | PURO | |
| dia 31 em fevereiro ajusta para ultimo dia do mes | PURO | |
| dia 31 em fevereiro bissexto ajusta para 29 | PURO | |
| dia 31 em abril ajusta para 30 | PURO | |
| dia 30 em fevereiro ajusta para ultimo dia do mes | PURO | |
| startsAt no futuro e respeitado como ponto inicial | PURO | |
| endsAt e respeitado - nao gera alem do fim | PURO | |
| janela zero nao gera datas alem de hoje | PURO | |
| bimestral dia 10 gera jan mar mai em janela de 6 meses | PURO | |
| trimestral dia 1 gera jan abr jul em 9 meses | PURO | |
| semestral gera dois vencimentos em 12 meses | PURO | |
| anual gera apenas uma data em 12 meses | PURO | |
| semanal gera 5 datas em 29 dias | PURO | |
| quinzenal gera datas a cada 14 dias | PURO | |
| quinzenal respeita endsAt | PURO | |

### BuildTcespDescTest.kt (8 testes)

| Teste | Categoria | Observação |
|---|---|---|
| EXPENSE com creditorName retorna prefixo PAGO A | PURO | Chama `buildTcespDesc()` |
| INCOME com creditorName retorna prefixo RECEBIDO DE | PURO | |
| REVERSAL com creditorName retorna prefixo RECEBIDO DE | PURO | |
| EXPENSE com creditorName nulo retorna CREDOR NAO IDENTIFICADO | PURO | |
| INCOME com creditorName nulo retorna CREDOR NAO IDENTIFICADO | PURO | |
| creditorName minusculo e convertido para maiusculo | PURO | |
| documentType e documentNumber ambos presentes aparecem no sufixo | PURO | |
| apenas documentNumber presente usa prefixo CF DOC | PURO | |

### TransferContainmentTest.kt (5 testes — adicionados BLOCO 1)

| Teste | Categoria | Observação |
|---|---|---|
| duplicate de TRANSFER lanca IllegalArgumentException | REAL | Instancia `TransactionService` real com mocks |
| duplicate de EXPENSE comum nao lanca excecao | REAL | Verifica retorno via `repo.insert` capturado |
| cancel com irma PENDING cancela as duas pernas | REAL | Verifica `deactivate` chamado 2×via mockk |
| cancel com irma PAID lanca IllegalStateException sem desativar nenhuma perna | REAL | Verifica `deactivate` chamado 0× |
| cancel com irma CANCELED lanca IllegalStateException sem desativar nenhuma perna | REAL | Verifica `deactivate` chamado 0× |

---

## 2. Contagem por categoria

| Categoria | Quantidade | % |
|---|---|---|
| REAL | 38 | 17,4% |
| PURO | 155 | 71,1% |
| ESTADO | 6 | 2,8% |
| FALSO | 19 | 8,7% |
| **Total** | **218** | **100%** |

> **Número que mais surpreende:** apenas **38 testes (17,4%)** exercitam código de produção via instância real do objeto. Outros 155 testam funções puras (que também são válidos), mas **19 (8,7%) são FALSO** — incluindo classes inteiras que ostentam nomes de RN de negócio mas testam código interno ao próprio teste.

---

## 3. Lista dos FALSO

| Teste | O que deveria testar |
|---|---|
| `RN-20 conta de origem igual ao destino deve ser rejeitado` | `createTransfer()` com sourceId == destId deve lançar |
| `RN-20 valor zero deve ser rejeitado` | `createTransfer()` com amount=0 deve lançar |
| `RN-20 valor negativo deve ser rejeitado` | `createTransfer()` com amount negativo deve lançar |
| `RN-20 valor positivo e contas distintas passam validacao` | `createTransfer()` com dados válidos deve criar o par e retornar dois IDs |
| `RN-22 justificativa em branco deve ser rejeitada` | `reverseTransaction()` com justification blank deve lançar |
| `RN-22 justificativa vazia deve ser rejeitada` | `reverseTransaction()` com justification vazia deve lançar |
| `RN-22 justificativa valida passa validacao` | `reverseTransaction()` com justification válida deve criar estorno |
| `RN-17 totalInstallments 1 nao deve gerar filhos` | `create()` com installmentTotal=1 não deve chamar `generateInstallments` |
| `RN-04 saldo inicial mais receita menos despesa` | `calculateBalance()` via repositório, não aritmética de `Money` |
| `RN-04 saldo sem lancamentos retorna saldo inicial` | Idem |
| `RN-04 saldo pode ser negativo quando despesas superam receitas` | Idem |
| `RN-04 saldo zero quando receitas igualam despesas mais saldo inicial` | Idem |
| `C2 update de amount em PAID lanca excecao` | `TransactionService.update()` com campo financeiro alterado em PAID |
| `C2 update de accountId em PAID lanca excecao` | Idem |
| `C2 update de paymentDate em PAID lanca excecao` | Idem |
| `C2 update de notes em PAID e permitido` | `TransactionService.update()` com campo não-financeiro em PAID deve passar |
| `C2 update de categoryId em PAID e permitido` | Idem |
| `C2 update de amount em PENDING e permitido pela guarda de terminalidade` | `TransactionService.update()` em PENDING deve aceitar |
| `C2 PUT API usa o mesmo servico — guarda nao depende da UI` | `TransactionService.update()` acionado por chamada REST — não foi testado |
| `test partial payment status logic in service would be here but we test the math` | `TransactionService.recordPayment()` com pagamento parcial |

---

## 4. Testes cujo nome não corresponde ao corpo

| Arquivo | Teste | Discrepância |
|---|---|---|
| `InstallmentCalculatorTest` | `RN-04 saldo inicial mais receita menos despesa` | Testa `Money +/-` direto, não a fórmula de saldo |
| `InstallmentCalculatorTest` | `RN-04 saldo sem lancamentos retorna saldo inicial` | Idem |
| `InstallmentCalculatorTest` | `RN-04 saldo pode ser negativo quando despesas superam receitas` | Idem |
| `InstallmentCalculatorTest` | `RN-04 saldo zero quando receitas igualam despesas mais saldo inicial` | Idem |
| `TransferAndReversalTest` | `RN-20 conta de origem igual ao destino deve ser rejeitado` | Não chama `createTransfer()` |
| `TransferAndReversalTest` | `RN-22 justificativa em branco deve ser rejeitada` | Não chama `reverseTransaction()` |
| `TransferAndReversalTest` | `RN-22 justificativa valida passa validacao` | Apenas `assertFalse(isBlank())` |
| `TerminalImmutabilityTest` | `C2 PUT API usa o mesmo servico — guarda nao depende da UI` | Não existe chamada REST; usa função local espelhada |
| `TransactionWorkflowTest` | `test partial payment status logic in service would be here but we test the math` | Nome admite que não testa o serviço — confirma |

---

## 5. Cobertura ausente por área (TransactionService)

| Método | Teste REAL existente |
|---|---|
| `recordPayment` | **NÃO** — `PartialPaymentAccumulationTest` espelha a lógica localmente; nenhum teste chama `service.recordPayment()` |
| `cancel` | **SIM** (adicionado BLOCO 1) — `TransferContainmentTest` cobre cascata PENDING, PAID, CANCELED |
| `createTransfer` | **NÃO** — `TransferAndReversalTest` (RN-20) usa lógica inline |
| `reverseTransaction` | **NÃO** — `TransferAndReversalTest` (RN-22/23) usa `ReversalEligibility` puro ou lógica inline |
| `duplicate` | **SIM** (adicionado BLOCO 1) — TRANSFER lança, EXPENSE retorna ID correto |
| `generateInstallments` | **NÃO** — `InstallmentCalculatorTest` espelha a matemática; nenhum teste verifica que `create()` realmente cria filhos no repositório |
