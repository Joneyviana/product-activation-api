package br.com.example.activation.outbox.service;

import java.util.UUID;

public record ClaimedActivation(UUID eventId, UUID requestId, String externalId,
                                String customerId, String productCode, int attempts) {}
