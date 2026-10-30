package br.com.example.activation.outbox.service;

import br.com.example.activation.outbox.domain.OutboxEvent;
import br.com.example.activation.outbox.domain.OutboxStatus;
import br.com.example.activation.outbox.repository.OutboxEventRepository;
import br.com.example.activation.request.domain.ActivationRequest;
import br.com.example.activation.request.domain.ActivationStatus;
import br.com.example.activation.request.repository.ActivationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxCompletionServiceTest {
    private OutboxEventRepository outboxRepository;
    private ActivationRequestRepository requestRepository;
    private OutboxCompletionService service;
    private OutboxEvent event;
    private ActivationRequest request;
    private Instant now;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxEventRepository.class);
        requestRepository = mock(ActivationRequestRepository.class);
        now = Instant.parse("2026-10-28T12:00:00Z");
        request = ActivationRequest.create("EXT-COMP-1", "CUST-1", "PREMIUM", now);
        request.startProcessing(now);
        event = OutboxEvent.forActivation(request, now);
        event.claim(now, now.plusSeconds(60));
        when(outboxRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        service = new OutboxCompletionService(outboxRepository, requestRepository,
                Clock.fixed(now, ZoneOffset.UTC), 3, java.time.Duration.ofSeconds(11));
    }

    @Test
    void successCompletesRequestAndMarksEventProcessed() {
        service.succeeded(event.getId(), request.getId());

        assertEquals(ActivationStatus.COMPLETED, request.getStatus());
        assertEquals(OutboxStatus.PROCESSED, event.getStatus());
        assertNotNull(event.getProcessedAt());
    }

    @Test
    void transientFailureReturnsRequestToPendingAndSchedulesRetry() {
        service.temporarilyFailed(event.getId(), request.getId(), 1, "timeout");

        assertEquals(ActivationStatus.PENDING, request.getStatus());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertTrue(event.getNextAttemptAt().isAfter(now));
        assertNull(event.getLockedUntil());
    }

    @Test
    void exhaustedDeliveryAttemptsMarkRequestFailedAndEventDead() {
        service.temporarilyFailed(event.getId(), request.getId(), 3, "timeout");

        assertEquals(ActivationStatus.FAILED, request.getStatus());
        assertEquals(OutboxStatus.DEAD, event.getStatus());
        assertNotNull(event.getProcessedAt());
    }

    @Test
    void permanentFailureMarksRequestFailedAndEventProcessed() {
        service.permanentlyFailed(event.getId(), request.getId(), "invalid product");

        assertEquals(ActivationStatus.FAILED, request.getStatus());
        assertEquals(OutboxStatus.PROCESSED, event.getStatus());
    }
}
