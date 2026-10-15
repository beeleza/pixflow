CREATE TABLE pix_transactions (
    id                   VARCHAR(36)    NOT NULL,
    idempotency_key      VARCHAR(255)   NOT NULL,
    source_account       VARCHAR(36)    NOT NULL,
    destination_key      VARCHAR(255)   NOT NULL,
    destination_key_type VARCHAR(16)    NOT NULL,
    amount               NUMERIC(19, 2) NOT NULL,
    status               VARCHAR(16)    NOT NULL,
    failure_reason       VARCHAR(500),
    created_at           TIMESTAMPTZ    NOT NULL,
    settled_at           TIMESTAMPTZ,
    version              BIGINT         NOT NULL,

    CONSTRAINT pk_pix_transactions PRIMARY KEY (id),
    CONSTRAINT uk_pix_transactions_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_pix_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_pix_transactions_destination_key_type
        CHECK (destination_key_type IN ('CPF', 'CNPJ', 'EMAIL', 'PHONE', 'RANDOM')),
    CONSTRAINT ck_pix_transactions_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_pix_transactions_source_account ON pix_transactions (source_account, created_at DESC);
