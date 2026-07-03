-- V4: Base Financial Registrations (Suppliers, Financial Accounts, Projects)

CREATE TABLE IF NOT EXISTS suppliers (
    id         SERIAL PRIMARY KEY,
    document   VARCHAR(20)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    trade_name VARCHAR(100),
    email      VARCHAR(100),
    phone      VARCHAR(20),
    pix_key    VARCHAR(100),
    bank       VARCHAR(50),
    agency     VARCHAR(20),
    account    VARCHAR(20),
    notes      TEXT,
    is_active  BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by INTEGER,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS financial_accounts (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    bank_name       VARCHAR(50),
    agency          VARCHAR(20),
    account_number  VARCHAR(20),
    account_type    VARCHAR(20) NOT NULL,
    initial_balance DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    is_active       BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by      INTEGER,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS projects (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(50) UNIQUE NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    start_date  TIMESTAMP,
    end_date    TIMESTAMP,
    is_active   BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  INTEGER,
    FOREIGN KEY (created_by) REFERENCES users(id)
);
