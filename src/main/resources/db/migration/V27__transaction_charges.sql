-- Campos para registrar juros e multa no momento da quitação.
-- interest_amount e fine_amount são informativos (breakdown do paidAmount).
-- O paidAmount continua sendo o total real desembolsado (principal + juros + multa).
ALTER TABLE financial_transactions
    ADD COLUMN interest_amount DECIMAL(19, 2) NULL,
    ADD COLUMN fine_amount     DECIMAL(19, 2) NULL;
