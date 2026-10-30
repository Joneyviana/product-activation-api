package br.com.example.activation.request.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activation_requests",
       uniqueConstraints = @UniqueConstraint(name = "uk_activation_external_id", columnNames = "external_id"),
       indexes = {
           @Index(name = "idx_activation_customer_created", columnList = "customer_id, created_at"),
           @Index(name = "idx_activation_status_created", columnList = "status, created_at")
       })
public class ActivationRequest {
    @Id
    private UUID id;

    @Column(name = "external_id", nullable = false, length = 80)
    private String externalId;

    @Column(name = "customer_id", nullable = false, length = 80)
    private String customerId;

    @Column(name = "product_code", nullable = false, length = 80)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ActivationRequest() {}

    private ActivationRequest(String externalId, String customerId, String productCode, Instant now) {
        this.id = UUID.randomUUID();
        this.externalId = externalId;
        this.customerId = customerId;
        this.productCode = productCode;
        this.status = ActivationStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ActivationRequest create(String externalId, String customerId, String productCode, Instant now) {
        return new ActivationRequest(externalId, customerId, productCode, now);
    }

    public void startProcessing(Instant now) {
        if (status != ActivationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can start processing");
        }
        status = ActivationStatus.PROCESSING;
        updatedAt = now;
    }

    public void complete(Instant now) {
        if (status != ActivationStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING requests can be completed");
        }
        status = ActivationStatus.COMPLETED;
        updatedAt = now;
    }

    public void fail(Instant now) {
        if (status != ActivationStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING requests can fail");
        }
        status = ActivationStatus.FAILED;
        updatedAt = now;
    }

    public void retry(Instant now) {
        if (status != ActivationStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING requests can be retried");
        }
        status = ActivationStatus.PENDING;
        updatedAt = now;
    }

    public boolean hasSamePayload(String customerId, String productCode) {
        return this.customerId.equals(customerId) && this.productCode.equals(productCode);
    }

    public UUID getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getCustomerId() { return customerId; }
    public String getProductCode() { return productCode; }
    public ActivationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
