CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE transactions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key   VARCHAR(64)     NOT NULL,
    source_account    UUID            NOT NULL,
    dest_account      UUID            NOT NULL,
    amount            NUMERIC(19,4)   NOT NULL CHECK (amount > 0),
    currency          CHAR(3)         NOT NULL,
    status            VARCHAR(16)     NOT NULL,
    failure_reason    TEXT,
    initiator_user    UUID            NOT NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_txn_idem_src ON transactions(idempotency_key, source_account);
CREATE INDEX idx_txn_source_time ON transactions(source_account, created_at DESC);
CREATE INDEX idx_txn_dest_time   ON transactions(dest_account,   created_at DESC);
CREATE INDEX idx_txn_user_time   ON transactions(initiator_user, created_at DESC);
