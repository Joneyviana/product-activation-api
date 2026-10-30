package br.com.example.activation.request.service;

import br.com.example.activation.outbox.repository.OutboxEventRepository;
import br.com.example.activation.request.dto.CreateActivationRequest;
import br.com.example.activation.request.repository.ActivationRequestRepository;
import br.com.example.activation.shared.TimeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ActivationRequestWriter.class, TimeConfiguration.class})
class ActivationRequestWriterIntegrationTest {

    @Autowired ActivationRequestWriter writer;
    @Autowired ActivationRequestRepository requestRepository;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void shouldPersistRequestAndOutboxEventTogether() {
        writer.save(new CreateActivationRequest("EXT-ATOMIC-1", "CUST-1", "PREMIUM"));

        assertEquals(1, requestRepository.count());
        assertEquals(1, outboxRepository.count());
        assertEquals("EXT-ATOMIC-1", requestRepository.findByExternalId("EXT-ATOMIC-1")
                .orElseThrow().getExternalId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRollbackRequestAndOutboxTogetherWhenOuterTransactionFails() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            writer.save(new CreateActivationRequest("EXT-ROLLBACK-1", "CUST-1", "PREMIUM"));
            throw new IllegalStateException("simulate failure before commit");
        }));

        assertEquals(0, requestRepository.count());
        assertEquals(0, outboxRepository.count());
    }
}
