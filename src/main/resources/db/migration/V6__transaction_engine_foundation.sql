-- Fase 3A.1: Motor transacional (contas a pagar/receber)

CREATE TABLE IF NOT EXISTS financial_transactions (
    id                    SERIAL PRIMARY KEY,
    type                  VARCHAR(20)    NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    description           VARCHAR(255)   NOT NULL,
    amount                DECIMAL(19, 2) NOT NULL,
    issue_date            TIMESTAMP      NOT NULL,
    due_date              TIMESTAMP      NOT NULL,
    payment_date          TIMESTAMP,
    account_id            INTEGER        NOT NULL,
    supplier_id           INTEGER,
    project_id            INTEGER,
    notes                 TEXT,
    created_by            INTEGER,
    created_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active             BOOLEAN        NOT NULL DEFAULT TRUE,
    parent_transaction_id INTEGER,
    ledger_entry_id       INTEGER,
    FOREIGN KEY (account_id)            REFERENCES financial_accounts(id),
    FOREIGN KEY (supplier_id)           REFERENCES suppliers(id),
    FOREIGN KEY (project_id)            REFERENCES projects(id),
    FOREIGN KEY (created_by)            REFERENCES users(id),
    FOREIGN KEY (parent_transaction_id) REFERENCES financial_transactions(id)
);

CREATE INDEX IF NOT EXISTS idx_fin_tx_account ON financial_transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_fin_tx_status ON financial_transactions(status);
CREATE INDEX IF NOT EXISTS idx_fin_tx_type ON financial_transactions(type);
CREATE INDEX IF NOT EXISTS idx_fin_tx_due_date ON financial_transactions(due_date);
CREATE INDEX IF NOT EXISTS idx_fin_tx_active ON financial_transactions(is_active);
