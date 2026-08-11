# OVERDUE_RECEIVABLES_INVESTIGATION.md
> Investigação: recebíveis vencidos e sincronização de OVERDUE
> Data: 2026-08-10 | SisgFin

---

## 1. Resumo

**Confirmado:** uma receita vencida **aparece como PENDING** quando o usuário abre a tela de Recebíveis.

`ReceivablesViewModel` acessa `TransactionRepository` diretamente — sem passar por `TransactionService`. `syncOverdueStatuses()` só é chamado dentro de `TransactionService.listAll()`, que por sua vez só é invocado pela tela principal de Transações. A tela de Recebíveis e o Dashboard nunca disparam a sincronização.

Resultado concreto: se o usuário abrir apenas a tela de Recebíveis sem nunca ter aberto a tela de Transações, receitas vencidas aparecem com status `PENDING`. O banco permanece desatualizado.

A menor correção possível é injetar `TransactionService` em `ReceivablesViewModel` e chamar `syncOverdueStatuses()` antes de `findReceivables()`. Dois arquivos afetados.

---

## 2. Fluxo Atual

```
ReceivablesScreen
  → ReceivablesViewModel.load()
  → transactionRepository.findReceivables()   ← Repository direto, sem Service
  → SELECT * FROM financial_transactions
    WHERE type='INCOME'
    AND status IN ('PENDING','OVERDUE','PARTIAL')
    AND isActive=true
    ORDER BY dueDate ASC
```

`TransactionService` **não está no caminho**. `syncOverdueStatuses()` **não é chamado**.

---

## 3. `syncOverdueStatuses()`

**Localização:** `TransactionService.kt:594-612`

**Implementação real:**

```kotlin
fun syncOverdueStatuses(today: LocalDate = LocalDate.now()) {
    val pending = repository.findPendingActive()   // busca TODOS os PENDING ativos
    pending.forEach { tx ->
        if (!OverdueEngine.shouldMarkOverdue(tx, today)) return@forEach
        TransactionStateMachine.assertTransition(PENDING, OVERDUE)
        val updated = tx.copy(status = OVERDUE, updatedAt = LocalDateTime.now())
        repository.update(updated)         // ← WRITE no banco
        addTimeline(...)                   // ← INSERT timeline
        audit(...)                         // ← INSERT audit_log
    }
}
```

**Comportamento:**
- Carrega todos os `PENDING` + `isActive=true` (qualquer tipo: INCOME, EXPENSE, etc.)
- Para cada um: checa `OverdueEngine.shouldMarkOverdue()`
- Se elegível: atualiza status para `OVERDUE` no banco + grava timeline + audit
- Altera o banco: sim
- Condições que impedem atualização: status != PENDING, isActive=false, dueDate >= hoje

**Quem chama:**

```kotlin
// TransactionService.kt:39-40
override fun listAll(): List<Transaction> {
    syncOverdueStatuses()    // ← única chamada em toda a codebase
    ...
}
```

`syncOverdueStatuses()` é chamado **exclusivamente** dentro de `TransactionService.listAll()`.

---

## 4. Relação com a Listagem de Recebíveis

**Pergunta direta → Resposta direta:**

```
syncOverdueStatuses()
→ executado antes da listagem de recebíveis?  NÃO
→ executado depois da listagem?               NÃO
→ não executado?                              SIM — nunca executado nesse fluxo
```

**Evidência:**

```kotlin
// ReceivablesViewModel.kt:41-44
class ReceivablesViewModel(
    private val transactionRepository: TransactionRepository,  // ← só Repository
    private val supplierRepository: SupplierRepository          // ← só Repository
) : BaseViewModel()
```

`TransactionService` não está nos parâmetros do construtor. Não pode ser chamado.

```kotlin
// ReceivablesViewModel.kt:57
val all = transactionRepository.findReceivables()   // ← acesso direto ao repositório
```

Nenhuma chamada a `syncOverdueStatuses()` antes, durante ou depois.

---

## 5. Cenário de Receita Vencida

**Entrada:**
```
Receita: R$ 1.000
dueDate: ontem
status armazenado no banco: PENDING
```

**O que acontece hoje quando o usuário abre Recebíveis:**

1. `ReceivablesViewModel.load()` é chamado
2. `transactionRepository.findReceivables()` executa:
   ```sql
   SELECT * FROM financial_transactions
   WHERE type = 'INCOME'
   AND status IN ('PENDING', 'OVERDUE', 'PARTIAL')
   AND isActive = true
   ORDER BY dueDate ASC
   ```
3. A receita tem `status = 'PENDING'` → **incluída no resultado**
4. `syncOverdueStatuses()` não foi chamado → banco permanece com `status = 'PENDING'`
5. **A receita é exibida como PENDENTE, não como VENCIDA**

**O banco não é atualizado.** A próxima vez que o usuário abrir a tela de Transações (`TransactionsViewModel`), aí sim `syncOverdueStatuses()` será chamado e o status no banco mudará para OVERDUE.

---

## 6. Regra de Vencimento

**Implementação real — `OverdueEngine.kt:12-16`:**

```kotlin
fun shouldMarkOverdue(transaction: Transaction, today: LocalDate = LocalDate.now()): Boolean {
    if (transaction.status != TransactionStatus.PENDING) return false
    if (!transaction.isActive) return false
    return transaction.dueDate.toLocalDate().isBefore(today)
}
```

**Regra em linguagem simples:**

```
Uma transação é marcada OVERDUE quando:

status == PENDING
AND isActive == true
AND dueDate.toLocalDate() < hoje (isBefore — estritamente anterior, não inclui hoje)
```

**Corolários confirmados pelo código:**

- Se dueDate == hoje → **não** é OVERDUE (isBefore retorna false)
- Se status == PAID → nunca marcada OVERDUE
- Se status == CANCELED → nunca marcada OVERDUE (`isActive=false` + estado terminal)
- Se status == OVERDUE → nunca re-processada (status != PENDING)
- Se status == PARTIAL → nunca marcada OVERDUE (status != PENDING)
- `paymentDate` não participa da regra — apenas `dueDate`, `status` e `isActive`

---

## 7. Outros Fluxos

**Onde `syncOverdueStatuses()` É chamado:**

| Fluxo | Via | Chamada |
|-------|-----|---------|
| Tela de Transações (TransactionsViewModel) | `BaseCrudViewModel.load()` → `TransactionService.listAll()` | Sim |
| TransactionsViewModel.markAsPaidFull() | `service.listAll()` | Sim |
| TransactionsViewModel.exportReceipt() | `service.listAll()` | Sim |
| Ktor API GET /transactions | `TransactionRoutes.kt:41` → `service.listAll()` | Sim |

**Onde `syncOverdueStatuses()` NÃO é chamado:**

| Fluxo | Acesso direto |
|-------|--------------|
| ReceivablesScreen / ReceivablesViewModel | `transactionRepository.findReceivables()` |
| DashboardViewModel | `transactionRepository` diretamente |
| CashFlowService / CashFlowViewModel | Não verificado |

**Conclusão:** a sincronização só ocorre quando o usuário abre a tela principal de Transações ou quando a API Ktor recebe um GET /transactions.

---

## 8. Bugs Encontrados

| ID | Problema | Severidade | Evidência |
|----|----------|------------|-----------|
| OV-1 | Receita vencida exibida como PENDING na tela de Recebíveis quando usuário nunca abriu a tela de Transações | **MÉDIO** | `ReceivablesViewModel.kt:41-44` — sem `TransactionService`; `TransactionService.kt:40` — único ponto de chamada de `syncOverdueStatuses()` |
| OV-2 | Dashboard também não executa `syncOverdueStatuses()` | **BAIXO** | `DashboardViewModel.kt` injeta `TransactionRepository` diretamente, sem `TransactionService` |

---

## 9. Testes Existentes

| Teste | Arquivo | Cobre |
|-------|---------|-------|
| `test overdue engine` | `TransactionWorkflowTest.kt` | `OverdueEngine.shouldMarkOverdue()` em memória pura; verifica regra dueDate < hoje; sem banco |
| `test state machine transitions` | `TransactionWorkflowTest.kt` | Transição PENDING → OVERDUE via state machine; sem banco |

Nenhum teste cobre:
- `syncOverdueStatuses()` com banco real
- comportamento de `ReceivablesViewModel` com receita vencida
- diferença entre abrir Recebíveis vs. abrir Transações

---

## 10. Testes Ausentes

| Cenário | Tipo necessário |
|---------|----------------|
| Receita PENDING com dueDate=ontem → `syncOverdueStatuses()` → status=OVERDUE no banco | Integração com banco |
| `ReceivablesViewModel.load()` retorna OVERDUE após sincronização | Integração com banco |
| Receita com dueDate=hoje não é marcada OVERDUE | Unidade (puro) — **possível sem banco** |
| Receita PAID com dueDate=ontem nunca é marcada OVERDUE | Unidade (puro) — **possível sem banco** |
| Receita PARTIAL não é marcada OVERDUE | Unidade (puro) — **possível sem banco** |

Os casos puros poderiam ser adicionados a `TransactionWorkflowTest.kt` sem dependência de banco.

---

## 11. Menor Correção Possível

**Descrição:** injetar `TransactionService` em `ReceivablesViewModel` e chamar `syncOverdueStatuses()` antes de `findReceivables()`.

**Antes (pseudocódigo atual):**
```kotlin
class ReceivablesViewModel(
    private val transactionRepository: TransactionRepository,
    private val supplierRepository: SupplierRepository
) {
    fun load() {
        val all = transactionRepository.findReceivables()
        ...
    }
}
```

**Depois (menor alteração):**
```kotlin
class ReceivablesViewModel(
    private val transactionRepository: TransactionRepository,
    private val supplierRepository: SupplierRepository,
    private val transactionService: TransactionService      // ← novo parâmetro
) {
    fun load() {
        transactionService.syncOverdueStatuses()            // ← nova linha
        val all = transactionRepository.findReceivables()
        ...
    }
}
```

Nenhuma alteração em `findReceivables()`, no banco, no schema, na state machine ou no `OverdueEngine`.

---

## 12. Arquivos que Precisariam Ser Alterados

| Arquivo | Alteração |
|---------|-----------|
| `receivables/ReceivablesViewModel.kt` | Adicionar parâmetro `TransactionService`; chamar `syncOverdueStatuses()` em `load()` |
| `di/ViewModelModule.kt` | Atualizar construção de `ReceivablesViewModel` para injetar `TransactionService` |
