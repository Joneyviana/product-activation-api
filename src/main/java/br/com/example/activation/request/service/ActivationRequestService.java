package br.com.example.activation.request.service;

import br.com.example.activation.request.domain.ActivationRequest;
import br.com.example.activation.request.domain.ActivationStatus;
import br.com.example.activation.request.dto.ActivationRequestResponse;
import br.com.example.activation.request.dto.CreateActivationRequest;
import br.com.example.activation.request.repository.ActivationRequestRepository;
import br.com.example.activation.shared.exception.ConflictException;
import br.com.example.activation.shared.exception.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActivationRequestService {
    private final ActivationRequestRepository repository;
    private final ActivationRequestWriter writer;

    public ActivationRequestService(ActivationRequestRepository repository, ActivationRequestWriter writer) {
        this.repository = repository;
        this.writer = writer;
    }

    public CreationResult create(CreateActivationRequest input) {
        var existing = repository.findByExternalId(input.externalId());
        if (existing.isPresent()) {
            var request = existing.get();
            if (!request.hasSamePayload(input.customerId(), input.productCode())) {
                throw new ConflictException("externalId already exists with a different payload");
            }
            return new CreationResult(ActivationRequestResponse.from(request), false);
        }

        try {
            var request = writer.save(input);
            return new CreationResult(ActivationRequestResponse.from(request), true);
        } catch (DataIntegrityViolationException race) {
            var concurrent = repository.findByExternalId(input.externalId());
            if (concurrent.isPresent() &&
                    concurrent.get().hasSamePayload(input.customerId(), input.productCode())) {
                return new CreationResult(ActivationRequestResponse.from(concurrent.get()), false);
            }
            if (concurrent.isPresent()) {
                throw new ConflictException("externalId already exists with a different payload");
            }
            throw race;
        }
    }

    @Transactional(readOnly = true)
    public ActivationRequestResponse get(UUID id) {
        return repository.findById(id).map(ActivationRequestResponse::from)
                .orElseThrow(() -> new NotFoundException("Activation request not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ActivationRequestResponse> list(ActivationStatus status, String customerId, Pageable pageable) {
        Page<ActivationRequest> page;
        if (status != null && customerId != null && !customerId.isBlank()) {
            page = repository.findByStatusAndCustomerId(status, customerId, pageable);
        } else if (status != null) {
            page = repository.findByStatus(status, pageable);
        } else if (customerId != null && !customerId.isBlank()) {
            page = repository.findByCustomerId(customerId, pageable);
        } else {
            page = repository.findAll(pageable);
        }
        return page.map(ActivationRequestResponse::from);
    }

    public record CreationResult(ActivationRequestResponse response, boolean created) {}
}
