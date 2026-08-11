# P0-4 — Liquidação parcial: acumulação e validação no serviço

> **Severidade: CRÍTICO.** Segunda baixa parcial sobrescreve `paidAmount`, produzindo saldo de conta incorreto e título integralmente pago preso em `PARTIAL`.
>
> Origem: F2_FIX_P0.md — `recordPayment` não valida contra `paidAmount` acumulado; `resolveStatusAfterPayment` compara `paidAmount vs totalAmount`.
>
> Entregar: código + testes + `docs/relatorios/P0_4_LIQUIDACAO.md`

---

## Etapa 1 — Investigação (sem alterar código)

**Pare e reporte antes de codificar.** A resposta da pergunta 1 define a fórmula de todo o resto.

### 1.1 Semântica de `paidAmount` *(bloqueante)*

1. Cole `TransactionService.recordPayment` completo.
2. **`paidAmount` inclui `interestAmount` e `fineAmount`, ou representa apenas o principal amortizado?**
   Responda pelo código que grava, não pela documentação.
3. Cole `TransactionValidator.validatePayment` e `TransactionStateMachine.resolveStatusAfterPayment`.
4. Confirme por leitura de código: numa segunda chamada a `recordPayment` sobre título já `PARTIAL`, o valor **sobrescreve** ou **soma** ao `paidAmount` existente? Mesma pergunta para `interestAmount` e `fineAmount`.
5. `TransactionStateMachine` aceita a transição `PARTIAL → PARTIAL`? Se não, qual exceção o operador recebe hoje ao tentar segunda baixa parcial?

### 1.2 Onde `paidAmount` é lido

Liste **todos** os pontos que leem `paidAmount`, indicando se tratam o valor como cash pago ou como principal amortizado:

```bash
grep -rn "paidAmount\|paid_amount" src/main/kotlin --include=*.kt
```

Interessam especialmente: `sumPartialPaid`, `sumPartialPaidBefore`, `sumRealizedByProject`, `StatementExporter`, `ReportsExporter`, `CashFlowService`.

### 1.3 Estado da base

```sql
SELECT id, amount, paid_amount, interest_amount, fine_amount, status
  FROM financial_transactions
 WHERE status IN ('PARTIAL','PAID') AND is_active = true
 ORDER BY id;

-- Títulos possivelmente afetados pela sobrescrita
SELECT COUNT(*) FROM financial_transactions
 WHERE status = 'PARTIAL' AND is_active = true
   AND paid_amount IS NOT NULL;

-- Títulos com encargos
SELECT COUNT(*) FROM financial_transactions
 WHERE (interest_amount IS NOT NULL AND interest_amount > 0)
    OR (fine_amount    IS NOT NULL AND fine_amount    > 0);
```

### 1.4 Desconto

Confirme: existe campo de desconto em `financial_transactions`? (Esperado: **não** — V27 trouxe apenas juros e multa.)

Se não existe, registre como pendência: título quitado com abatimento tem principal pago menor que `amount` e permanecerá `PARTIAL` indefinidamente pela regra nova. **Não implemente desconto nesta tarefa** — apenas relate.

---

## Etapa 2 — Correção

> Executar somente após a Etapa 1 estar reportada. As fórmulas abaixo assumem que `paidAmount` **inclui** encargos. Se a Etapa 1 mostrar que é só principal, **pare e reporte** — a espec precisa ser ajustada.

### 2.1 Conceitos explícitos

Introduzir no domínio, como propriedades calculadas de `Transaction`:

```kotlin
/** Principal efetivamente amortizado (exclui encargos). */
val principalPaid: Money
    get() = (paidAmount ?: Money.ZERO) -
            (interestAmount ?: Money.ZERO) -
            (fineAmount ?: Money.ZERO)

/** Quanto ainda falta amortizar do valor do título. */
val outstandingPrincipal: Money
    get() = amount - principalPaid
```

**Toda regra de liquidação passa a usar estes dois nomes.** Nenhum ponto do código deve subtrair `paidAmount` de `amount` diretamente — foi essa conta que produziu o bug.

### 2.2 `TransactionValidator.validatePayment`

Substituir a validação atual (`paidAmount <= total`) por:

- `valorPrincipal > 0`
- `valorPrincipal <= transaction.outstandingPrincipal`
- juros e multa, se informados, `>= 0`

Mensagem de erro deve informar o saldo devedor, para o operador entender:
`"Valor excede o saldo devedor do título (R$ X restantes)."`

### 2.3 `TransactionService.recordPayment` — acumular

```kotlin
paidAmount     = (current.paidAmount     ?: ZERO) + valorPrincipal + juros + multa
interestAmount = (current.interestAmount ?: ZERO) + juros
fineAmount     = (current.fineAmount     ?: ZERO) + multa
```

**Toda escrita em `paidAmount` deve ficar concentrada neste único ponto.** Se houver outro lugar que grave o campo, consolide aqui — isso é o que tornará barata a migração futura para `transaction_payments`.

Registrar evento na timeline por baixa, com valor, data e saldo devedor resultante. Hoje, com o campo único, a timeline é o **único** registro de que existiram múltiplas baixas — sem ela, a segunda liquidação é invisível.

### 2.4 `resolveStatusAfterPayment`

Comparar `principalPaid` (acumulado, após a baixa) contra `amount`:

| Condição | Status resultante |
|---|---|
| `principalPaid == amount` | `PAID` |
| `0 < principalPaid < amount` | `PARTIAL` |
| `principalPaid > amount` | não deve ocorrer — validação barra antes; lançar exceção defensiva |

**Comparação de `Money`:** usar igualdade da própria classe, respeitando escala. Nunca `BigDecimal.equals` cru (`1.00` ≠ `1.0`) — verificar como `Money` implementa `equals`/`compareTo` e usar o operador correto.

### 2.5 `TransactionStateMachine`

Adicionar `PARTIAL → PARTIAL`. Nenhuma outra transição muda.

### 2.6 `PayablesViewModel`

Trocar o cálculo local por `tx.outstandingPrincipal`. A proteção passa a estar no serviço; o ViewModel deixa de ser a única barreira.

---

## Etapa 3 — Testes obrigatórios

Escrever **antes** da correção e confirmar que falham.

| # | Cenário | Esperado |
|---|---|---|
| 1 | 1.000: baixa 300, depois 700 | `PAID`, `paidAmount = 1.000`, saldo da conta −1.000 |
| 2 | 1.000: baixa 300, depois 300 | `PARTIAL`, `paidAmount = 600`, saldo −600 |
| 3 | 1.000: baixa 300, depois 800 | **rejeitado**, mensagem cita saldo devedor de 700 |
| 4 | 1.000: baixa 300, depois 700 + 50 juros | `PAID`, `principalPaid = 1.000`, `paidAmount = 1.050`, saldo −1.050 |
| 5 | 1.000: baixa integral 1.000 | `PAID` (sem regressão do caminho atual) |
| 6 | Três baixas 400/400/200 | `PAID`, três eventos na timeline |
| 7 | `openingBalance` com duas baixas antes da data de corte | idêntico a `calculateBalance` no mesmo instante |
| 8 | Baixa de valor zero ou negativo | rejeitado |

O teste 4 é o que valida a semântica dos encargos: o saldo da conta deve refletir **1.050** (dinheiro que saiu), enquanto o título é considerado quitado por **1.000** de principal.

Substituir `Thread.sleep(400)` por `runTest` + `advanceUntilIdle()` nos testes de ViewModel existentes — `sleep` é flake esperando CI lento.

---

## Etapa 4 — Correção de dados

Se a query 1.3 mostrar títulos `PARTIAL` na base, avaliar se algum sofreu sobrescrita (duas baixas registradas com apenas a última refletida). Indícios: timeline com dois eventos de pagamento e `paidAmount` correspondendo só ao último.

**Não corrija dados sem reportar.** Traga a lista de IDs suspeitos e o valor esperado versus o gravado.

---

## Não fazer nesta tarefa

- Não criar a tabela `transaction_payments` (tarefa seguinte, espec própria).
- Não implementar campo de desconto.
- Não alterar `calculateBalance` / `openingBalance` — as fórmulas continuam corretas; só o valor de `paidAmount` estava errado.
- Não alterar estorno.
- Não tocar em F3, F5-escrita ou extração de componentes.

---

## Relatório

`docs/relatorios/P0_4_LIQUIDACAO.md`:

1. Resposta à pergunta 1.1.2 (`paidAmount` inclui encargos?) com o trecho que comprova.
2. Sobrescreve ou acumula, antes da correção.
3. Lista completa dos pontos que leem `paidAmount` e se algum precisou mudar.
4. Testes 1–8: falharam antes? (sim/não por teste)
5. Títulos `PARTIAL` existentes na base e se algum apresenta indício de sobrescrita.
6. Confirmação de que `paidAmount` agora é gravado em um único ponto do código.
