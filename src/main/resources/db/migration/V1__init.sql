CREATE TABLE IF NOT EXISTS users (
    id       SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS accounts (
    id      SERIAL PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    balance DOUBLE PRECISION DEFAULT 0.0
);

CREATE TABLE IF NOT EXISTS transactions (
    id          SERIAL PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    amount      DOUBLE PRECISION NOT NULL,
    type        VARCHAR(20) NOT NULL,
    date        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    account_id  INTEGER NOT NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(id)
);
