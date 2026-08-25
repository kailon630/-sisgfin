-- T-13 (correção): reclassifica origin usando campos estruturais em vez de ILIKE.
-- V30 priorizava employee_id antes de type/ofx_fitid, classificando incorretamente
-- REVERSALs e TRANSFERs com employee_id como PAYROLL_ENGINE.
UPDATE financial_transactions
SET origin = CASE
    WHEN type = 'REVERSAL'                                                         THEN 'REVERSAL'
    WHEN type = 'TRANSFER'                                                         THEN 'TRANSFER'
    WHEN ofx_fitid IS NOT NULL                                                     THEN 'OFX'
    WHEN recurrence_template_id IS NOT NULL                                        THEN 'RECURRENCE'
    WHEN employee_id IS NOT NULL                                                   THEN 'PAYROLL_ENGINE'
    WHEN installment_total > 1 AND parent_transaction_id IS NOT NULL               THEN 'INSTALLMENT'
    ELSE 'MANUAL'
END;
