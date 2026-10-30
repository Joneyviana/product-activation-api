package br.com.example.activation.provider;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

/** A retry proxy is kept in a separate Spring bean so the annotation is intercepted. */
@Service
public class RetriableProductProviderCall {
    private final ProductProviderClient provider;

    public RetriableProductProviderCall(ProductProviderClient provider) {
        this.provider = provider;
    }

    @Retry(name = "productProvider")
    public ProductProviderClient.ActivationResult activate(String externalId, String customerId, String productCode) {
        var result = provider.activate(externalId, customerId, productCode);
        if (result == ProductProviderClient.ActivationResult.TEMPORARY_FAILURE) {
            throw new TemporaryProviderFailureException("Temporary product provider failure");
        }
        return result;
    }
}
