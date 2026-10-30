package br.com.example.activation.request.controller;

import br.com.example.activation.request.domain.ActivationStatus;
import br.com.example.activation.request.dto.ActivationRequestResponse;
import br.com.example.activation.request.dto.CreateActivationRequest;
import br.com.example.activation.request.service.ActivationRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
public class ActivationRequestController {
    private final ActivationRequestService service;

    public ActivationRequestController(ActivationRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ActivationRequestResponse> create(@Valid @RequestBody CreateActivationRequest input) {
        var result = service.create(input);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(result.response().id()).toUri();
        return result.created()
                ? ResponseEntity.created(location).body(result.response())
                : ResponseEntity.ok().location(location).body(result.response());
    }

    @GetMapping("/{id}")
    public ActivationRequestResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    public Page<ActivationRequestResponse> list(
            @RequestParam(required = false) ActivationStatus status,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        }
        return service.list(status, customerId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

}
