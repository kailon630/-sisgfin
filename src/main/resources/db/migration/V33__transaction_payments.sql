-- M1: tabela de baixas individuais — entidade de baixa do SPEC_TRANSACTION_PAYMENTS.md
-- idempotency_key adicionado ao DDL do SPEC (não estava no original).

CREATE TABLE IF NOT EXISTS transaction_payments (
    id                  SERIAL PRIMARY KEY,
    transaction_id      INTEGER NOT NULL REFERENCES financial_transactions(id),
    payment_date        DATE NOT NULL,
    account_id          INTEGER NOT NULL REFERENCES financial_accounts(id),
    principal_amount    NUMERIC(19,4) NOT NULL CHECK (principal_amount > 0),
    interest_amount     NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (interest_amount >= 0),
    fine_amount         NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (fine_amount >= 0),
    discount_amount     NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    reversed_by_id      INTEGER NULL REFERENCES transaction_payments(id),
    idempotency_key     VARCHAR(100) NULL,
    notes               TEXT NULL,
    created_by          INTEGER NULL REFERENCES users(id),
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_tp_transaction') THEN
        EXECUTE 'CREATE INDEX idx_tp_transaction ON transaction_payments(transaction_id)';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_tp_date_account') THEN
        EXECUTE 'CREATE INDEX idx_tp_date_account ON transaction_payments(payment_date, account_id)';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_tp_idempotency') THEN
        EXECUTE 'CREATE UNIQUE INDEX idx_tp_idempotency ON transaction_payments(idempotency_key) WHERE idempotency_key IS NOT NULL';
    END IF;
END $$;

-- Backfill: fonte primária = timeline (um evento por baixa, data e valor individuais).
-- Nota: timeline.amount armazena cashThisBaixa (principal + juros + multa juntos);
-- não é possível separar os componentes, então tudo vai em principal_amount.
-- Reconciliação: Σ(cashThisBaixa) = paid_amount — invariante preservada.
INSERT INTO transaction_payments
    (transaction_id, payment_date, account_id, principal_amount, interest_amount, fine_amount, discount_amount, notes, created_by, created_at)
SELECT
    t.id,
    te.created_at::date,
    t.account_id,
    te.amount,
    0,
    0,
    0,
    'Backfill M1 — origem: timeline, evento id=' || te.id,
    te.performed_by,
    te.created_at
FROM financial_transactions t
JOIN transaction_events te
    ON  te.transaction_id = t.id
    AND te.event_type IN ('PAYMENT', 'PARTIAL_PAYMENT')
    AND te.amount IS NOT NULL
    AND te.amount > 0
WHERE t.is_active = true
    AND t.paid_amount IS NOT NULL
    AND t.paid_amount > 0
    AND NOT EXISTS (
        SELECT 1 FROM transaction_payments tp2
        WHERE tp2.transaction_id = t.id
          AND tp2.notes LIKE 'Backfill M1 — origem: timeline%'
    );

-- Backfill: fonte secundária = campo colapsado (para títulos sem eventos de pagamento).
-- Aplica-se quando o operador nunca usou o fluxo de baixa pelo sistema (dados legados).
-- Esses registros têm data aproximada (payment_date do título = última baixa registrada).
-- paid_amount é principal puro nestes dados históricos; interest_amount/fine_amount são separados.
-- Lista de IDs afetados deve constar no relatório de migração.
INSERT INTO transaction_payments
    (transaction_id, payment_date, account_id, principal_amount, interest_amount, fine_amount, discount_amount, notes, created_by, created_at)
SELECT
    t.id,
    COALESCE(t.payment_date, t.updated_at)::date,
    t.account_id,
    t.paid_amount,
    COALESCE(t.interest_amount, 0),
    COALESCE(t.fine_amount, 0),
    0,
    'Backfill M1 — origem: campo colapsado (data aproximada)',
    NULL,
    COALESCE(t.payment_date, t.updated_at)
FROM financial_transactions t
WHERE t.is_active = true
    AND t.paid_amount IS NOT NULL
    AND t.paid_amount > 0
    AND NOT EXISTS (
        SELECT 1
        FROM transaction_events te
        WHERE te.transaction_id = t.id
            AND te.event_type IN ('PAYMENT', 'PARTIAL_PAYMENT')
    )
    AND NOT EXISTS (
        SELECT 1 FROM transaction_payments tp2
        WHERE tp2.transaction_id = t.id
          AND tp2.notes LIKE 'Backfill M1 — origem: campo colapsado%'
    );
