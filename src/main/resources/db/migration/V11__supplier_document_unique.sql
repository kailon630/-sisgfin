-- V11: Unique constraint on suppliers.document
CREATE UNIQUE INDEX IF NOT EXISTS idx_suppliers_document ON suppliers(document);
