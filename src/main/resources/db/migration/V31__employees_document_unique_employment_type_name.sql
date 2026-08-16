-- T-15: normalizar employment_type para gravar .name (ex: "ESTAGIO" em vez de "Estágio")
UPDATE employees
SET employment_type = CASE
    WHEN employment_type = 'CLT'       THEN 'CLT'
    WHEN employment_type = 'PJ'        THEN 'PJ'
    WHEN employment_type = 'Estágio'   THEN 'ESTAGIO'
    WHEN employment_type = 'Outros'    THEN 'OUTROS'
    ELSE employment_type  -- mantém valores já normalizados
END
WHERE employment_type IS NOT NULL;

-- T-10: normalizar documentos de funcionários (remover pontuação)
-- Supressionários (ex: "254.461.288-69") → "25446128869"
UPDATE employees
SET document = regexp_replace(document, '[^0-9]', '', 'g')
WHERE document ~ '[^0-9]';

-- T-10: UNIQUE em employees.document (falha se houver CPFs duplicados)
-- Se a migração falhar aqui, há duplicatas na tabela — corrija manualmente antes de prosseguir.
CREATE UNIQUE INDEX idx_employees_document ON employees(document);
