-- PD-04: vínculo entre a transferência de reversão e a original.
-- parentTransactionId já carrega três significados (parcela, perna de destino, estorno de título)
-- — nova coluna evita sobrecarregar um quarto.
ALTER TABLE financial_transactions
    ADD COLUMN reverses_transfer_id INTEGER NULL
        REFERENCES financial_transactions(id);

COMMENT ON COLUMN financial_transactions.reverses_transfer_id IS
    'Aponta para a perna de saída (parentTransactionId IS NULL) da transferência original que este par reverte. Preenchido apenas na perna de saída da transferência de reversão.';

CREATE INDEX idx_ft_reverses_transfer ON financial_transactions(reverses_transfer_id)
    WHERE reverses_transfer_id IS NOT NULL;
