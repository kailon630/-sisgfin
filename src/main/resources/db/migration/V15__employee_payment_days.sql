-- Fase 6-C: suporte a múltiplos dias de pagamento e tipo de vínculo por funcionário
ALTER TABLE employees ADD COLUMN payment_days  VARCHAR(50)  NULL;
ALTER TABLE employees ADD COLUMN employment_type VARCHAR(10) NULL;

-- FK de lançamentos para funcionário (permite deduplicação e relatório de folha)
ALTER TABLE financial_transactions ADD COLUMN employee_id INTEGER NULL REFERENCES employees(id);
