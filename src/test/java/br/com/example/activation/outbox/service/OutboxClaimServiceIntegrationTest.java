package br.com.example.activation.outbox.service;

import br.com.example.activation.outbox.domain.OutboxEvent;
import br.com.example.activation.outbox.domain.OutboxStatus;
import br.com.example.activation.outbox.repository.OutboxEventRepository;
import br.com.example.activation.request.domain.ActivationRequest;
import br.com.example.activation.request.domain.ActivationStatus;
import br.com.example.activation.request.repository.ActivationRequestRepository;
import br.com.example.activation.shared.TimeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "outbox.lease-duration=30s"
})
@Import({OutboxClaimService.class, TimeConfiguration.class})
class OutboxClaimServiceIntegrationTest {

    @Autowired OutboxClaimService claimService;
    @Autowired ActivationRequestRepository requestRepository;
    @Autowired OutboxEventRepository outboxRepository;

    @Test
    void claimingPendingEventMovesRequestAndEventToProcessing() {
        Instant now = Instant.now();
        ActivationRequest request = ActivationRequest.create("EXT-CLAIM-1", "CUST-1", "PREMIUM", now);
        requestRepository.saveAndFlush(request);
        OutboxEvent event = OutboxEvent.forActivation(request, now);
        outboxRepository.saveAndFlush(event);

        var claimed = claimService.claim(event.getId());

        assertTrue(claimed.isPresent());
        assertEquals(ActivationStatus.PROCESSING,
                requestRepository.findById(request.getId()).orElseThrow().getStatus());
        OutboxEvent storedEvent = outboxRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxStatus.PROCESSING, storedEvent.getStatus());
        assertEquals(1, storedEvent.getAttempts());
        assertNotNull(storedEvent.getLockedUntil());
    }

    @Test
    void expiredProcessingLeaseMakesEventEligibleForRecovery() {
        Instant now = Instant.now();
        ActivationRequest request = ActivationRequest.create("EXT-LEASE-1", "CUST-1", "PREMIUM", now);
        requestRepository.saveAndFlush(request);
        OutboxEvent event = OutboxEvent.forActivation(request, now);
        event.claim(now, now.minusSeconds(1));
        outboxRepository.saveAndFlush(event);

        var eligible = outboxRepository.findEligible(
                now, OutboxStatus.PENDING, OutboxStatus.PROCESSING,
                org.springframework.data.domain.PageRequest.of(0, 20));

        assertTrue(eligible.stream().anyMatch(candidate -> candidate.getId().equals(event.getId())));
    }
}
