-- Fase 7-A: Recorrência Automática
-- Templates que definem regras de geração periódica de lançamentos

CREATE TABLE IF NOT EXISTS recurrence_templates (
    id             SERIAL PRIMARY KEY,
    description    VARCHAR(255)   NOT NULL,
    amount         DECIMAL(19, 2) NOT NULL,
    type           VARCHAR(20)    NOT NULL,
    interval_type  VARCHAR(20)    NOT NULL,
    day_of_month   INTEGER        NOT NULL,
    account_id     INTEGER        NOT NULL   REFERENCES financial_accounts(id),
    supplier_id    INTEGER                   REFERENCES suppliers(id),
    category_id    INTEGER                   REFERENCES expense_categories(id),
    cost_center_id INTEGER                   REFERENCES projects(id),
    document_type  VARCHAR(20),
    notes          TEXT,
    starts_at      TIMESTAMP      NOT NULL,
    ends_at        TIMESTAMP,
    is_active      BOOLEAN        NOT NULL   DEFAULT TRUE,
    created_by     INTEGER                   REFERENCES users(id),
    created_at     TIMESTAMP      NOT NULL   DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP      NOT NULL   DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_recurrence_templates_active
    ON recurrence_templates (is_active);
