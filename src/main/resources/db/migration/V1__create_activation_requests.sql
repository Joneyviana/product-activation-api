CREATE TABLE activation_requests (
    id UUID PRIMARY KEY,
    external_id VARCHAR(80) NOT NULL,
    customer_id VARCHAR(80) NOT NULL,
    product_code VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_activation_external_id UNIQUE (external_id),
    CONSTRAINT chk_activation_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_activation_customer_created ON activation_requests (customer_id, created_at DESC);
CREATE INDEX idx_activation_status_created ON activation_requests (status, created_at DESC);
