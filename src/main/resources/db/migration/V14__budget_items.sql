-- Fase 3-B: Orçamento por rubrica (dotação projeto × categoria × ano)

CREATE TABLE IF NOT EXISTS budget_items (
    id              SERIAL PRIMARY KEY,
    project_id      INTEGER        NOT NULL REFERENCES projects(id),
    category_id     INTEGER        NOT NULL REFERENCES expense_categories(id),
    year            INTEGER        NOT NULL,
    monthly_amount  DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    annual_amount   DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    notes           TEXT,
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_by      INTEGER REFERENCES users(id),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Unicidade: uma dotação por projeto × categoria × ano
    UNIQUE (project_id, category_id, year)
);

CREATE INDEX IF NOT EXISTS idx_budget_items_project   ON budget_items(project_id);
CREATE INDEX IF NOT EXISTS idx_budget_items_category  ON budget_items(category_id);
CREATE INDEX IF NOT EXISTS idx_budget_items_year      ON budget_items(year);
