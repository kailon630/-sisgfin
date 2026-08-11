# R3 — Cálculo de saldo, estorno e transferência

## 1. Resumo executivo

O saldo de conta é calculado em código (nunca digitado), com fórmula **mais ampla** que a RN-04 textual dos requisitos. `REVERSAL` entra como **parcela positiva**. Estorno cria lançamento `type=REVERSAL` / `status=PAID` e afeta saldo imediatamente. Transferência é um **par** de linhas `TRANSFER` (origem sem `parentTransactionId`, destino com). A UI bloqueia edição de PAID; `TransactionService.update` / API **não** têm guarda equivalente de campos.

## 2. Estado atual

### 2.1 `calculateBalance()` — corpo completo

Local: `FinancialAccountService` em `FinancialServices.kt`.  
Nota: `openingBalance()` **não** está no service; está em `TransactionRepository`.

```58:69:src/main/kotlin/br/com/sisgfin/FinancialServices.kt
    fun calculateBalance(accountId: Int): Money {
        val account = accountRepository.findById(accountId) ?: return Money.ZERO
        val income         = transactionRepository.sumPaid(accountId, TransactionType.INCOME)
        val expense        = transactionRepository.sumPaid(accountId, TransactionType.EXPENSE)
        val reversal       = transactionRepository.sumPaid(accountId, TransactionType.REVERSAL)
        val adjustment     = transactionRepository.sumPaid(accountId, TransactionType.ADJUSTMENT)
        val transferIn     = transactionRepository.sumPaidTransferIn(accountId)
        val transferOut    = transactionRepository.sumPaidTransferOut(accountId)
        val incomePartial  = transactionRepository.sumPartialPaid(accountId, TransactionType.INCOME)
        val expensePartial = transactionRepository.sumPartialPaid(accountId, TransactionType.EXPENSE)
        return account.initialBalance + income + incomePartial + reversal + adjustment + transferIn - expense - expensePartial - transferOut
    }
```

### 2.2 `openingBalance()` — corpo completo

```534:544:src/main/kotlin/br/com/sisgfin/financial/transactions/TransactionRepository.kt
    fun openingBalance(initialBalance: Money, accountId: Int, before: LocalDate): Money {
        val income          = sumPaidBefore(accountId, TransactionType.INCOME, before)
        val expense         = sumPaidBefore(accountId, TransactionType.EXPENSE, before)
        val reversal        = sumPaidBefore(accountId, TransactionType.REVERSAL, before)
        val adjustment      = sumPaidBefore(accountId, TransactionType.ADJUSTMENT, before)
        val transferIn      = sumPaidTransferInBefore(accountId, before)
        val transferOut     = sumPaidTransferOutBefore(accountId, before)
        val incomePartial   = sumPartialPaidBefore(accountId, TransactionType.INCOME, before)
        val expensePartial  = sumPartialPaidBefore(accountId, TransactionType.EXPENSE, before)
        return initialBalance + income + incomePartial + reversal + adjustment + transferIn - expense - expensePartial - transferOut
    }
```

### 2.3 `REVERSAL` na fórmula

**Sim.** Entra com sinal **positivo** (`+ reversal`), via `sumPaid(..., REVERSAL)` (status PAID, ativo).  
Teste documenta recuperação de despesa paga: `initial + reversal - expense` restaura saldo (`TransferAndReversalTest.kt:185–194`).

### 2.4 `reverseTransaction()` — corpo completo

```252:313:src/main/kotlin/br/com/sisgfin/financial/transactions/TransactionService.kt
    fun reverseTransaction(originalId: Int, justification: String): Int {
        requirePermission(Permission.ConfirmPayment)
        if (justification.isBlank()) {
            throw IllegalArgumentException("Justificativa é obrigatória para realizar um estorno.")
        }
        val original = repository.findById(originalId)
            ?: throw IllegalArgumentException("Lançamento não encontrado.")
        if (original.status != TransactionStatus.PAID) { /* IllegalStateException */ }
        if (original.type == TransactionType.REVERSAL) { /* IllegalArgumentException */ }
        if (repository.hasReversal(originalId)) { /* IllegalStateException */ }

        val reversal = Transaction(
            type = TransactionType.REVERSAL,
            status = TransactionStatus.PAID,
            amount = original.amount,
            description = "Estorno: ${original.description}",
            issueDate = now,
            dueDate = now,
            paymentDate = now,
            paidAmount = original.amount,
            accountId = original.accountId,
            supplierId = original.supplierId,
            costCenterId = original.costCenterId,
            categoryId = original.categoryId,
            notes = justification,
            parentTransactionId = originalId,
            // ...
        )
        val reversalId = repository.insert(reversal)
        // timelines + audit
        return reversalId
    }
```

- **type:** `REVERSAL`
- **status:** `PAID` (já liquidado)
- **Afeta saldo:** sim, imediatamente, porque entra em `sumPaid(REVERSAL)`

O lançamento original **permanece** PAID; o efeito de caixa vem da linha de estorno positiva (modelo “compensação”, não status REVERSED no original).

### 2.5 Representação de `TRANSFER`

Par de linhas, ambas `type=TRANSFER`, status PAID:

| Papel | Identificação no saldo | Critério |
|-------|------------------------|----------|
| Saída (`transferOut`) | `sumPaidTransferOut` | `parentTransactionId IS NULL` |
| Entrada (`transferIn`) | `sumPaidTransferIn` | `parentTransactionId IS NOT NULL` (aponta para a origem) |

Criação em `TransactionService.createTransfer`. Não é uma única linha com conta origem+destino.

### 2.6 Edição de lançamento já `PAID`

| Camada | Bloqueia edição de campos? |
|--------|----------------------------|
| UI (`TransactionDetailsPanel`) | Sim: `canEdit = !isTerminal(status)`; PAID/CANCELED ocultam form/Save |
| State machine | Bloqueia **transições de status** a partir de PAID; não valida campos |
| `TransactionService.update` | Não impede alterar `amount` / `accountId` / `paymentDate` se status permanecer PAID |
| API `PUT` | Mesmo caminho de serviço — sem guarda de terminalidade de campos |

### 2.7 RN-04 documentada vs código

| Fonte | Fórmula |
|-------|---------|
| `docs/REQUISITOS_SISGFIN.md` RN-04 | `saldo_inicial + Σreceitas_pagas − Σdespesas_pagas` |
| `docs/RETRATO_PROJETO.md` (tabela RN) | inicial + INCOME PAID + INCOME PARTIAL + ADJUSTMENT + transferIn − EXPENSE PAID − EXPENSE PARTIAL − transferOut (**omite REVERSAL na tabela**) |
| Código `calculateBalance` | inclui também `+ REVERSAL` e `+ ADJUSTMENT` e parciais |

O saldo da tela segue o **código**, não o texto curto da RN-04 original.

## 3. Resultados das queries

Agrupamento por conta/tipo/status (ativos):

| account_id | type | status | count | SUM(amount) | SUM(paid_amount) |
|-----------:|------|--------|------:|------------:|-----------------:|
| 1 | EXPENSE | PENDING | 4 | 20000.00 | NULL |

Não há lançamentos PAID/REVERSAL/TRANSFER nesta base — reconciliação aritmética de saldo liquidado não tem movimento pago para confrontar; saldo exibido = `initialBalance` das contas (existem 2 contas `financial_accounts` com `initial_balance=100000.00` cada, ids 1 e 2; txs apontam para `account_id=1`).

## 4. Achados

| # | Severidade | Achado | Arquivo:linha | Impacto |
|---|-----------|--------|---------------|---------|
| 1 | MÉDIO | Documentação RN-04 incompleta vs código (falta REVERSAL / PARTIAL / TRANSFER / ADJUSTMENT no requisito curto) | `REQUISITOS_SISGFIN.md:52` vs `FinancialServices.kt:58–68` | Divergência ao auditar “pelo documento” |
| 2 | CRÍTICO | Estorno de **receita** (INCOME) também gera `REVERSAL` somado positivamente — não inverte o sinal do tipo original | `TransactionService.kt:279–298` + `calculateBalance` | Estornar receita **aumentaria** o saldo em vez de reduzir |
| 3 | ALTO | Original PAID permanece PAID após estorno; só a linha REVERSAL compensa | `reverseTransaction` | Relatórios que somam EXPENSE PAID sem nettar REVERSAL distorcem “despesas pagas” |
| 4 | ALTO | Serviço/API permitem update de campos em status PAID; só a UI bloqueia | `TransactionService.kt:99–111` | Caminho REST pode alterar valor/conta/data de pago e invalidar saldo histórico |
| 5 | MÉDIO | Tabela RN do RETRATO omite `REVERSAL` embora o código some | `RETRATO_PROJETO.md:539` | Retrato desatualizado em relação ao código |

## 5. Incertezas

- Não há PAID/REVERSAL na base local para medir saldo real pós-estorno numericamente.
- Não foi confirmado se a API expõe estorno de INCOME na UI (botão Reverse aparece para qualquer PAID não-REVERSAL com permissão).
- Comportamento de estorno de TRANSFER / ADJUSTMENT não foi coberto por teste de integração dedicado além das regras de status.
