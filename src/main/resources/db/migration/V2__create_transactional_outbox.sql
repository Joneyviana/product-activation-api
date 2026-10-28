CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL REFERENCES activation_requests(id),
    event_type VARCHAR(100) NOT NULL,
    external_id VARCHAR(80) NOT NULL,
    customer_id VARCHAR(80) NOT NULL,
    product_code VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_outbox_aggregate_event UNIQUE (aggregate_id, event_type),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'DEAD')),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_due ON outbox_events (status, next_attempt_at, created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_id);
