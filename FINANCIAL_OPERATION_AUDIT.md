# FINANCIAL_OPERATION_AUDIT.md
> Auditoria do ciclo financeiro operacional — SisgFin
> Data: 2026-08-10 | Auditor: Claude Sonnet 4.6 (investigação estática)

---

## 1. Resumo Executivo

O motor transacional do SisgFin está bem estruturado para pré-produção: state machine centralizada, validadores, timeline e auditoria presentes na grande maioria dos fluxos. Nenhuma regra de negócio crítica está ausente em absoluto.

**Riscos que precisam de atenção antes de ir a produção:**

- Pagamento de transferência não é atômico — os dois lados (EXPENSE/INCOME) precisam ser pagos manualmente e de forma independente. Enquanto apenas um lado está PAID, o saldo da conta fica inconsistente.
- `duplicate()` sobre uma transação TRANSFER ou sobre um pai com parcelas pode corromper a lookup de par de transferência ou disparar cancelamento em cascata indevido.
- `ReceivablesViewModel` acessa o repositório diretamente, bypassando `syncOverdueStatuses()`.
- `cancelFutureByRecurrenceTemplate()` faz UPDATE em massa sem timeline/audit por transação.
- `sumPaid()` usa a coluna `amount`, não `paidAmount` — overpagamento (paidAmount > amount, permitido pelo validador) é silenciosamente ignorado no saldo.

O `LedgerService` é um no-op stub (confirmado). O saldo é calculado inteiramente por consultas diretas à tabela `financial_transactions`. Isso é coerente: não há divergência contábil.

---

## 2. Arquitetura Encontrada

### Tabelas de banco

| Tabela | Propósito |
|--------|-----------|
| `financial_transactions` | Motor transacional principal (contas a pagar/receber) |
| `transactions` | Tabela **legada** V1 — vinculada a `accounts`, usada apenas no `LegacyTransactionRepository` |
| `accounts` | Contas legadas V1 — não usadas pelo motor atual |
| `financial_accounts` | Contas financeiras atuais |
| `suppliers` | Fornecedores **e** Clientes (discriminado por `entityType`: FORNECEDOR/CLIENTE/AMBOS) |
| `employees` | Funcionários — campo separado de suppliers |
| `projects` | CostCenters (o objeto Kotlin é `CostCenter`, a tabela é `projects`) |

> **Atenção:** O objeto Kotlin `CostCenter` mapeia para a tabela `projects`. O campo `costCenterId` em `Transaction` aponta para essa tabela. Também existe um campo separado `projectId` em Transaction (para `financial/projects/Project`). São duas entidades distintas com nomes confusos.

### Stack de camadas

```
UI (Compose Desktop)
  TransactionsScreen / TransactionDetailsPanel / ReceivablesScreen
    ↓
  ViewModel
  TransactionsViewModel / ReceivablesViewModel
    ↓
  Service
  TransactionService / FinancialAccountService / SupplierService
    ↓
  Repository
  TransactionRepository / FinancialAccountRepository / SupplierRepository
    ↓
  Exposed SQL → financial_transactions / financial_accounts / suppliers
```

---

## 3. Fluxo de Pagamento (Despesa)

### Mapeamento completo

```
UI: TransactionDetailsPanel
  botão "Quitar" → showPaymentDialog = true
  PaymentDialog → viewModel.recordPayment(id, paymentDate, paidAmount, interest, fine)
    ↓
ViewModel: TransactionsViewModel.recordPayment()
  runOperation { service.recordPayment(...) }
    ↓
Service: TransactionService.recordPayment()
  1. requirePermission(Permission.ConfirmPayment)
  2. repository.findById(id) — busca estado atual
  3. TransactionStateMachine.allowsPayment(existing.status) — valida estado
  4. TransactionValidator.validatePayment(total, paidAmount, paymentDate, issueDate)
  5. TransactionStateMachine.resolveStatusAfterPayment(total, paid) → PAID ou PARTIAL
  6. TransactionStateMachine.assertTransition(from, to)
  7. TransactionValidator.validateForSave(updated, existing)
  8. repository.update(updated) ← WRITE
  9. ledgerService.recordPayment() ← NO-OP (stub)
  10. addTimeline(PAYMENT ou PARTIAL_PAYMENT)
  11. audit(TRANSACTION_PAID ou TRANSACTION_PARTIAL_PAYMENT)
    ↓
Repository: TransactionRepository.update()
  UPDATE financial_transactions SET status, paymentDate, paidAmount, interestAmount, fineAmount
```

### Quem pode ser o favorecido de um pagamento?

**Campo:** `Transaction.supplierId` — referencia a tabela `suppliers`

A tabela `suppliers` tem `entityType`:
- `FORNECEDOR` — fornecedor puro
- `CLIENTE` — cliente puro
- `AMBOS` — fornecedor e cliente

O `TransactionDetailsPanel` filtra o dropdown de "favorecido" por tipo:
- Para `EXPENSE`: mostra apenas `FORNECEDOR` e `AMBOS`
- Para `INCOME`: mostra apenas `CLIENTE` e `AMBOS`

**Employee** tem campo separado `Transaction.employeeId`. Pagamentos de funcionários gerados pelo `PayrollEngine` usam `employeeId`, não `supplierId`. Os dois campos podem coexistir (um pagamento pode ter ambos null — sem favorecido nomeado).

**Não existe limitação artificial que force supplierId em todo pagamento.** É opcional. O validador permite `supplierId = null`. `validateSupplier()` só é chamado se supplierId != null.

**Supplier não está hardcoded** nos fluxos de negócio principais. Está presente como referência opcional.

---

## 4. Fluxo de Recebimento

```
UI: ReceivablesScreen
  ReceivablesViewModel.load()
    ↓
  transactionRepository.findReceivables()
  — busca INCOME com status PENDING/OVERDUE/PARTIAL
  — agrupa por supplierId (cliente)
  — "Sem cliente" quando supplierId == null
    ↓
  Para confirmar recebimento: ainda usa TransactionsScreen/TransactionDetailsPanel
  — mesmo caminho de recordPayment() descrito acima
```

**INCOME**: tipo `TransactionType.INCOME`

- **Origem:** campo livre `description` + `supplierId` (cliente opcional) + `contractId` (opcional)
- **Conta financeira:** `accountId` obrigatório
- **Lançamento:** persiste em `financial_transactions` com `type=INCOME`
- **Liquidação:** `recordPayment()` → `status = PAID`, `paidAmount` preenchido
- **Saldo:** `FinancialAccountService.calculateBalance()` soma `sumPaid(accountId, INCOME)` + outros tipos

**Observação:** `ReceivablesViewModel` busca diretamente do `transactionRepository` sem passar pelo `TransactionService`. Portanto, **não executa `syncOverdueStatuses()`**. Receitas vencidas podem aparecer como PENDING no painel de recebíveis mesmo após a data de vencimento.

---

## 5. Estados

### Definição (TransactionStatus)

| Status | displayName | Terminal |
|--------|-------------|----------|
| DRAFT | Rascunho | Não |
| PENDING | Pendente | Não |
| SCHEDULED | Agendado | Não |
| OVERDUE | Vencido | Não |
| PARTIAL | Parcial | Não |
| PAID | Pago | **Sim** |
| CANCELED | Cancelado | **Sim** |

### Tabela de transições (TransactionStateMachine)

| Estado | Entrada | Saídas permitidas | Operações permitidas |
|--------|---------|-------------------|----------------------|
| DRAFT | `create()` com status=DRAFT | PENDING, CANCELED, SCHEDULED | Edit, Cancel, Duplicate |
| SCHEDULED | `create()` com status=SCHEDULED; RecurrenceEngine | PENDING, CANCELED | Edit, Cancel, Duplicate |
| PENDING | `create()` padrão; SCHEDULED→PENDING; DRAFT→PENDING | PAID, OVERDUE, PARTIAL, CANCELED | Pay, Cancel, Duplicate, Edit |
| OVERDUE | `syncOverdueStatuses()` (PENDING+vencido) | PAID, PARTIAL, CANCELED | Pay, Cancel, Duplicate, Edit |
| PARTIAL | `recordPayment()` quando paidAmount < amount | PAID, CANCELED | Pay (completar), Cancel, Duplicate, Edit |
| PAID | `recordPayment()` quando paidAmount >= amount | ∅ | Reverse (se ADMIN), Duplicate |
| CANCELED | `cancel()` / `deactivate()` | ∅ | Duplicate |

### Operações que DEVERIAM ser impossíveis (confirmadas no código)

- PAID → PENDING: bloqueado por `forbiddenExplicit` + allowedTransitions vazio
- CANCELED → qualquer ativo: bloqueado por allowedTransitions vazio
- PAID → CANCELED: `allowsCancel(PAID)` retorna false
- Estorno de REVERSAL: verificado explicitamente em `reverseTransaction()`
- Duplo estorno: `hasReversal()` verifica antes de criar novo REVERSAL
- Pagamento em DRAFT: `allowsPayment()` não inclui DRAFT

---

## 6. Saldo

### Fórmula (FinancialAccountService.calculateBalance)

```kotlin
saldo = initialBalance
      + sumPaid(accountId, INCOME)
      + sumPaid(accountId, REVERSAL)
      + sumPaid(accountId, ADJUSTMENT)
      + sumPaidTransferIn(accountId)
      - sumPaid(accountId, EXPENSE)
      - sumPaidTransferOut(accountId)
```

### Mapa de impacto no saldo

| Operação | Altera saldo? | Condição |
|----------|---------------|---------|
| Pagamento (EXPENSE) | **Sim** — reduz | Quando status chega a PAID |
| Recebimento (INCOME) | **Sim** — aumenta | Quando status chega a PAID |
| Transferência | **Sim** — origem reduz, destino aumenta | **Somente quando cada lado chega a PAID individualmente** |
| Estorno (REVERSAL) | **Sim** — reverte | REVERSAL é criado diretamente como PAID |
| Cancelamento | **Não** | CANCELED não entra na fórmula |
| Pagamento parcial (PARTIAL) | **Não** | Fórmula filtra só PAID; PARTIAL fica fora |
| Ajuste (ADJUSTMENT) | **Sim** — aumenta | Quando PAID |

### Detalhe técnico: `sumPaid()` usa a coluna `amount`, não `paidAmount`

Para PAID, `paidAmount >= amount` (garantido pelo validador). O saldo usa `amount`. Overpagamento (`paidAmount > amount`, permitido pelo `validatePayment()` quando `paidAmount <= total`... ver seção 11) não é refletido no saldo. Em uso normal isso não ocorre, mas a inconsistência existe.

---

## 7. Parcelamento

### Estrutura

- **Pai:** criado por `create()` com `installmentTotal > 1`
  - `installmentCurrent = 1`
  - `amount = floor(totalAmount / n)` — piso, RN-17
  - `parentTransactionId = null`
- **Filhos:** criados por `generateInstallments()`, parcelas 2..N
  - `installmentCurrent = i`
  - `amount = slice` para 2..N-1; `amount = total - (slice * (n-1))` para N — última absorve arredondamento (RN-18)
  - `dueDate = dueDatePai.plusMonths(i-1)`
  - `parentTransactionId = paiId`

### Pagamento individual de parcela

Cada parcela é uma `Transaction` independente. O pagamento de uma não afeta as outras. Não há lógica de "propagação" de pagamento — cada parcela deve ser quitada individualmente via `recordPayment()`.

### Impacto no lançamento pai

**Nenhum.** O pai não é atualizado quando filhos são pagos. O saldo global da conta aumenta à medida que cada parcela é paga, mas o pai permanece no status original até ser operado diretamente.

### Cancelamento (RN-19)

`cancel(paiId)` cascateia filhos PENDING/DRAFT automaticamente. Filhos PAID não são afetados. Filhos OVERDUE são afetados (status OVERDUE está em PENDING e DRAFT da query `findActiveChildrenOf`... **ver Bug #5**).

---

## 8. Transferências

### Representação

Duas `Transaction` independentes, vinculadas por `parentTransactionId`:

```
Origem:  type=TRANSFER, status=PENDING, accountId=sourceId, parentTransactionId=null
Destino: type=TRANSFER, status=PENDING, accountId=destId,   parentTransactionId=sourceId
```

### Lançamentos gerados

`createTransfer()` insere dois registros. Ambos com:
- `type = TRANSFER`
- `status = PENDING`
- `supplierId = null` (validador bloqueia supplierId em TRANSFER)
- `paidAmount = null` inicialmente

### Pagamento de transferência

**Não há mecanismo de pagamento atômico.** Cada lado é pago via `recordPayment()` independentemente. Para que o saldo reflita corretamente a movimentação:
1. Pagar o lado origem (EXPENSE-like) → reduz saldo da conta origem
2. Pagar o lado destino (INCOME-like) → aumenta saldo da conta destino

Se apenas um lado for pago, o saldo fica inconsistente entre as duas contas.

`sumPaidTransferIn/Out` discrimina pelo campo `parentTransactionId`:
- Transferência com `parentTransactionId != null` → ENTRADA (destino)
- Transferência com `parentTransactionId == null` → SAÍDA (origem)

### Estados

Ambos os lados seguem as mesmas transições da state machine padrão.

### Cancelamento (RN-21)

`cancel(idOrigem)` → `findTransferDestination(idOrigem)` → cancela destino se `allowsCancel`.
`cancel(idDestino)` → `findById(existing.parentTransactionId)` → cancela origem se `allowsCancel`.

### Estorno de transferência

Não existe estorno específico de transferência. Um lado pode ser estornado individualmente via `reverseTransaction()` como qualquer PAID, criando um REVERSAL na mesma conta. Isso deixa o outro lado sem contraparte de estorno.

---

## 9. Cancelamento

| Alvo | Comportamento |
|------|---------------|
| Transação simples | `deactivate()` → isActive=false, status=CANCELED |
| Pai de parcelamento | Cascateia para filhos PENDING/DRAFT via `findActiveChildrenOf()` |
| Transferência | Cascateia para o par vinculado se `allowsCancel()` |
| Lançamento PAID | **Bloqueado** — `allowsCancel(PAID) = false` |
| Lançamento CANCELED | **Bloqueado** — terminal |

O cancelamento NÃO afeta o saldo (somente PAID conta para o saldo).

---

## 10. Estorno

- Apenas lançamentos com `status == PAID` podem ser estornados (RN-23)
- Apenas usuários com `Permission.ConfirmPayment` podem estornar (RN-14)
- Justificativa obrigatória e não-branca (RN-22)
- Lançamento do tipo REVERSAL não pode ser estornado
- Segundo estorno do mesmo lançamento: bloqueado por `hasReversal()` — **mas** `hasReversal()` filtra por `isActive=true`. Como REVERSALs são criados como PAID (terminal), não podem ser cancelados. `hasReversal()` sempre encontrará o estorno ativo. Sem risco de duplo estorno.

O estorno cria uma nova `Transaction`:
```
type = REVERSAL
status = PAID
amount = original.amount
paidAmount = original.amount
accountId = original.accountId   ← mesma conta
paymentDate = now
notes = justification
parentTransactionId = originalId
```

O estorno **aumenta** o saldo (REVERSAL entra positivamente na fórmula de saldo).

---

## 11. Duplicidades

### Analisados

| Cenário | Proteção presente? | Detalhe |
|---------|-------------------|---------|
| Clicar duas vezes em "Quitar" | **Parcial** | A state machine rejeita o segundo POST (PAID é terminal), mas há race condition entre o clique e a checagem de estado em apps com Ktor |
| Pagar pelo menu e pelo painel | **Sim** — state machine | Segundo pagamento recebe `IllegalStateException` |
| Repetir requisição Ktor | **Não** | Sem idempotency key; dois POSTs simultâneos podem resultar em dois pagamentos (ver Bug #2) |
| Estornar duas vezes | **Sim** — `hasReversal()` | Segundo estorno bloqueado |
| Transferir duas vezes | **Não** | `createTransfer()` não tem verificação de duplicata |
| Cancelar duas vezes | **Sim** — state machine | CANCELED é terminal |
| Reconciliar manual já PAID | **Sim** — `allowsPayment()` | Rejeita se já PAID |

---

## 12. Bugs Encontrados

### CRÍTICO

Nenhum encontrado que impeça operação básica de forma absoluta.

### ALTO

**Bug #1 — Transferência não atômica: saldo inconsistente entre pagamentos dos dois lados**
- Arquivo: `TransactionService.kt:192-248`
- Ao criar uma transferência, dois registros são inseridos como PENDING. O sistema não obriga que os dois sejam pagos juntos nem ao mesmo tempo. Entre o pagamento do lado origem e o do lado destino, o saldo da conta origem já diminuiu mas o da conta destino ainda não aumentou.
- Impacto: saldo incorreto durante qualquer período entre os dois pagamentos. Se um lado nunca for pago, o saldo fica permanentemente errado.

**Bug #2 — Race condition em `recordPayment()` (cenário multi-request via Ktor)**
- Arquivo: `TransactionService.kt:318-371`
- `findById()` e `update()` não estão em uma transação com lock. Dois requests simultâneos podem ambos passar pelo check `allowsPayment(PENDING)` e ambos persistir o pagamento, sobrescrevendo o primeiro com os dados do segundo.
- Impacto: somente relevante se o servidor Ktor estiver em uso com mais de um cliente simultâneo. Desktop single-user: sem risco prático.

**Bug #3 — `duplicate()` de TRANSFER corrompe `findTransferDestination()`**
- Arquivo: `TransactionService.kt:380-399`
- `duplicate()` sempre copia `parentTransactionId = source.id`. Se `source` é a transação origem de uma transferência (type=TRANSFER, parentTransactionId=null), o duplicado também tem `type=TRANSFER, parentTransactionId=source.id`.
- `findTransferDestination(source.id)` retorna `.firstOrNull()` — pode retornar o duplicado em vez do destino real.
- Cancelar a origem após duplicação pode cancelar o duplicado em vez do destino da transferência original.

**Bug #4 — `duplicate()` de pai com parcelas é cancelado quando o original é cancelado**
- Arquivo: `TransactionService.kt:380-399` + `TransactionService.kt:156-168`
- `duplicate()` seta `parentTransactionId = source.id`. Se `source` tem `installmentTotal > 1 && parentTransactionId == null` (`isInstallmentParent = true`), cancelar `source` chama `findActiveChildrenOf(source.id)` que inclui PENDING/DRAFT com `parentTransactionId = source.id`. O duplicado (que é PENDING com `parentTransactionId = source.id`) é encontrado e cancelado.
- O usuário duplicou uma série de parcelas mas ao cancelar a original, a cópia é destruída junto.

### MÉDIO

**Bug #5 — `findActiveChildrenOf()` não cancela filhos OVERDUE em cascata**
- Arquivo: `TransactionRepository.kt:382-394`
- A query filtra apenas `status IN (PENDING, DRAFT)`. Filhos OVERDUE não são incluídos.
- Ao cancelar um pai parcelado, filhos que já venceram permanecem OVERDUE e ativas. Ficam visíveis na lista "Ação Necessária" indefinidamente como órfãs.

**Bug #6 — `ReceivablesViewModel` bypassa `syncOverdueStatuses()`**
- Arquivo: `ReceivablesViewModel.kt:57`
- Acessa `transactionRepository.findReceivables()` diretamente, sem instanciar `TransactionService`.
- Receitas vencidas aparecem como PENDING no painel de recebíveis até que o usuário acesse a tela principal de transações (que chama `listAll()` → `syncOverdueStatuses()`).

**Bug #7 — `cancelFutureByRecurrenceTemplate()` bypassa timeline e auditoria individuais**
- Arquivo: `TransactionRepository.kt:734-752`
- Faz `FinancialTransactionsTable.update(...)` em massa sem gravar evento de timeline nem entrada de auditoria para cada transação cancelada.
- Transações canceladas desta forma ficam sem histórico de "quem cancelou e quando".

**Bug #8 — `sumPaid()` usa coluna `amount` em vez de `paidAmount` para PAID**
- Arquivo: `TransactionRepository.kt:311-324`
- Quando `paidAmount > amount` (overpagamento) o excedente não conta no saldo.
- `validatePayment()` bloqueia `paidAmount > total` (linha 97-99 de TransactionValidator), então overpagamento é impossível em fluxo normal. Risco baixo, mas a inconsistência entre `amount` e `paidAmount` no sumário está presente.

**Bug #9 — Ambos os lados de transferência aparecem em "Ação Necessária"**
- Arquivo: `TransactionRepository.kt:112-125`
- `filterActionRequired()` retorna todos os PENDING/OVERDUE/PARTIAL ativos, incluindo type=TRANSFER.
- Uma transferência cria 2 registros PENDING, ambos aparecem na lista. Usuário vê dois itens para a mesma operação e pode tentar pagar um sem entender o par.

**Bug #10 — Estorno de transferência cria assimetria contábil**
- `reverseTransaction()` não tem tratamento especial para TRANSFER. Estornar um lado cria um REVERSAL na mesma conta mas não estorna o outro lado.
- O saldo da conta fica revertido para o lado estornado, mas não para o par.

### BAIXO

**Bug #11 — Comprovante PDF sem nome do favorecido para pagamentos de funcionários**
- Arquivo: `TransactionsViewModel.kt:291`
- `exportReceipt()` usa `tx.supplierId` para o nome do favorecido. Transações de funcionários têm `employeeId != null` mas `supplierId = null`. O PDF é gerado sem nome do beneficiário.

**Bug #12 — `syncOverdueStatuses()` tem efeitos colaterais dentro de `listAll()`**
- Arquivo: `TransactionService.kt:39-53`
- `listAll()` é chamado em toda atualização da UI. `syncOverdueStatuses()` percorre TODOS os PENDING ativos, podendo escrever timeline + audit para cada um que venceu. Uma lista com 500 transações pode gerar dezenas de writes durante uma simples navegação de tela.

**Bug #13 — `findById()` retorna transações inativas (isActive=false)**
- Arquivo: `TransactionRepository.kt:20-26`
- Sem filtro `isActive`. Usado em `cancel()`, `recordPayment()`, `reverseTransaction()` — todos protegidos pelo state machine, então sem risco de corrupção de dados. Mas dados de transações canceladas ficam acessíveis por ID diretamente.

---

## 13. Riscos

| Risco | Probabilidade | Impacto |
|-------|--------------|---------|
| Transferência com apenas um lado pago — saldo incorreto | Alta (sem enforcement) | Alto |
| Race condition no Ktor com múltiplos usuários simultâneos | Média (depende de adoção do servidor) | Alto |
| Filhos OVERDUE órfãos após cancelamento do pai | Média | Médio |
| Duplicata de TRANSFER corrompendo lookup | Baixa (requer ação específica do usuário) | Alto |
| Receitas vencidas permanecendo PENDING no painel de recebíveis | Alta (sempre que usuário abre Recebíveis sem abrir Transações antes) | Baixo (cosmético) |
| Transações sem auditoria individual por cancelamento em massa (recorrência) | Média | Médio (compliance) |

---

## 14. Testes Existentes

| Fluxo | Status |
|-------|--------|
| State machine (transições válidas/inválidas) | **Teste existente** — `TransactionWorkflowTest` |
| OverdueEngine (lógica de vencimento) | **Teste existente** — `TransactionWorkflowTest` |
| Fórmula de saldo (aritmética pura) | **Teste existente** — `TransferAndReversalTest` (testa fórmula sem banco) |
| RN-20 validações de transferência (valor zero, contas iguais) | **Teste existente** — `TransferAndReversalTest` |
| RN-21 cancela transferência PENDING | **Teste existente** — `TransferAndReversalTest` |
| RN-22 justificativa de estorno | **Teste existente** — `TransferAndReversalTest` |
| RN-23 estorno somente de PAID | **Teste existente** — `TransferAndReversalTest` |
| RN-16 paymentDate >= issueDate | **Teste existente** — `TransactionValidatorTest` |
| RN-08 aviso fora do período do convênio | **Teste existente** — `TransactionValidatorTest` |
| RN-17/18 cálculo de parcelas e arredondamento | **Teste existente** — `InstallmentCalculatorTest` |
| RN-19 cancelamento via state machine | **Teste existente** — `InstallmentCalculatorTest` |
| Parcelamento com banco (criação, filhos) | **Sem teste** |
| `recordPayment()` com banco real | **Sem teste** |
| Cancelamento em cascata de parcelas | **Sem teste** |
| `createTransfer()` e pagamento dos dois lados | **Sem teste** |
| `reconcile()` OFX | **Sem teste** |
| `syncOverdueStatuses()` com banco | **Sem teste** |
| `duplicate()` de TRANSFER | **Sem teste** |
| `cancelFutureByRecurrenceTemplate()` | **Sem teste** |
| `PayrollEngine` com banco | **Sem teste** |
| Saldo da conta (calculateBalance) | **Sem teste** |
| Validador de documento CPF/CNPJ | **Teste existente** — `DocumentValidatorTest` |
| Parser OFX | **Teste existente** — `OfxParserTest` |
| Parser XLSX folha de pagamento | **Teste existente** — `PayrollXlsxParserTest` |
| RecurrenceEngine (geração de datas) | **Teste existente** — `RecurrenceEngineTest` |
| Money (aritmética) | **Teste existente** — `MoneyTest` |

**Cobertura geral:** lógica pura tem boa cobertura. Fluxos que dependem do banco de dados não têm testes de integração.

---

## 15. Pontos que Precisam de Investigação Adicional

1. **Ktor API em produção:** o arquivo `KtorServer.kt` não foi analisado. Verificar se `recordPayment` e `createTransfer` estão expostos como endpoints REST. Se sim, o Bug #2 (race condition) é imediato.

2. **Ledger futuro:** `LedgerService` é um stub. Se implementado no futuro, `reconcile()` (que chama `repository.update()` diretamente sem `ledgerService.recordPayment()`) precisará ser atualizado.

3. **`TransactionDetailsPanel` — proteção contra double-click no botão "Quitar":** o arquivo foi parcialmente lido. Verificar se o botão é desabilitado após o primeiro clique ou se há `isLoading` que previne duplo submit antes da resposta do `runOperation`.

4. **`duplicate()` — comportamento intencional ou bug para parcelamentos?** Verificar se o produto quer que duplicar uma parcela-pai crie uma nova série completa ou apenas uma cópia simples.

5. **`costCenterId` vs `projectId` em Transaction:** existem dois campos distintos (linhas 27 e 44 de `Transaction.kt`). `costCenterId` mapeia para tabela `projects` (CostCenter). `projectId` mapeia para `financial/projects/Project`. A sobreposição de nomenclatura pode ser fonte de bugs de UI e de relatórios.

6. **Permissão `ConfirmPayment` na camada Ktor:** verificar se a autenticação JWT na API Ktor enforça a mesma permissão que `sessionManager.hasPermission(Permission.ConfirmPayment)` no desktop. Se não, qualquer usuário autenticado via API pode confirmar pagamentos.

7. **`filterActionRequired()` incluindo TRANSFER:** verificar se a UI tem forma de distinguir e pagar corretamente os dois lados de uma transferência a partir da lista de "Ação Necessária".
