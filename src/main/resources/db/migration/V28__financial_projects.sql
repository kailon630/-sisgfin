-- Módulo de Projetos: entidade independente de Centro de Custo

CREATE TABLE IF NOT EXISTS financial_projects (
    id              SERIAL PRIMARY KEY,
    code            VARCHAR(50) UNIQUE NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'EM_ANDAMENTO',
    budget          NUMERIC(15, 2),
    start_date      DATE,
    expected_end    DATE,
    actual_end      DATE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      INTEGER REFERENCES users(id)
);

-- Vínculo projeto nas transações (independente do project_id existente = Centro de Custo)
ALTER TABLE financial_transactions
    ADD COLUMN IF NOT EXISTS financial_project_id INTEGER REFERENCES financial_projects(id);

CREATE INDEX IF NOT EXISTS idx_fin_tx_financial_project ON financial_transactions(financial_project_id);

-- Vínculo projeto nos templates de recorrência
ALTER TABLE recurrence_templates
    ADD COLUMN IF NOT EXISTS financial_project_id INTEGER REFERENCES financial_projects(id);
