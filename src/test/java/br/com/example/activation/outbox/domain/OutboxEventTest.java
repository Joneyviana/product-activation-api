package br.com.example.activation.outbox.domain;

import br.com.example.activation.request.domain.ActivationRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventTest {
    @Test
    void shouldBeClaimableWhenPendingAndDue() {
        Instant now = Instant.parse("2026-10-28T12:00:00Z");
        var request = ActivationRequest.create("EXT-1", "CUST-1", "PROD-1", now);
        var event = OutboxEvent.forActivation(request, now);

        assertTrue(event.isClaimable(now));
        event.claim(now, now.plusSeconds(30));
        assertEquals(OutboxStatus.PROCESSING, event.getStatus());
        assertEquals(1, event.getAttempts());
        assertFalse(event.isClaimable(now.plusSeconds(10)));
        assertTrue(event.isClaimable(now.plusSeconds(30)));
    }

    @Test
    void retryShouldPersistNextAttemptTime() {
        Instant now = Instant.parse("2026-10-28T12:00:00Z");
        var request = ActivationRequest.create("EXT-2", "CUST-1", "PROD-1", now);
        var event = OutboxEvent.forActivation(request, now);
        event.claim(now, now.plusSeconds(30));
        event.scheduleRetry(now.plusSeconds(5), "timeout");

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(now.plusSeconds(5), event.getNextAttemptAt());
        assertTrue(event.isClaimable(now.plusSeconds(5)));
    }
    @Test
    void circuitOpenDeferralShouldNotConsumeDeliveryAttempt() {
        Instant now = Instant.parse("2026-10-28T12:00:00Z");
        var request = ActivationRequest.create("EXT-3", "CUST-1", "PROD-1", now);
        var event = OutboxEvent.forActivation(request, now);
        event.claim(now, now.plusSeconds(30));
        event.deferRetry(now.plusSeconds(11), "circuit open");

        assertEquals(0, event.getAttempts());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertFalse(event.isClaimable(now.plusSeconds(10)));
        assertTrue(event.isClaimable(now.plusSeconds(11)));
    }

}

