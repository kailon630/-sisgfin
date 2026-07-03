-- V5: Money Precision Migration (DOUBLE PRECISION → DECIMAL)

ALTER TABLE accounts
    ALTER COLUMN balance TYPE DECIMAL(19, 2) USING balance::DECIMAL(19, 2);

ALTER TABLE transactions
    ALTER COLUMN amount TYPE DECIMAL(19, 2) USING amount::DECIMAL(19, 2);

ALTER TABLE employees
    ALTER COLUMN salary TYPE DECIMAL(19, 2) USING salary::DECIMAL(19, 2);

ALTER TABLE financial_accounts
    ALTER COLUMN initial_balance TYPE DECIMAL(19, 2) USING initial_balance::DECIMAL(19, 2);
