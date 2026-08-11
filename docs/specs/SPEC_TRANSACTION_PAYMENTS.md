# Entidade de Baixa — `transaction_payments`

> **Não é para implementar ainda.** Documento de decisão. Depende do P0-4 concluído.
>
> Classificação: **conformidade**, não melhoria. Vai na fila junto com fechamento de período, não em "backlog de arquitetura".

---

## 1. Por que deixou de ser opcional

O P0-4 corrige o saldo acumulando `paidAmount`. Mas acumular colapsa N baixas num único número: três pagamentos de R$ 400, R$ 400 e R$ 200 viram `paidAmount = 1.000`, e some **quando** cada um saiu, **de qual conta**, e **com quais encargos**.

Consequências concretas:

| Área | Impacto da perda |
|---|---|
| **Livro Diário TCESP** | Precisa listar cada saída de caixa com sua data. Com campo único, três pagamentos aparecem como um só, na data do último. |
| **Extrato / conciliação OFX** | Três débitos no extrato bancário contra um registro no sistema — a conciliação não fecha. |
| **`calculateBalance`** | Usa `paidAmount` sem data própria. `openingBalance(before)` não consegue recortar corretamente: uma baixa de janeiro e outra de março ficam ambas presas à data do título. |
| **Auditoria** | Timeline vira o único registro das baixas — texto livre, não dado consultável. |
| **Pagamento por conta diferente** | Título registrado na conta A, pago pela conta B: hoje impossível de representar. |

O terceiro item é o mais grave: **`openingBalance` fica incorreto** para qualquer título com baixas em meses distintos. E `openingBalance` é a base de todo relatório com saldo inicial de período.

---

## 2. Modelo

### 2.1 Tabela

```sql
CREATE TABLE transaction_payments (
    id                  SERIAL PRIMARY KEY,
    transaction_id      INTEGER NOT NULL REFERENCES financial_transactions(id),
    payment_date        DATE NOT NULL,
    account_id          INTEGER NOT NULL REFERENCES financial_accounts(id),
    principal_amount    NUMERIC(19,4) NOT NULL CHECK (principal_amount > 0),
    interest_amount     NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (interest_amount >= 0),
    fine_amount         NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (fine_amount    >= 0),
    discount_amount     NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    reversed_by_id      INTEGER NULL REFERENCES transaction_payments(id),
    notes               TEXT NULL,
    created_by          INTEGER NULL REFERENCES users(id),
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_tp_transaction ON transaction_payments(transaction_id);
CREATE INDEX idx_tp_date_account ON transaction_payments(payment_date, account_id);
```

**Caixa efetivo da baixa** = `principal_amount + interest_amount + fine_amount − discount_amount`.

Notas de modelagem:

- `account_id` próprio resolve o pagamento por conta diferente da do título.
- `payment_date` é `DATE`, não `TIMESTAMP` — evita a classe de bug de fronteira que já apareceu no F0.
- `discount_amount` fecha a lacuna registrada no P0-4.
- Baixa é **imutável**: não se edita nem se apaga. Erro se corrige com estorno de baixa, que preenche `reversed_by_id` (mesmo princípio do C1 e do C2).

### 2.2 O que acontece com os campos atuais

`paidAmount`, `interestAmount` e `fineAmount` em `financial_transactions` deixam de ser fonte da verdade e viram **projeção**: soma das baixas ativas.

Durante a transição, permanecem gravados (dual-write) como cache reconstruível. A decisão de mantê-los ou removê-los fica para o fim, quando todas as leituras tiverem migrado.

### 2.3 Status derivado

```
principalPago = Σ(principal_amount) das baixas não estornadas
  == amount        → PAID
  0 < x < amount   → PARTIAL
  == 0             → PENDING / OVERDUE (regra atual)
```

`PARTIAL → PARTIAL` continua válido (adicionado no P0-4).

---

## 3. Decisões em aberto

Precisam de resposta antes de escrever código.

### D1 — Estorno de baixa × estorno de título

Hoje `reverseTransaction` estorna o título inteiro criando um `REVERSAL`. Com baixas, surgem duas operações distintas:

- **Estornar uma baixa** — pagamento feito errado; título volta a `PARTIAL` ou `PENDING`.
- **Estornar o título** — o que existe hoje.

São a mesma coisa quando há uma única baixa. Divergem com várias. Proposta: manter as duas, sendo o estorno de título um atalho que estorna todas as baixas ativas e marca o título.

**Impacto no C1:** `reversed_type` e a direção do estorno no saldo continuam válidos, mas o estorno de baixa não precisa de linha `REVERSAL` — basta marcar `reversed_by_id` e a soma exclui. Menos linhas fantasma no Livro Diário.

### D2 — Baixa em conta diferente do título

O modelo permite. A UI deve expor? Argumento a favor: acontece na prática (título na conta do convênio, pago pelo caixa). Contra: aumenta a chance de erro de digitação em campo que hoje não existe.

Recomendação: permitir no modelo, **não** expor na UI inicialmente. Default = conta do título.

### D3 — Desconto afeta o principal ou é despesa negativa?

Se `principal_amount = 950` e `discount_amount = 50` num título de 1.000, o título quita? Contabilmente sim (quitação com abatimento), mas `principalPago` seria 950.

Recomendação: `principalPago = Σ(principal_amount + discount_amount)` para efeito de quitação, enquanto o caixa registra apenas `principal_amount`. Confirmar com o contador da associação.

### D4 — Fechamento de período

Baixa com `payment_date` em período fechado deve ser bloqueada. Como o fechamento ainda não existe, a tabela nasce sem essa guarda — mas o campo `payment_date` é exatamente o gancho de que o fechamento precisará. **Vale sequenciar fechamento logo depois**, não antes.

---

## 4. Plano de migração (expand/contract)

Produção com prestação de contas pública. Nenhuma etapa pode quebrar o que já funciona.

| Etapa | O que faz | Reversível? |
|---|---|---|
| **M1** | Criar tabela. Backfill: cada título com `paidAmount > 0` gera **uma** baixa com `payment_date = paymentDate`, `account_id = accountId`, principal = `paidAmount − juros − multa`. | sim (drop table) |
| **M2** | **Dual-write:** `recordPayment` grava a baixa **e** continua atualizando `paidAmount`. Leituras seguem usando `paidAmount`. | sim |
| **M3** | **Reconciliação:** job/teste que compara `Σ(baixas)` com `paidAmount` de cada título. Rodar até divergência zero por período estável. | — |
| **M4** | Migrar leituras: `sumPartialPaid`, `openingBalance`, Extrato, Livro Diário passam a somar `transaction_payments`. Uma por vez, com comparação de resultado antes/depois. | sim |
| **M5** | UI: dialog de liquidação (F7) grava baixa; painel de detalhes ganha lista de baixas. | sim |
| **M6** | `paidAmount` vira somente leitura, calculado. Decidir se permanece como cache ou é removido. | — |

**M3 é o portão.** Não avançar para M4 enquanto houver qualquer divergência entre a soma das baixas e o `paidAmount` de qualquer título.

### Verificação de reconciliação

```sql
SELECT t.id, t.amount, t.paid_amount,
       COALESCE(SUM(p.principal_amount + p.interest_amount + p.fine_amount
                    - p.discount_amount), 0) AS soma_baixas
  FROM financial_transactions t
  LEFT JOIN transaction_payments p
         ON p.transaction_id = t.id AND p.reversed_by_id IS NULL
 WHERE t.is_active = true AND t.paid_amount IS NOT NULL
 GROUP BY t.id, t.amount, t.paid_amount
HAVING COALESCE(SUM(p.principal_amount + p.interest_amount + p.fine_amount
                    - p.discount_amount), 0) <> t.paid_amount;
```

Resultado esperado após M2 estabilizado: **zero linhas**.

---

## 5. O que isso destrava

- **F8 (baixa em lote)** com liquidação parcial — hoje não é representável.
- **Conciliação OFX** de título pago em múltiplos débitos.
- **Livro Diário TCESP** com uma linha por saída de caixa real.
- **`openingBalance` correto** para títulos com baixas em períodos distintos.
- **Fechamento de período**, que precisa de data de caixa por evento.

---

## 6. Ordem recomendada

```
P0-4 (acumulação e validação)          ← em execução
  ↓
sumRealized nettando REVERSAL          ← pendente desde R7, barato, sai no TCESP
  ↓
F5-escrita (beneficiário unificado)    ← bug original ainda aberto
  ↓
SummaryTileRow extraído  →  F3
  ↓
M1–M3 (transaction_payments, dual-write + reconciliação)
  ↓
F7 (dialog de liquidação, já gravando baixa)
  ↓
M4–M6 (migração de leituras)
  ↓
Fechamento de período
```

**Backup automático não está nesta fila e deveria estar acima de tudo.** Segue marcado como ❌ no retrato, ao lado do instalador MSI, como se fossem itens equivalentes. Não são.
