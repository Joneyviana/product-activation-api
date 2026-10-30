package br.com.example.activation.request.repository;

import br.com.example.activation.request.domain.ActivationRequest;
import br.com.example.activation.request.domain.ActivationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID> {
    Optional<ActivationRequest> findByExternalId(String externalId);
    Page<ActivationRequest> findByStatusAndCustomerId(ActivationStatus status, String customerId, Pageable pageable);
    Page<ActivationRequest> findByStatus(ActivationStatus status, Pageable pageable);
    Page<ActivationRequest> findByCustomerId(String customerId, Pageable pageable);
}
