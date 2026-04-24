CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    account_number  VARCHAR(24)     NOT NULL UNIQUE,
    balance         NUMERIC(19,4)   NOT NULL DEFAULT 0 CHECK (balance >= 0),
    currency        CHAR(3)         NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_user ON accounts(user_id);

CREATE TABLE account_ledger (
    id              BIGSERIAL PRIMARY KEY,
    account_id      UUID            NOT NULL REFERENCES accounts(id),
    tx_id           UUID            NOT NULL,
    direction       CHAR(1)         NOT NULL CHECK (direction IN ('D','C')),
    amount          NUMERIC(19,4)   NOT NULL CHECK (amount > 0),
    balance_after   NUMERIC(19,4)   NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_account_time ON account_ledger(account_id, created_at DESC);
CREATE UNIQUE INDEX uq_ledger_tx_account_dir ON account_ledger(tx_id, account_id, direction);
