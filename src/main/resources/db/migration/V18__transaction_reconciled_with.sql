-- Armazena o ofx_fitid do lançamento OFX com o qual o lançamento manual foi conciliado.
-- Preenchido apenas quando o usuário executa "Vincular" no ConciliationDialog.
ALTER TABLE financial_transactions ADD COLUMN reconciled_with_fitid VARCHAR(64) NULL;
