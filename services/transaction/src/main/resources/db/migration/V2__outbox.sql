CREATE TABLE outbox_event (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID            NOT NULL,
    topic           VARCHAR(64)     NOT NULL,
    msg_key         VARCHAR(128)    NOT NULL,
    payload         TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,
    attempts        INTEGER         NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_error      TEXT
);

CREATE INDEX idx_outbox_pending
    ON outbox_event (next_attempt_at)
    WHERE sent_at IS NULL;
