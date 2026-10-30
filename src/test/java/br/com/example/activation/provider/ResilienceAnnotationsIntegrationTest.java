package br.com.example.activation.provider;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/jwks",
        "outbox.poll-interval=3600000",
        "resilience4j.retry.instances.productProvider.max-attempts=3",
        "resilience4j.retry.instances.productProvider.wait-duration=1ms",
        "resilience4j.circuitbreaker.instances.productProvider.sliding-window-size=1",
        "resilience4j.circuitbreaker.instances.productProvider.minimum-number-of-calls=1",
        "resilience4j.circuitbreaker.instances.productProvider.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.productProvider.wait-duration-in-open-state=60s"
})
class ResilienceAnnotationsIntegrationTest {

    @Autowired RetriableProductProviderCall retriableCall;
    @Autowired ResilientProductProvider resilientProvider;
    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;
    @MockBean ProductProviderClient providerClient;

    @BeforeEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> cb.reset());
        reset(providerClient);
    }

    @Test
    void retryAnnotationShouldRetryTemporaryFailuresUpToConfiguredMaximum() {
        when(providerClient.activate("EXT-RETRY", "CUST-1", "PREMIUM"))
                .thenReturn(ProductProviderClient.ActivationResult.TEMPORARY_FAILURE)
                .thenReturn(ProductProviderClient.ActivationResult.TEMPORARY_FAILURE)
                .thenReturn(ProductProviderClient.ActivationResult.SUCCESS);

        retriableCall.activate("EXT-RETRY", "CUST-1", "PREMIUM");

        verify(providerClient, times(3)).activate("EXT-RETRY", "CUST-1", "PREMIUM");
    }

    @Test
    void circuitBreakerAnnotationShouldOpenAfterRetrySequenceFails() {
        when(providerClient.activate("EXT-CIRCUIT", "CUST-1", "PREMIUM"))
                .thenReturn(ProductProviderClient.ActivationResult.TEMPORARY_FAILURE);

        assertThrows(TemporaryProviderFailureException.class,
                () -> resilientProvider.activate("EXT-CIRCUIT", "CUST-1", "PREMIUM"));
        assertThrows(CallNotPermittedException.class,
                () -> resilientProvider.activate("EXT-CIRCUIT", "CUST-1", "PREMIUM"));

        verify(providerClient, times(3)).activate("EXT-CIRCUIT", "CUST-1", "PREMIUM");
    }
}
