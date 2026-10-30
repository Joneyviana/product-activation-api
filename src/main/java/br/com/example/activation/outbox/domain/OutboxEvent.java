package br.com.example.activation.outbox.domain;

import br.com.example.activation.request.domain.ActivationRequest;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_due", columnList = "status,next_attempt_at,created_at"),
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_id")
})
public class OutboxEvent {
    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "external_id", nullable = false, length = 80)
    private String externalId;

    @Column(name = "customer_id", nullable = false, length = 80)
    private String customerId;

    @Column(name = "product_code", nullable = false, length = 80)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Version
    @Column(nullable = false)
    private long version;

    protected OutboxEvent() {}

    private OutboxEvent(ActivationRequest request, Instant now) {
        this.id = UUID.randomUUID();
        this.aggregateId = request.getId();
        this.eventType = "PRODUCT_ACTIVATION_REQUESTED";
        this.externalId = request.getExternalId();
        this.customerId = request.getCustomerId();
        this.productCode = request.getProductCode();
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public static OutboxEvent forActivation(ActivationRequest request, Instant now) {
        return new OutboxEvent(request, now);
    }

    public boolean isClaimable(Instant now) {
        return (status == OutboxStatus.PENDING && !nextAttemptAt.isAfter(now))
                || (status == OutboxStatus.PROCESSING && lockedUntil != null && !lockedUntil.isAfter(now));
    }

    public void claim(Instant now, Instant leaseUntil) {
        if (!isClaimable(now)) throw new IllegalStateException("Outbox event is not claimable");
        status = OutboxStatus.PROCESSING;
        attempts++;
        lockedUntil = leaseUntil;
        lastError = null;
    }

    public void scheduleRetry(Instant nextAttempt, String error) {
        status = OutboxStatus.PENDING;
        nextAttemptAt = nextAttempt;
        lockedUntil = null;
        lastError = truncate(error);
    }

    /** Defers work while a dependency circuit is open without consuming a delivery attempt. */
    public void deferRetry(Instant nextAttempt, String error) {
        if (attempts > 0) attempts--;
        status = OutboxStatus.PENDING;
        nextAttemptAt = nextAttempt;
        lockedUntil = null;
        lastError = truncate(error);
    }

    public void markProcessed(Instant now) {
        status = OutboxStatus.PROCESSED;
        processedAt = now;
        lockedUntil = null;
        lastError = null;
    }

    public void markDead(Instant now, String error) {
        status = OutboxStatus.DEAD;
        processedAt = now;
        lockedUntil = null;
        lastError = truncate(error);
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getExternalId() { return externalId; }
    public String getCustomerId() { return customerId; }
    public String getProductCode() { return productCode; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public String getLastError() { return lastError; }
}