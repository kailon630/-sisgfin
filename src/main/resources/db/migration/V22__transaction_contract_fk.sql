ALTER TABLE financial_transactions ADD COLUMN contract_id INTEGER REFERENCES contracts(id);

CREATE INDEX IF NOT EXISTS ix_ft_contract_id
    ON financial_transactions (contract_id)
    WHERE contract_id IS NOT NULL;
