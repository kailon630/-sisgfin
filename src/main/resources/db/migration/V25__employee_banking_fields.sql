-- Fase 9: Dados bancários estruturados por funcionário para geração de remessa bancária.
-- Separar código do banco (COMPE), agência, DV da agência, conta, DV da conta e tipo de conta
-- permite gerar o arquivo de remessa BB sem depender do vínculo com fornecedor.
ALTER TABLE employees ADD COLUMN bank_code      VARCHAR(3)  NULL;
ALTER TABLE employees ADD COLUMN agency_number  VARCHAR(10) NULL;
ALTER TABLE employees ADD COLUMN agency_dv      VARCHAR(2)  NULL;
ALTER TABLE employees ADD COLUMN account_number VARCHAR(20) NULL;
ALTER TABLE employees ADD COLUMN account_dv     VARCHAR(2)  NULL;
ALTER TABLE employees ADD COLUMN account_type   VARCHAR(2)  NULL DEFAULT 'CS';
