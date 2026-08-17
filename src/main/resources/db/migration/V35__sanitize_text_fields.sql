-- T-19: Remove caracteres de controle de campos de texto livre.
--
-- Cobre:
--   C0 controls U+0001-U+001F (inclui \r=0D, \n=0A, \t=09) via E'[\\x01-\\x1F]'
--   DEL U+007F via E'[\\x7F]'
--   NBSP U+00A0 via chr(160)
--   Zero-width U+200B,200C,200D,FEFF via translate()
--
-- NUL (U+0000) nao pode existir em strings UTF-8 no PostgreSQL — ignorado.
-- C1 (U+0080-U+009F) e de ocorrencia improvavel em dados SCI — nao coberto aqui;
-- novos registros sao sanitizados pelo TextSanitizer no lado Kotlin.

-- Passo 1: remove zero-width chars (sem adicionar espaco)
UPDATE employees
SET name  = translate(name,  U&'\200B\200C\200D\FEFF', ''),
    role  = translate(role,  U&'\200B\200C\200D\FEFF', ''),
    email = translate(email, U&'\200B\200C\200D\FEFF', '');

UPDATE suppliers
SET name = translate(name, U&'\200B\200C\200D\FEFF', '');

-- Passo 2: substitui controles C0+DEL por espaco, depois colapsa espacos multiplos e trim
UPDATE employees
SET name  = trim(regexp_replace(regexp_replace(name,  E'[\\x01-\\x1F\\x7F]+', ' ', 'g'), ' {2,}', ' ', 'g')),
    role  = trim(regexp_replace(regexp_replace(role,  E'[\\x01-\\x1F\\x7F]+', ' ', 'g'), ' {2,}', ' ', 'g')),
    email = trim(regexp_replace(regexp_replace(email, E'[\\x01-\\x1F\\x7F]+', ' ', 'g'), ' {2,}', ' ', 'g'))
WHERE name  ~ E'[\\x01-\\x1F\\x7F]'
   OR role  ~ E'[\\x01-\\x1F\\x7F]'
   OR email ~ E'[\\x01-\\x1F\\x7F]';

UPDATE suppliers
SET name = trim(regexp_replace(regexp_replace(name, E'[\\x01-\\x1F\\x7F]+', ' ', 'g'), ' {2,}', ' ', 'g'))
WHERE name ~ E'[\\x01-\\x1F\\x7F]';

-- Passo 3: substitui NBSP (U+00A0, chr(160)) por espaco normal
UPDATE employees
SET name  = replace(name,  chr(160), ' '),
    role  = replace(role,  chr(160), ' '),
    email = replace(email, chr(160), ' ')
WHERE name  LIKE '%' || chr(160) || '%'
   OR role  LIKE '%' || chr(160) || '%'
   OR email LIKE '%' || chr(160) || '%';

UPDATE suppliers
SET name = replace(name, chr(160), ' ')
WHERE name LIKE '%' || chr(160) || '%';
