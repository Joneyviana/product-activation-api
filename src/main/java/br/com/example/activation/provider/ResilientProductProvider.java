package br.com.example.activation.provider;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

/**
 * Circuit Breaker intentionally wraps the Retry call. Keeping Retry in a separate
 * Spring bean makes the proxy boundary explicit and lets the circuit count the
 * final outcome of the whole retry sequence, rather than each HTTP attempt.
 */
@Service
public class ResilientProductProvider {
    private final RetriableProductProviderCall retriableCall;

    public ResilientProductProvider(RetriableProductProviderCall retriableCall) {
        this.retriableCall = retriableCall;
    }

    @CircuitBreaker(name = "productProvider")
    public ProductProviderClient.ActivationResult activate(String externalId, String customerId, String productCode) {
        return retriableCall.activate(externalId, customerId, productCode);
    }
}
