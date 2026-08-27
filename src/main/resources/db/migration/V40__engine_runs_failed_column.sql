-- PD-08 (complemento): suporte a PARTIAL_FAILURE.
-- Contagem de falhas por engine run para distinguir falha total de falha parcial.
ALTER TABLE engine_runs ADD COLUMN failed INTEGER NOT NULL DEFAULT 0;
