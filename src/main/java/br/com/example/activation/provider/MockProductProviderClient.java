package br.com.example.activation.provider;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mock-provider")
public class MockProductProviderClient implements ProductProviderClient {
    @Override
    public ActivationResult activate(String externalId, String customerId, String productCode) {
        if (productCode.endsWith("-TEMP")) return ActivationResult.TEMPORARY_FAILURE;
        if (productCode.endsWith("-FAIL")) return ActivationResult.PERMANENT_FAILURE;
        return ActivationResult.SUCCESS;
    }
}
