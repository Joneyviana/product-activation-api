package br.com.example.activation.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetriableProductProviderCallTest {
    @Test
    void shouldReturnSuccessfulProviderResult() {
        ProductProviderClient provider = mock(ProductProviderClient.class);
        when(provider.activate("EXT-1", "CUST-1", "PROD-1"))
                .thenReturn(ProductProviderClient.ActivationResult.SUCCESS);
        var call = new RetriableProductProviderCall(provider);

        assertEquals(ProductProviderClient.ActivationResult.SUCCESS,
                call.activate("EXT-1", "CUST-1", "PROD-1"));
    }

    @Test
    void shouldExposeTemporaryFailureForRetryAspect() {
        ProductProviderClient provider = mock(ProductProviderClient.class);
        when(provider.activate("EXT-1", "CUST-1", "PROD-1"))
                .thenReturn(ProductProviderClient.ActivationResult.TEMPORARY_FAILURE);
        var call = new RetriableProductProviderCall(provider);

        assertThrows(TemporaryProviderFailureException.class,
                () -> call.activate("EXT-1", "CUST-1", "PROD-1"));
    }
}
