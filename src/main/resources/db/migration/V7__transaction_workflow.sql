-- Fase 3A.2: Workflow transacional — pagamento parcial e timeline operacional

ALTER TABLE financial_transactions ADD COLUMN paid_amount DECIMAL(19, 2) DEFAULT 0.0;

CREATE TABLE IF NOT EXISTS transaction_events (
    id             SERIAL PRIMARY KEY,
    transaction_id INTEGER      NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    description    VARCHAR(255) NOT NULL,
    amount         DECIMAL(19, 2),
    status_from    VARCHAR(20),
    status_to      VARCHAR(20),
    performed_by   INTEGER,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (transaction_id) REFERENCES financial_transactions(id),
    FOREIGN KEY (performed_by)   REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_transaction_events_id ON transaction_events(transaction_id);
