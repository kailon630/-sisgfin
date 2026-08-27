-- PD-09: guard de unicidade para lançamentos de folha e recorrência.
-- Protege contra race condition TOCTOU quando múltiplas instâncias do app
-- executam as engines simultaneamente — complements the code-level check.
--
-- origin IN ('PAYROLL_ENGINE','PAYROLL_IMPORT'): permite lançamento MANUAL
-- ou API para o mesmo funcionário no mesmo dia (adiantamento, reembolso).
-- status <> 'CANCELED': permite recriar após cancelamento legítimo.
-- CONCURRENTLY omitido: Flyway executa em transação; CONCURRENTLY exige
-- autocommit. Trava breve aceitável no boot de instalação/migração.

CREATE UNIQUE INDEX IF NOT EXISTS ux_ft_payroll_employee_due
    ON financial_transactions (employee_id, due_date)
    WHERE employee_id IS NOT NULL
      AND is_active = true
      AND status <> 'CANCELED'
      AND origin IN ('PAYROLL_ENGINE', 'PAYROLL_IMPORT');

CREATE UNIQUE INDEX IF NOT EXISTS ux_ft_recurrence_template_due
    ON financial_transactions (recurrence_template_id, due_date)
    WHERE recurrence_template_id IS NOT NULL
      AND is_active = true
      AND status <> 'CANCELED';
