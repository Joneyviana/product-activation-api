package br.com.example.activation.provider;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpProductProviderClientTest {

    @Test
    void shouldSendStableExternalIdAsIdempotencyKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpProductProviderClient client = new HttpProductProviderClient(
                builder.baseUrl("http://provider.test").build());

        server.expect(requestTo("http://provider.test/provider/v1/activate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "EXT-IDEMPOTENT-1"))
                .andRespond(withSuccess());

        assertEquals(ProductProviderClient.ActivationResult.SUCCESS,
                client.activate("EXT-IDEMPOTENT-1", "CUST-1", "PREMIUM"));
        server.verify();
    }

    @Test
    void rateLimitedProviderResponseShouldBeClassifiedAsTemporaryFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpProductProviderClient client = new HttpProductProviderClient(
                builder.baseUrl("http://provider.test").build());

        server.expect(requestTo("http://provider.test/provider/v1/activate"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertEquals(ProductProviderClient.ActivationResult.TEMPORARY_FAILURE,
                client.activate("EXT-429", "CUST-1", "PREMIUM"));
        server.verify();
    }
}
