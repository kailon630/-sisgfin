CREATE TABLE IF NOT EXISTS contracts (
    id                     SERIAL PRIMARY KEY,
    number                 VARCHAR(30)   NOT NULL UNIQUE,
    description            VARCHAR(255)  NOT NULL,
    contractor_id          INTEGER       NOT NULL REFERENCES suppliers(id),
    type                   VARCHAR(20)   NOT NULL,
    total_value            DECIMAL(19,2) NOT NULL,
    start_date             TIMESTAMP     NOT NULL,
    end_date               TIMESTAMP,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'VIGENTE',
    notes                  TEXT,
    recurrence_template_id INTEGER       REFERENCES recurrence_templates(id),
    created_by             INTEGER       REFERENCES users(id),
    created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_contracts_status     ON contracts (status);
CREATE INDEX IF NOT EXISTS ix_contracts_contractor ON contracts (contractor_id);

ALTER TABLE recurrence_templates ADD COLUMN contract_id INTEGER REFERENCES contracts(id);
