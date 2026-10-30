package br.com.example.activation.outbox.service;

import br.com.example.activation.outbox.domain.OutboxEvent;
import br.com.example.activation.outbox.repository.OutboxEventRepository;
import br.com.example.activation.request.domain.ActivationRequest;
import br.com.example.activation.request.domain.ActivationStatus;
import br.com.example.activation.request.repository.ActivationRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OutboxCompletionService {
    private final OutboxEventRepository outboxRepository;
    private final ActivationRequestRepository requestRepository;
    private final Clock clock;
    private final int maxDeliveryAttempts;
    private final Duration circuitOpenRetryDelay;

    public OutboxCompletionService(OutboxEventRepository outboxRepository,
                                   ActivationRequestRepository requestRepository,
                                   Clock clock,
                                   @Value("${outbox.max-delivery-attempts:10}") int maxDeliveryAttempts,
                                   @Value("${outbox.circuit-open-retry-delay:11s}") Duration circuitOpenRetryDelay) {
        this.outboxRepository = outboxRepository;
        this.requestRepository = requestRepository;
        this.clock = clock;
        this.maxDeliveryAttempts = maxDeliveryAttempts;
        this.circuitOpenRetryDelay = circuitOpenRetryDelay;
    }

    @Transactional
    public void succeeded(UUID eventId, UUID requestId) {
        Instant now = Instant.now(clock);
        OutboxEvent event = outboxRepository.findByIdForUpdate(eventId).orElseThrow();
        ActivationRequest request = requestRepository.findById(requestId).orElseThrow();
        if (request.getStatus() == ActivationStatus.PROCESSING) request.complete(now);
        event.markProcessed(now);
        requestRepository.save(request);
        outboxRepository.save(event);
    }

    @Transactional
    public void permanentlyFailed(UUID eventId, UUID requestId, String reason) {
        Instant now = Instant.now(clock);
        OutboxEvent event = outboxRepository.findByIdForUpdate(eventId).orElseThrow();
        ActivationRequest request = requestRepository.findById(requestId).orElseThrow();
        if (request.getStatus() == ActivationStatus.PROCESSING) request.fail(now);
        event.markProcessed(now);
        requestRepository.save(request);
        outboxRepository.save(event);
    }

    @Transactional
    public void deferredByCircuitBreaker(UUID eventId, UUID requestId, String reason) {
        Instant now = Instant.now(clock);
        OutboxEvent event = outboxRepository.findByIdForUpdate(eventId).orElseThrow();
        ActivationRequest request = requestRepository.findById(requestId).orElseThrow();
        if (request.getStatus() == ActivationStatus.PROCESSING) request.retry(now);
        event.deferRetry(now.plus(circuitOpenRetryDelay), reason);
        requestRepository.save(request);
        outboxRepository.save(event);
    }

    @Transactional
    public void temporarilyFailed(UUID eventId, UUID requestId, int attempts, String reason) {
        Instant now = Instant.now(clock);
        OutboxEvent event = outboxRepository.findByIdForUpdate(eventId).orElseThrow();
        ActivationRequest request = requestRepository.findById(requestId).orElseThrow();

        if (attempts >= maxDeliveryAttempts) {
            if (request.getStatus() == ActivationStatus.PROCESSING) request.fail(now);
            event.markDead(now, "Delivery attempts exhausted: " + reason);
        } else {
            if (request.getStatus() == ActivationStatus.PROCESSING) request.retry(now);
            event.scheduleRetry(now.plus(backoffWithJitter(attempts)), reason);
        }
        requestRepository.save(request);
        outboxRepository.save(event);
    }

    private Duration backoffWithJitter(int attempts) {
        long exponent = Math.min(Math.max(attempts - 1, 0), 8);
        long baseSeconds = Math.min(300, 1L << exponent);
        // +/- 20% jitter avoids many events retrying at the same instant.
        long minMillis = (long) (baseSeconds * 800L);
        long maxMillis = (long) (baseSeconds * 1200L);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1));
    }
}
