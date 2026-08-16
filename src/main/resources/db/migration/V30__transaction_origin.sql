-- T-13: discriminador de origem dos lançamentos
-- Permite que cancelPendingPayrollForMonth filtre apenas origens de folha,
-- protegendo lançamentos MANUAL com employeeId de serem cancelados pela importação.

ALTER TABLE financial_transactions
    ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

COMMENT ON COLUMN financial_transactions.origin IS
    'Origem do lançamento: MANUAL, PAYROLL_ENGINE, PAYROLL_IMPORT, RECURRENCE, OFX, API, INSTALLMENT, TRANSFER, REVERSAL, DUPLICATE.';

-- Backfill: mais específico primeiro.
-- Nota: employee_id é mapeado para PAYROLL_ENGINE porque não há como distinguir
-- engine de import retroativamente (ambos gravam employee_id, sem outro campo discriminador).
UPDATE financial_transactions
SET origin = CASE
    WHEN employee_id IS NOT NULL                                             THEN 'PAYROLL_ENGINE'
    WHEN recurrence_template_id IS NOT NULL                                  THEN 'RECURRENCE'
    WHEN ofx_fitid IS NOT NULL                                               THEN 'OFX'
    WHEN type = 'REVERSAL'                                                   THEN 'REVERSAL'
    WHEN type = 'TRANSFER'                                                   THEN 'TRANSFER'
    WHEN installment_total IS NOT NULL AND parent_transaction_id IS NOT NULL THEN 'INSTALLMENT'
    ELSE 'MANUAL'
END;

CREATE INDEX idx_ft_origin ON financial_transactions(origin);
