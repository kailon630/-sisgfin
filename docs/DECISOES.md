# Decisões de Domínio — SisgFin

Registro de decisões de negócio com impacto em código e relatórios.
Complementa o KANBAN.md (que registra o que foi feito) com o **porquê**.

---

## D-REALIZADO — Realizado orçamentário = Σ(principal_amount das baixas ativas)

**Decisão:** `sumRealized`, `sumRealizedMonth` (`BudgetItemRepository`) e
`sumRealizedByProject` (`TransactionRepository`) computam o realizado orçamentário
como `Σ(principalAmount)` das baixas ativas de lançamentos EXPENSE.

Sob D-PRINCIPAL (B), `principalAmount` **já é face amortizado** (inclui desconto).
Portanto: realizado = Σ(principal_amount) — sem subtrair discount_amount separado.

Juros (`interestAmount`) e multa (`fineAmount`) **não consomem dotação orçamentária**.

**Motivação:** Juros e multa por atraso são despesas financeiras de natureza distinta
da dotação original. Ex.: dotação "Material de Consumo" R$ 10.000 → título R$ 1.000
pago com R$ 50 de juros → realizado = R$ 1.000 (não R$ 1.050). O caixa registra
R$ 1.050 (via cashEffective em transaction_payments), mas o orçamento não.

**Contraste com cashEffective:** `cashEffective = principal + interest + fine - discount`
é usado para **saldo de caixa** (`calculateBalance`, `openingBalance`). A separação é:
- Caixa (conta bancária): cashEffective — quanto saiu de fato (ex: 950).
- Orçamento (dotação): principal_amount — face amortizado (ex: 1.000).

**Desconto** está embutido no face (1.000, não 950) e não reduz o realizado —
o convênio autorizou 1.000 e o objeto foi executado integralmente.

**Paridade obrigatória:** `sumRealized` e `sumRealizedByProject` respondem à mesma
pergunta. Ambas leem `transaction_payments.principal_amount`; nunca devem divergir
para o mesmo pagamento.

**Pendência (não implementado):** categoria própria para juros/multa no orçamento.
Se o operador quiser que juros apareçam no orçamento, deve lançá-los manualmente em
uma rubrica "Despesas Financeiras". Essa decisão de negócio está pendente com o cliente.

**Registrado em:** 2026-08-25 (3b). Atualizado em 2026-08-25 (P3+P1+P2).

---

## D-PRINCIPAL — principal_amount é face amortizado (semântica B)

**Decisão (provisória — pendente de confirmação contábil):**
`transaction_payments.principal_amount` armazena o **face amortizado** da baixa,
não o dinheiro desembolsado. Juros e multa continuam em colunas próprias.

Exemplo: título de R$ 1.000 quitado com R$ 50 de desconto, operador paga R$ 950:
- `principal_amount = 1.000` (face amortizado = cash + desconto)
- `discount_amount = 50`
- `cashEffective = 1.000 + 0 + 0 − 50 = 950` (saída real de caixa)

`financial_transactions.paid_amount` acumula faces. `outstandingPrincipal = amount - paidAmount = 0` ao quitar — consistente com status PAID.

**Motivação:** O convênio autorizou R$ 1.000 e o objeto foi executado integralmente.
O desconto é ganho na negociação, não redução do objeto. Realizado orçamentário = face.

**Invariante:** `outstandingPrincipal = amount - paidAmount`. Status: PAID quando
`paidAmount >= amount` (sem precisar somar desconto separadamente — já está no face).

**cashEffective** (para caixa) = `principal + interest + fine - discount` por baixa.
Separação de responsabilidades:
- Orçamento: `Σ(principal_amount)` = face executado.
- Caixa: `Σ(cashEffective)` = dinheiro movimentado.

**Registrado em:** 2026-08-25. Parte A (principalPaid fix) + 3a (L423 reversePayment) +
P3 (semântica B — face amortizado em principal_amount, 2026-08-25).

---

## D1 — Estorno de baixa individual

**Decisão:** Estorno via `reversePayment` reverte uma baixa individual em
`transaction_payments` (marca `reversed_by_id` cruzado). O caixa é restaurado pelo
valor cashEffective completo da baixa. O título recalcula `paidAmount` (principal puro)
a partir das baixas ativas remanescentes.

**Registrado em:** implementado no M5-A; bug de paidAmount corrigido em 3a (2026-08-25).

---

## D3 — Desconto

**(a)** Desconto conta para quitação do principal: `principalQuitado = paidAmount + Σ(discountAmount)`.

**(b)** Desconto reduz o realizado orçamentário: `Σ(principal - discount)`. Ver D-REALIZADO acima.

**Registrado em:** implementado no M4/D3(a-b); confirmado 2026-08-25.

---

## D4 — Fechamento de período

**Status:** NÃO IMPLEMENTADO. Sem guard de `payment_date` por período fechado.
Baixas em períodos históricos são aceitas sem restrição.

**Decisão pendente:** regra de negócio sobre quais perfis podem baixar em períodos
anteriores ao mês corrente ainda não definida com o cliente.

---

## D5 — Transferências nascem PAID

**Decisão:** Transferências são criadas via `createTransfer` com baixas atômicas.
`recordPayment` rejeita tipo TRANSFER com `IllegalStateException`.

**Registrado em:** implementado no M5-A (D5).
