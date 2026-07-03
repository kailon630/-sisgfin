-- V9: Plano de contas (categorias de despesa/receita)

CREATE TABLE IF NOT EXISTS expense_categories (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(10)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    group_code  VARCHAR(10),
    group_name  VARCHAR(100),
    is_income   BOOLEAN NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_expense_categories_code
    ON expense_categories(code);

ALTER TABLE financial_transactions
    ADD COLUMN category_id INTEGER REFERENCES expense_categories(id);
