package br.com.example.activation.outbox.service;

import br.com.example.activation.outbox.domain.OutboxEvent;
import br.com.example.activation.outbox.domain.OutboxStatus;
import br.com.example.activation.outbox.repository.OutboxEventRepository;
import br.com.example.activation.provider.ProductProviderClient;
import br.com.example.activation.provider.ResilientProductProvider;
import br.com.example.activation.provider.TemporaryProviderFailureException;
import br.com.example.activation.request.domain.ActivationRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {
    @Mock OutboxEventRepository outboxRepository;
    @Mock OutboxClaimService claimService;
    @Mock OutboxCompletionService completionService;
    @Mock ResilientProductProvider provider;

    private OutboxProcessor processor;
    private OutboxEvent event;
    private ClaimedActivation activation;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-28T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        ActivationRequest request = ActivationRequest.create("EXT-WORKER-1", "CUST-1", "PREMIUM", now);
        event = OutboxEvent.forActivation(request, now);
        activation = new ClaimedActivation(event.getId(), request.getId(), "EXT-WORKER-1", "CUST-1", "PREMIUM", 1);
        processor = new OutboxProcessor(outboxRepository, claimService, completionService, provider, clock);
        when(outboxRepository.findEligible(eq(now), eq(OutboxStatus.PENDING), eq(OutboxStatus.PROCESSING), any(PageRequest.class)))
                .thenReturn(List.of(event));
        when(claimService.claim(event.getId())).thenReturn(Optional.of(activation));
    }

    @Test
    void successfulActivationCompletesRequestAndOutbox() {
        when(provider.activate("EXT-WORKER-1", "CUST-1", "PREMIUM"))
                .thenReturn(ProductProviderClient.ActivationResult.SUCCESS);

        processor.poll();

        verify(completionService).succeeded(event.getId(), activation.requestId());
        verify(completionService, never()).temporarilyFailed(any(), any(), anyInt(), anyString());
    }

    @Test
    void remoteCallMustRunOutsideDatabaseTransaction() {
        when(provider.activate(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "The remote provider call must not run inside a database transaction");
            return ProductProviderClient.ActivationResult.SUCCESS;
        });

        processor.poll();

        verify(completionService).succeeded(event.getId(), activation.requestId());
    }

    @Test
    void temporaryProviderFailureSchedulesAnotherDeliveryCycle() {
        when(provider.activate("EXT-WORKER-1", "CUST-1", "PREMIUM"))
                .thenThrow(new TemporaryProviderFailureException("provider timeout"));

        processor.poll();

        verify(completionService).temporarilyFailed(
                event.getId(), activation.requestId(), activation.attempts(), "provider timeout");
    }

    @Test
    void permanentProviderFailureDoesNotScheduleRetry() {
        when(provider.activate("EXT-WORKER-1", "CUST-1", "PREMIUM"))
                .thenReturn(ProductProviderClient.ActivationResult.PERMANENT_FAILURE);

        processor.poll();

        verify(completionService).permanentlyFailed(
                event.getId(), activation.requestId(), "Provider rejected activation");
        verify(completionService, never()).temporarilyFailed(any(), any(), anyInt(), anyString());
    }

    @Test
    void openCircuitDefersEventWithoutCallingProvider() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test");
        circuitBreaker.transitionToOpenState();
        when(provider.activate(anyString(), anyString(), anyString()))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(circuitBreaker));

        processor.poll();

        verify(completionService).deferredByCircuitBreaker(
                eq(event.getId()), eq(activation.requestId()), anyString());
        verify(completionService, never()).temporarilyFailed(any(), any(), anyInt(), anyString());
    }
}
