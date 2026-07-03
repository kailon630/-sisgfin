-- Fase 7-A: Liga financial_transactions aos templates de recorrência
-- Usado para deduplicação e para exibir histórico no painel do template

ALTER TABLE financial_transactions
    ADD COLUMN recurrence_template_id INTEGER REFERENCES recurrence_templates(id);

CREATE INDEX IF NOT EXISTS ix_ft_recurrence_template_id
    ON financial_transactions (recurrence_template_id)
    WHERE recurrence_template_id IS NOT NULL;
