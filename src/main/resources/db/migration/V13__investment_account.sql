-- RN-06: suporte a conta do tipo APLICACAO (investimento)
-- Adiciona campo da corretora/fundo; o tipo INVESTMENT é guardado no varchar account_type existente.
ALTER TABLE financial_accounts ADD COLUMN investment_broker VARCHAR(100);
