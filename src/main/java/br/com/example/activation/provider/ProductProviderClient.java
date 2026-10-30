package br.com.example.activation.provider;

public interface ProductProviderClient {
    ActivationResult activate(String externalId, String customerId, String productCode);

    enum ActivationResult {
        SUCCESS, TEMPORARY_FAILURE, PERMANENT_FAILURE
    }
}
