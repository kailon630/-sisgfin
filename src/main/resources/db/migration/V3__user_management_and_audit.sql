-- V3: User Management and Audit Logs

CREATE TABLE IF NOT EXISTS audit_logs (
    id           SERIAL PRIMARY KEY,
    entity_type  VARCHAR(50)  NOT NULL,
    entity_id    INTEGER      NOT NULL,
    action       VARCHAR(100) NOT NULL,
    old_value    TEXT,
    new_value    TEXT,
    performed_by INTEGER,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (performed_by) REFERENCES users(id)
);

-- Reestruturação da tabela users via ALTER TABLE (PostgreSQL suporta diretamente)
ALTER TABLE users ADD COLUMN IF NOT EXISTS name         VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email        VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS role         VARCHAR(20) NOT NULL DEFAULT 'ADMIN';
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active    BOOLEAN     NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_by   INTEGER REFERENCES users(id);

-- Migra dados das colunas antigas para as novas
UPDATE users SET
    name          = username,
    email         = username || '@sisgfin.com',
    password_hash = password;

-- Remove coluna legada
ALTER TABLE users DROP COLUMN IF EXISTS password;

-- Aplica NOT NULL agora que os dados estão populados
ALTER TABLE users ALTER COLUMN name          SET NOT NULL;
ALTER TABLE users ALTER COLUMN email         SET NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;
