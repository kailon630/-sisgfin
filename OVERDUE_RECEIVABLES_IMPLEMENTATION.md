# OVERDUE_RECEIVABLES_IMPLEMENTATION.md
> Implementação da correção de recebíveis vencidos não sincronizados
> Data: 2026-08-10 | SisgFin

---

## 1. Resumo

Correção implementada com sucesso. `ReceivablesViewModel` agora chama `syncOverdueStatuses()` antes de `findReceivables()`, garantindo que receitas vencidas apareçam com status `OVERDUE` na tela de Recebíveis independentemente de o usuário ter aberto a tela de Transações. 131 testes passando, 0 falhas.

---

## 2. Arquivos Alterados

| Arquivo | Tipo de alteração |
|---------|------------------|
| `src/main/kotlin/br/com/sisgfin/receivables/ReceivablesViewModel.kt` | Import + parâmetro `TransactionService` + chamada `syncOverdueStatuses()` |
| `src/main/kotlin/br/com/sisgfin/di/ViewModelModule.kt` | Adição de `get()` na construção de `ReceivablesViewModel` |
| `src/test/kotlin/br/com/sisgfin/financial/transactions/workflow/OverdueEngineTest.kt` | Arquivo novo — 6 testes |

---

## 3. Alterações Realizadas

### `ReceivablesViewModel.kt`

**Import adicionado:**
```kotlin
import br.com.sisgfin.financial.transactions.TransactionService
```

**Parâmetro adicionado ao construtor:**
```kotlin
class ReceivablesViewModel(
    private val transactionRepository: TransactionRepository,
    private val supplierRepository: SupplierRepository,
    private val transactionService: TransactionService   // ← novo
) : BaseViewModel()
```

**Chamada adicionada em `load()`:**
```kotlin
val result = withContext(Dispatchers.IO) {
    runCatching {
        transactionService.syncOverdueStatuses()           // ← nova linha
        val today = LocalDate.now()
        val all = transactionRepository.findReceivables()
        ...
    }
}
```

- Posicionada dentro de `runCatching`: erros de sincronização são capturados e exibidos na UI
- Posicionada dentro de `withContext(Dispatchers.IO)`: correto para operações de banco via Exposed
- Posicionada antes de `findReceivables()`: banco atualizado antes da consulta

### `ViewModelModule.kt`

```kotlin
// Antes:
factory { ReceivablesViewModel(get(), get()) }

// Depois:
factory { ReceivablesViewModel(get(), get(), get()) }
```

Koin resolve o terceiro `get()` como `TransactionService` via binding existente no módulo de serviços.

---

## 4. Fluxo Corrigido

```
Antes:
  ReceivablesScreen
    → ReceivablesViewModel.load()
    → transactionRepository.findReceivables()   ← sem sync
    → receita vencida retorna como PENDING

Depois:
  ReceivablesScreen
    → ReceivablesViewModel.load()
    → transactionService.syncOverdueStatuses()  ← sincroniza banco
    → transactionRepository.findReceivables()
    → receita vencida retorna como OVERDUE
```

---

## 5. Testes Criados

**Arquivo:** `OverdueEngineTest.kt` — 6 testes

| Teste | Cenário | Resultado esperado |
|-------|---------|-------------------|
| T1 | PENDING, dueDate=ontem | `shouldMarkOverdue` = true |
| T2 | PENDING, dueDate=amanhã | `shouldMarkOverdue` = false |
| T3 | PENDING, dueDate=hoje | `shouldMarkOverdue` = false (isBefore estrito) |
| T4 | PAID, dueDate=ontem | `shouldMarkOverdue` = false |
| T5 | CANCELED, dueDate=ontem | `shouldMarkOverdue` = false |
| T6 | PENDING, isActive=false, dueDate=ontem | `shouldMarkOverdue` = false |

T7 (fluxo completo do ViewModel com banco real) não implementado: sem H2/Testcontainers disponível — OUT_OF_SCOPE.

---

## 6. Testes Executados

```
./gradlew test
```

**Resultado:** BUILD SUCCESSFUL

| Suite | Testes | Falhas |
|-------|--------|--------|
| OverdueEngineTest (novos) | 6 | 0 |
| PartialBalanceTest | 11 | 0 |
| InstallmentCalculatorTest | 15 | 0 |
| TransactionValidatorTest | 17 | 0 |
| TransferAndReversalTest | 18 | 0 |
| TransactionWorkflowTest | 3 | 0 |
| MoneyTest | 6 | 0 |
| DocumentValidatorTest | 18 | 0 |
| OfxParserTest | 12 | 0 |
| PayrollXlsxParserTest | 8 | 0 |
| RecurrenceEngineTest | 17 | 0 |
| **Total** | **131** | **0** |

---

## 7. Resultado dos Testes

```
BUILD SUCCESSFUL in 17s
10 actionable tasks: 4 executed, 6 up-to-date
```

Todos os 131 testes passaram. Nenhuma regressão introduzida.

---

## 8. Análise de Risco

**`syncOverdueStatuses()` chamado de dois pontos agora:**

| Entry point | Via | Risco |
|-------------|-----|-------|
| `TransactionService.listAll()` | Tela de Transações / API Ktor | Existia antes |
| `ReceivablesViewModel.load()` | Tela de Recebíveis | Novo |

**Por que é seguro:**
- `shouldMarkOverdue()` retorna false se `status != PENDING` — transações já OVERDUE são ignoradas; não há dupla atualização
- Transações PAID, PARTIAL, CANCELED, DRAFT, SCHEDULED nunca são tocadas
- `findPendingActive()` retorna todos os tipos (INCOME, EXPENSE, etc.), não apenas INCOME — comportamento idêntico ao fluxo existente
- Se `syncOverdueStatuses()` lançar exceção, `runCatching` captura e exibe mensagem de erro; UI não quebra silenciosamente

---

## 9. Problemas Encontrados Fora do Escopo

**OUT_OF_SCOPE #1 — DashboardViewModel não sincroniza**

`DashboardViewModel` injeta `TransactionRepository` diretamente (sem `TransactionService`), mesmo bug identificado como OV-2 na investigação. Não alterado nesta implementação conforme instrução: "NÃO fazer: alteração de Dashboard".

**OUT_OF_SCOPE #2 — T7 (teste de integração com banco)**

Teste de fluxo completo `ReceivablesViewModel.load()` + `syncOverdueStatuses()` + `findReceivables()` requer banco real. Sem H2/Testcontainers no projeto. Verificação possível apenas em ambiente de desenvolvimento com PostgreSQL.

---

## 10. Conclusão

- **Implementação:** concluída
- **Testes criados:** 6 novos testes passando
- **Suite completa:** 131/131, 0 falhas, 0 regressões
- **Pendências:** DashboardViewModel (OUT_OF_SCOPE #1); T7 integração banco (OUT_OF_SCOPE #2)
