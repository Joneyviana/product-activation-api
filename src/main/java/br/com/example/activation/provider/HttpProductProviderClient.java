package br.com.example.activation.provider;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@Profile("!mock-provider")
public class HttpProductProviderClient implements ProductProviderClient {
    private final RestClient client;

    public HttpProductProviderClient(RestClient productProviderRestClient) {
        this.client = productProviderRestClient;
    }

    @Override
    public ActivationResult activate(String externalId, String customerId, String productCode) {
        try {
            var response = client.post()
                    .uri("/provider/v1/activate")
                    .header("Idempotency-Key", externalId)
                    .body(Map.of("externalId", externalId, "customerId", customerId, "productCode", productCode))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, result) -> {
                        if (result.getStatusCode().value() == 429) {
                            throw new TemporaryProviderException("Provider rate limited the request");
                        }
                        throw new PermanentProviderException("Provider rejected the activation request");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError,
                            (request, result) -> { throw new TemporaryProviderException("Provider server error"); })
                    .toBodilessEntity();
            return ActivationResult.SUCCESS;
        } catch (ResourceAccessException timeoutOrConnectionError) {
            return ActivationResult.TEMPORARY_FAILURE;
        } catch (TemporaryProviderException ex) {
            return ActivationResult.TEMPORARY_FAILURE;
        } catch (PermanentProviderException ex) {
            return ActivationResult.PERMANENT_FAILURE;
        }
    }

    static class TemporaryProviderException extends RuntimeException {
        TemporaryProviderException(String message) { super(message); }
    }
    static class PermanentProviderException extends RuntimeException {
        PermanentProviderException(String message) { super(message); }
    }
}
