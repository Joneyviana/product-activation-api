package br.com.example.activation.request.service;

import br.com.example.activation.request.domain.ActivationStatus;
import br.com.example.activation.request.dto.CreateActivationRequest;
import br.com.example.activation.request.repository.ActivationRequestRepository;
import br.com.example.activation.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivationRequestServiceTest {
    @Mock ActivationRequestRepository repository;
    @Mock br.com.example.activation.request.service.ActivationRequestWriter writer;

    @Test
    void duplicateExternalIdWithDifferentPayloadShouldConflict() {
        var existing = br.com.example.activation.request.domain.ActivationRequest.create(
                "EXT-1", "CUST-1", "INTERNET-500", Instant.parse("2026-10-28T12:00:00Z"));
        when(repository.findByExternalId("EXT-1")).thenReturn(Optional.of(existing));
        var service = new ActivationRequestService(repository, writer);

        assertThrows(ConflictException.class, () -> service.create(
                new CreateActivationRequest("EXT-1", "CUST-2", "INTERNET-500")));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createShouldStartPending() {
        when(repository.findByExternalId("EXT-2")).thenReturn(Optional.empty());
        when(writer.save(any())).thenAnswer(invocation -> br.com.example.activation.request.domain.ActivationRequest.create(
                "EXT-2", "CUST-1", "INTERNET-500", Instant.parse("2026-10-28T12:00:00Z")));
        var service = new ActivationRequestService(repository, writer);

        var result = service.create(new CreateActivationRequest("EXT-2", "CUST-1", "INTERNET-500"));

        assertTrue(result.created());
        assertEquals(ActivationStatus.PENDING, result.response().status());
        assertEquals(Instant.parse("2026-10-28T12:00:00Z"), result.response().createdAt());
    }
}
