package br.com.example.activation.outbox.service;

import br.com.example.activation.provider.ProductProviderClient;
import br.com.example.activation.outbox.domain.OutboxStatus;
import br.com.example.activation.provider.ResilientProductProvider;
import br.com.example.activation.provider.TemporaryProviderFailureException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class OutboxProcessor {
    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);
    private final br.com.example.activation.outbox.repository.OutboxEventRepository outboxRepository;
    private final OutboxClaimService claimService;
    private final OutboxCompletionService completionService;
    private final ResilientProductProvider provider;
    private final Clock clock;

    public OutboxProcessor(br.com.example.activation.outbox.repository.OutboxEventRepository outboxRepository,
                           OutboxClaimService claimService,
                           OutboxCompletionService completionService,
                           ResilientProductProvider provider,
                           Clock clock) {
        this.outboxRepository = outboxRepository;
        this.claimService = claimService;
        this.completionService = completionService;
        this.provider = provider;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval:1000}")
    public void poll() {
        var eligible = outboxRepository.findEligible(Instant.now(clock), OutboxStatus.PENDING, OutboxStatus.PROCESSING, PageRequest.of(0, 20));
        for (var event : eligible) {
            try {
                claimService.claim(event.getId()).ifPresent(this::process);
            } catch (Exception ex) {
                // A failed worker iteration must not stop processing other events.
                log.error("Failed to process outbox event {}", event.getId(), ex);
            }
        }
    }

    private void process(ClaimedActivation activation) {
        try {
            var result = provider.activate(activation.externalId(), activation.customerId(), activation.productCode());
            if (result == ProductProviderClient.ActivationResult.SUCCESS) {
                completionService.succeeded(activation.eventId(), activation.requestId());
            } else if (result == ProductProviderClient.ActivationResult.PERMANENT_FAILURE) {
                completionService.permanentlyFailed(activation.eventId(), activation.requestId(), "Provider rejected activation");
            } else {
                completionService.temporarilyFailed(activation.eventId(), activation.requestId(), activation.attempts(),
                        "Provider reported a temporary failure");
            }
        } catch (CallNotPermittedException circuitOpen) {
            completionService.deferredByCircuitBreaker(activation.eventId(), activation.requestId(), circuitOpen.getMessage());
        } catch (TemporaryProviderFailureException ex) {
            completionService.temporarilyFailed(activation.eventId(), activation.requestId(), activation.attempts(), ex.getMessage());
        } catch (Exception ex) {
            // Treat unexpected integration failures as transient; lease expiry recovers a crash before this update.
            log.warn("Unexpected provider processing failure for request {}", activation.requestId(), ex);
            completionService.temporarilyFailed(activation.eventId(), activation.requestId(), activation.attempts(), ex.getClass().getSimpleName());
        }
    }
}
