package br.com.example.activation.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateActivationRequest(
        @NotBlank @Size(max = 80)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must contain only letters, numbers, hyphens or underscores")
        String externalId,

        @NotBlank @Size(max = 80)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String customerId,

        @NotBlank @Size(max = 80)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String productCode
) {}
