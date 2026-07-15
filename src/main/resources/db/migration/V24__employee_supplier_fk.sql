-- Fase 8-A: Vincula funcionário ao seu cadastro de fornecedor/credor
-- Necessário para que o módulo de importação de folha saiba qual supplierId
-- usar ao criar os lançamentos de contas a pagar de cada funcionário.
-- Nullable: funcionários existentes não são afetados; vínculo é opcional.
ALTER TABLE employees ADD COLUMN supplier_id INTEGER REFERENCES suppliers(id);
