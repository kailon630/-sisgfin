-- Fase 6-D.2: histórico de importações OFX — rastreabilidade por arquivo e conta
CREATE TABLE IF NOT EXISTS ofx_imports (
    id                SERIAL PRIMARY KEY,
    account_id        INTEGER      NOT NULL REFERENCES financial_accounts(id),
    filename          VARCHAR(255) NOT NULL,
    bank_id           VARCHAR(50)  NOT NULL,
    acct_id           VARCHAR(50)  NOT NULL,
    dt_start          DATE         NOT NULL,
    dt_end            DATE         NOT NULL,
    imported_at       TIMESTAMP    NOT NULL,
    imported_by       INTEGER      NULL REFERENCES users(id),
    total_records     INTEGER      NOT NULL DEFAULT 0,
    new_records       INTEGER      NOT NULL DEFAULT 0,
    duplicate_records INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS ix_ofx_imports_account_id ON ofx_imports (account_id);
