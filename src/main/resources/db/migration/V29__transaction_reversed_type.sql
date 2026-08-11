-- C1: direção do estorno no cálculo de saldo
ALTER TABLE financial_transactions
    ADD COLUMN reversed_type VARCHAR(20) NULL;

COMMENT ON COLUMN financial_transactions.reversed_type IS
    'Tipo do lançamento original estornado. Preenchido apenas quando type = REVERSAL. Define a direção do estorno no cálculo de saldo.';

-- Backfill dos estornos existentes a partir do lançamento pai
UPDATE financial_transactions r
   SET reversed_type = o.type
  FROM financial_transactions o
 WHERE r.type = 'REVERSAL'
   AND r.parent_transaction_id = o.id
   AND r.reversed_type IS NULL;

CREATE INDEX idx_ft_reversed_type
    ON financial_transactions (reversed_type)
    WHERE type = 'REVERSAL';
