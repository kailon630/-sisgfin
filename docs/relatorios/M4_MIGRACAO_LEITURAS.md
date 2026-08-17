# M4 — Migração das Leituras para `transaction_payments`

**Épico**: E2 — Integridade de Caixa  
**Commits**: `8e15be0` → `a321538` (branch `main`, 2026-08-16)  
**Status**: ✅ Concluído

---

## Objetivo

Migrar todos os pontos de leitura do saldo e do realizado orçamentário de
`financial_transactions` (campos `amount` / `paidAmount`) para
`transaction_payments` (cashEffective = principal + juros + multa − desconto).

Isso resolve o P0-5 (encargos invisíveis no saldo) e D3(b) (desconto que não
reduzia o realizado).

---

## Blocos implementados

### Bloco 1 — `calculateBalance` (commit `8e15be0`)

**Antes**: `FinancialAccountService.calculateBalance()` usava
`TransactionRepository.sumPaid()` → `SUM(amount)` e
`sumPartialPaid()` → `SUM(paidAmount)`. Juros e multa eram invisíveis para
títulos PAID; assimetria ao mudar PARTIAL→PAID fazia encargos da 1ª baixa
desaparecerem.

**Depois**: dois novos métodos em `TransactionPaymentRepository`:
- `sumCashEffectiveByAccountAndType()` — JOIN `transaction_payments × financial_transactions`; PAID + PARTIAL unificados; filtra por `reversed_by_id IS NULL`
- `sumCashEffectiveForReversalOf()` — JOIN via `parent_transaction_id`; estorno credita cashEffective do original (não `amount` do título estorno)

`FinancialAccountService` recebe `paymentRepository: TransactionPaymentRepository` como terceiro parâmetro obrigatório. `ServiceModule` atualizado.

`EncargosNoSaldoTest` (P0-5) invertido: Q5a-Q5d agora afirmam comportamento correto. Os dois testes CARACTERIZACAO P0-5 em `RecordPaymentIntegrationTest` tiveram comments atualizados (assertions sobre `amount=1000` permanecem válidas — face value imutável).

### Bloco 2 — `openingBalance` (commit `6a63c21`)

**Antes**: `TransactionRepository.openingBalance()` filtrava por `t.payment_date` (data do título) e separava PARTIAL via `sumPartialPaidBefore()`.

**Depois**: dois novos métodos em `TransactionPaymentRepository`:
- `sumCashEffectiveByAccountAndTypeBefore()` — filtra por `p.payment_date < before` (data da baixa)
- `sumCashEffectiveForReversalOfBefore()` — filtra pela `paymentDate` do estorno

`openingBalance()` aceita `paymentRepository: TransactionPaymentRepository` como quarto parâmetro. `StatementViewModel`, `StatementRoutes` e `KtorServer` atualizados. `ViewModelModule` +1 `get()` para `StatementViewModel`.

### Bloco 3 — `sumRealized` / `sumRealizedMonth` (commit `dfa8184`)

**Antes**: `BudgetItemRepository` usava `SUM(amount)` de `financial_transactions`; desconto não reduzia o realizado.

**Depois**: `sumRealized()` e `sumRealizedMonth()` reescritos com JOIN direto em `TransactionPaymentsTable`:
- Despesas: filtra por `p.payment_date` (no mês ou no ano)
- Estornos: JOIN via `parent_transaction_id`; subtrai cashEffective do original
- PARTIAL contribui automaticamente (sem separação de status)

D3(b) implementado: desconto reduz a despesa orçamentária.

### Bloco 4 — Dashboard KPIs (commit `a321538`)

**Antes**: `DashboardViewModel.monthIncome` / `monthExpense` usavam fold sobre `findAllPaid()` com `paidAmount ?: amount`.

**Depois**: dois novos métodos em `TransactionPaymentRepository`:
- `sumCashEffectiveInPeriod()` — para INCOME e EXPENSE; filtra por `p.payment_date`
- `sumCashEffectiveForReversalOfInPeriod()` — para crédito de estorno de EXPENSE

`DashboardViewModel` recebe `paymentRepository` como quinto parâmetro. `ViewModelModule` +1 `get()`.

`CashFlowService.outflow()`/`inflow()`: sem mudança — trabalham com títulos não pagos (projeção); `amount` é correto para projeções. `calculateBalance()` via `CashFlowService.consolidatedBalance()` já estava corrigido pelo Bloco 1.

---

## Teste de invariante C-13

`CashInvariantTest` (7 testes) em `br.com.sisgfin.financial.payments`:
- C-13-A: despesa com juros e multa (cashEffective completo)
- C-13-B: desconto reduz cashEffective
- C-13-C: PARTIAL + PAID unificados sem duplicação
- C-13-D: sem assimetria PARTIAL→PAID
- C-13-E: estorno de EXPENSE credita de volta
- C-13-F: receita com juros aumenta saldo
- C-13-G: income = expense → saldo zero

---

## Limitações conhecidas

- **Transferências**: `sumPaidTransferIn/Out` mantidos em `TransactionRepository` (sem baixas em M4; M5 resolve)
- **Backfill**: baixas migradas têm `interest_amount = 0`, `fine_amount = 0` → cashEffective = principal; consistente mas sem detalhamento de encargos históricos
- **Desconto com discount semantics**: cashEffective = principal + juros + multa − desconto; quando principal = cash pago e discount = redução concedida, o saldo cai por (principal − desconto), não por principal — edge case documentado

---

## Arquivos modificados

| Arquivo | Mudança |
|---|---|
| `TransactionPaymentRepository.kt` | +6 métodos (sumCashEffective*) |
| `FinancialServices.kt` | `calculateBalance()` via paymentRepository |
| `TransactionRepository.kt` | `openingBalance()` aceita paymentRepository |
| `BudgetItemRepository.kt` | `sumRealized()` / `sumRealizedMonth()` via baixas |
| `DashboardViewModel.kt` | KPIs via paymentRepository |
| `StatementViewModel.kt` | openingBalance via paymentRepository |
| `StatementRoutes.kt` | paymentRepository repassado |
| `KtorServer.kt` | paymentRepository no servidor Ktor |
| `Main.kt` | paymentRepository no createKtorServer |
| `ServiceModule.kt` | FinancialAccountService +1 get() |
| `ViewModelModule.kt` | StatementViewModel +1 get(), DashboardViewModel +1 get() |
| `EncargosNoSaldoTest.kt` | Invertido para comportamento correto (P0-5) |
| `RecordPaymentIntegrationTest.kt` | Comments CARACTERIZACAO P0-5 atualizados |
| `CashInvariantTest.kt` | Novo — C-13 (7 testes) |
