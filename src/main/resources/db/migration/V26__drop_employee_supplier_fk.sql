-- Remove a FK supplier_id de employees.
-- Funcionários PJ não precisam mais de cadastro duplicado em Fornecedores;
-- o vínculo era exigido apenas pelo PayrollImportService, que agora usa employeeId diretamente.
ALTER TABLE employees DROP COLUMN supplier_id;
