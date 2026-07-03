-- Fase 6-D.2: rastreabilidade de lançamentos originados de importação OFX
ALTER TABLE financial_transactions ADD COLUMN ofx_fitid VARCHAR(64) NULL;

-- Índice único parcial: garante que o mesmo FITID nunca entre duas vezes na mesma conta.
-- WHERE ... IS NOT NULL exclui lançamentos manuais (ofx_fitid = NULL) do índice único.
CREATE UNIQUE INDEX IF NOT EXISTS ux_financial_transactions_account_fitid
    ON financial_transactions (account_id, ofx_fitid)
    WHERE ofx_fitid IS NOT NULL;
