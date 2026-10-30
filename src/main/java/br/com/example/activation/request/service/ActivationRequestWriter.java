package br.com.example.activation.request.service;

import br.com.example.activation.outbox.domain.OutboxEvent;
import br.com.example.activation.outbox.repository.OutboxEventRepository;
import br.com.example.activation.request.domain.ActivationRequest;
import br.com.example.activation.request.dto.CreateActivationRequest;
import br.com.example.activation.request.repository.ActivationRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
public class ActivationRequestWriter {
    private final ActivationRequestRepository requestRepository;
    private final OutboxEventRepository outboxRepository;
    private final Clock clock;

    public ActivationRequestWriter(ActivationRequestRepository requestRepository,
                                   OutboxEventRepository outboxRepository, Clock clock) {
        this.requestRepository = requestRepository;
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    /** Request and outbox event commit atomically; no HTTP call happens here. */
    @Transactional
    public ActivationRequest save(CreateActivationRequest input) {
        var now = Instant.now(clock);
        var request = ActivationRequest.create(input.externalId(), input.customerId(), input.productCode(), now);
        requestRepository.saveAndFlush(request);
        outboxRepository.saveAndFlush(OutboxEvent.forActivation(request, now));
        return request;
    }
}
