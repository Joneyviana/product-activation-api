package br.com.example.activation.request.service;

import br.com.example.activation.outbox.repository.OutboxEventRepository;
import br.com.example.activation.request.dto.CreateActivationRequest;
import br.com.example.activation.request.repository.ActivationRequestRepository;
import br.com.example.activation.shared.TimeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:activation-concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
@Import({ActivationRequestService.class, ActivationRequestWriter.class, TimeConfiguration.class})
class ActivationRequestConcurrencyIntegrationTest {

    @Autowired ActivationRequestService service;
    @Autowired ActivationRequestRepository requestRepository;
    @Autowired OutboxEventRepository outboxRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSameExternalIdAndPayloadShouldCreateOnlyOneRequestAndOutboxEvent() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<ActivationRequestService.CreationResult> first = pool.submit(() -> {
                start.await();
                return service.create(new CreateActivationRequest("EXT-RACE-1", "CUST-1", "PREMIUM"));
            });
            Future<ActivationRequestService.CreationResult> second = pool.submit(() -> {
                start.await();
                return service.create(new CreateActivationRequest("EXT-RACE-1", "CUST-1", "PREMIUM"));
            });
            start.countDown();

            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);

            assertEquals(firstResult.response().id(), secondResult.response().id());
            assertEquals(1, requestRepository.count());
            assertEquals(1, outboxRepository.count());
            assertEquals(1, (firstResult.created() ? 1 : 0) + (secondResult.created() ? 1 : 0));
        } finally {
            pool.shutdownNow();
        }
    }
}
