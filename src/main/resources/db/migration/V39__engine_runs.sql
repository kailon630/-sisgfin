-- PD-08: histórico de execuções das engines automáticas.
-- Substitui runCatching silencioso em launchBackgroundEngines.

CREATE TABLE engine_runs (
    id           SERIAL PRIMARY KEY,
    engine       VARCHAR(30)  NOT NULL,  -- PAYROLL | RECURRENCE
    reference    VARCHAR(20)  NULL,      -- competência (ex: 2026-08), quando aplicável
    started_at   TIMESTAMP    NOT NULL,
    finished_at  TIMESTAMP    NULL,
    status       VARCHAR(20)  NOT NULL,  -- RUNNING | SUCCESS | FAILED | SKIPPED_LOCKED
    created      INTEGER      NOT NULL DEFAULT 0,
    skipped      INTEGER      NOT NULL DEFAULT 0,
    error        TEXT         NULL
);

CREATE INDEX idx_engine_runs_engine_started ON engine_runs (engine, started_at DESC);
