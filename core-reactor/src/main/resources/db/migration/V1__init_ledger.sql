-- V1__init_ledger.sql
-- Core Vault: accounts + immutable double-entry ledger
-- Schema matches the capstone brief's starter schema exactly.

CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    currency VARCHAR(10) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, currency)
);

-- Every financial movement is TWO rows here (one CREDIT + one DEBIT) that
-- always balance for a given transaction_id. Balances are NEVER stored
-- directly; they are derived as SUM(CASE WHEN direction='CREDIT' THEN amount
-- ELSE -amount END) over ledger_entries for an account.
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    account_id UUID REFERENCES accounts(id),
    amount DECIMAL(18, 8) NOT NULL,
    direction VARCHAR(10) NOT NULL,          -- 'CREDIT' or 'DEBIT'
    entry_type VARCHAR(32) NOT NULL DEFAULT 'GENERAL',  -- DEPOSIT, WITHDRAWAL, TRADE_FILL, FEE
    reference VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries(transaction_id);

-- Idempotency guard: prevents a mashed "Buy" button from creating duplicate
-- financial movements when the same Idempotency-Key is replayed.
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    response_body TEXT,
    status_code INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Users table backing JWT authentication.
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Well-known "external world" account used as the offsetting DEBIT leg when
-- money enters the system via deposit (see LedgerController). Fixed UUID so
-- the application can reference it without a lookup.
INSERT INTO accounts (id, user_id, currency, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000000',
    'SYSTEM',
    CURRENT_TIMESTAMP
);
