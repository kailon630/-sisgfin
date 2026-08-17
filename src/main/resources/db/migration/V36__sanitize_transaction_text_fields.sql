-- T-21: sanitizacao preventiva de campos de texto em financial_transactions
-- Aplica as mesmas regras do TextSanitizer.kt:
--   description / document_type / document_number : clean()      -> controles -> espaco, NBSP -> espaco, zero-width -> remove
--   notes                                          : cleanPreserveNewlines() -> preserva \n, remove \r solto

-- ── description ──────────────────────────────────────────────────────────────
UPDATE financial_transactions
SET description = trim(
    regexp_replace(
        replace(
            translate(description,
                      U&'\200B\200C\200D\FEFF', '    '),
            chr(160), ' '
        ),
        E'[\\x01-\\x1F\\x7F-\\x9F]+', ' ', 'g'
    )
)
WHERE description ~ E'[\\x01-\\x1F\\x7F-\\x9F]'
   OR description LIKE '%' || chr(160) || '%'
   OR description ~ U&'[\200B\200C\200D\FEFF]';

-- ── document_type ─────────────────────────────────────────────────────────────
UPDATE financial_transactions
SET document_type = trim(
    regexp_replace(
        replace(
            translate(document_type,
                      U&'\200B\200C\200D\FEFF', '    '),
            chr(160), ' '
        ),
        E'[\\x01-\\x1F\\x7F-\\x9F]+', ' ', 'g'
    )
)
WHERE document_type IS NOT NULL
  AND (
      document_type ~ E'[\\x01-\\x1F\\x7F-\\x9F]'
   OR document_type LIKE '%' || chr(160) || '%'
   OR document_type ~ U&'[\200B\200C\200D\FEFF]'
  );

-- ── document_number ───────────────────────────────────────────────────────────
UPDATE financial_transactions
SET document_number = trim(
    regexp_replace(
        replace(
            translate(document_number,
                      U&'\200B\200C\200D\FEFF', '    '),
            chr(160), ' '
        ),
        E'[\\x01-\\x1F\\x7F-\\x9F]+', ' ', 'g'
    )
)
WHERE document_number IS NOT NULL
  AND (
      document_number ~ E'[\\x01-\\x1F\\x7F-\\x9F]'
   OR document_number LIKE '%' || chr(160) || '%'
   OR document_number ~ U&'[\200B\200C\200D\FEFF]'
  );

-- ── notes (preserva \n, remove \r solto) ─────────────────────────────────────
-- Passo 1: normaliza CRLF -> LF, remove \r solto, remove outros controles exceto \n e \t
UPDATE financial_transactions
SET notes = trim(
    regexp_replace(
        replace(
            replace(
                replace(
                    translate(notes, U&'\200B\200C\200D\FEFF', '    '),
                    chr(160), ' '
                ),
                E'\\r\\n', E'\\n'
            ),
            E'\\r', ''
        ),
        E'[\\x01-\\x08\\x0B-\\x1F\\x7F-\\x9F]+', ' ', 'g'
    )
)
WHERE notes IS NOT NULL
  AND (
      notes ~ E'[\\x01-\\x08\\x0B-\\x1F\\x7F-\\x9F]'
   OR notes LIKE '%' || chr(160) || '%'
   OR notes ~ U&'[\200B\200C\200D\FEFF]'
   OR notes LIKE E'%\\r%'
  );
