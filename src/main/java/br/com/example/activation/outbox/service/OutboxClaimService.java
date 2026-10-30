package br.com.example.activation.outbox.service;

import br.com.example.activation.outbox.domain.OutboxEvent;
import br.com.example.activation.outbox.domain.OutboxStatus;
import br.com.example.activation.outbox.repository.OutboxEventRepository;
import br.com.example.activation.request.domain.ActivationRequest;
import br.com.example.activation.request.repository.ActivationRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OutboxClaimService {
    private final OutboxEventRepository outboxRepository;
    private final ActivationRequestRepository requestRepository;
    private final Clock clock;
    private final Duration leaseDuration;

    public OutboxClaimService(OutboxEventRepository outboxRepository,
                              ActivationRequestRepository requestRepository,
                              Clock clock,
                              @Value("${outbox.lease-duration:60s}") Duration leaseDuration) {
        this.outboxRepository = outboxRepository;
        this.requestRepository = requestRepository;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
    }

    @Transactional
    public Optional<ClaimedActivation> claim(UUID eventId) {
        Instant now = Instant.now(clock);
        OutboxEvent event = outboxRepository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || !event.isClaimable(now)) return Optional.empty();

        ActivationRequest request = requestRepository.findById(event.getAggregateId()).orElse(null);
        if (request == null || request.getStatus() == br.com.example.activation.request.domain.ActivationStatus.COMPLETED
                || request.getStatus() == br.com.example.activation.request.domain.ActivationStatus.FAILED) {
            event.markProcessed(now);
            return Optional.empty();
        }

        event.claim(now, now.plus(leaseDuration));
        if (request.getStatus() == br.com.example.activation.request.domain.ActivationStatus.PENDING) {
            request.startProcessing(now);
        }
        requestRepository.save(request);
        outboxRepository.save(event);
        return Optional.of(new ClaimedActivation(event.getId(), request.getId(), event.getExternalId(),
                event.getCustomerId(), event.getProductCode(), event.getAttempts()));
    }
}
