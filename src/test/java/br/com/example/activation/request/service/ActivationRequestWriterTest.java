package br.com.example.activation.request.service;

import br.com.example.activation.outbox.domain.OutboxEvent;
import br.com.example.activation.outbox.repository.OutboxEventRepository;
import br.com.example.activation.request.domain.ActivationStatus;
import br.com.example.activation.request.dto.CreateActivationRequest;
import br.com.example.activation.request.repository.ActivationRequestRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActivationRequestWriterTest {
    @Test
    void shouldPersistRequestAndOutboxEventInSameWriterOperation() {
        var requestRepository = mock(ActivationRequestRepository.class);
        var outboxRepository = mock(OutboxEventRepository.class);
        var clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);
        when(requestRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var writer = new ActivationRequestWriter(requestRepository, outboxRepository, clock);

        var result = writer.save(new CreateActivationRequest("EXT-1", "CUST-1", "PROD-1"));

        assertEquals(ActivationStatus.PENDING, result.getStatus());
        verify(requestRepository).saveAndFlush(any());
        verify(outboxRepository).saveAndFlush(any(OutboxEvent.class));
    }
}
