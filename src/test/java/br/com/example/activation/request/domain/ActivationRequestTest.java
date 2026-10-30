package br.com.example.activation.request.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class ActivationRequestTest {
    @Test
    void shouldEnforceValidStatusTransitions() {
        Instant now = Instant.parse("2026-10-28T12:00:00Z");
        ActivationRequest request = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", now);

        assertEquals(ActivationStatus.PENDING, request.getStatus());
        request.startProcessing(now.plusSeconds(1));
        request.complete(now.plusSeconds(2));
        assertEquals(ActivationStatus.COMPLETED, request.getStatus());
        assertThrows(IllegalStateException.class, () -> request.startProcessing(now.plusSeconds(3)));
    }

    @Test
    void temporaryFailureCanReturnToPending() {
        Instant now = Instant.parse("2026-10-28T12:00:00Z");
        ActivationRequest request = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", now);
        request.startProcessing(now.plusSeconds(1));
        request.retry(now.plusSeconds(2));
        assertEquals(ActivationStatus.PENDING, request.getStatus());
    }
}
