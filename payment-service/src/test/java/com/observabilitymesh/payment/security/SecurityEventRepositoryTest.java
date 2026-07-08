package com.observabilitymesh.payment.security;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.config.PaymentProperties;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.model.PaymentAction;
import com.observabilitymesh.payment.service.PaymentAuthorization;
import com.observabilitymesh.sequenceclient.SequenceClient;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityEventRepositoryTest {

    @Mock MongoTemplate mongoTemplate;
    @Mock SequenceClient sequenceClient;

    private SecurityEventRepository repository;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "", "", "", 200);
        repository = new SecurityEventRepository(mongoTemplate, configuredMapper(), sequenceClient, properties);
    }

    private static ObjectMapper configuredMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void insertPersistsDocumentWithId() {
        when(sequenceClient.nextSecurityEventId("P-1")).thenReturn("SE-100");
        Subject subject = new Subject("u1", null, null, "VP", "FICC", List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());
        Payment payment = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        PaymentSecurityEvent event = PaymentSecurityEvent.authorizedAction(
                PaymentAction.CREATE, subject, payment, 1, Map.of());

        repository.insert(event, "SE-100");
        verify(mongoTemplate).insert(any(Document.class), eq("payment_service"));
    }

    @Test
    void listRecentAddsEventId() {
        when(mongoTemplate.find(any(), eq(Document.class), eq("payment_service")))
                .thenReturn(List.of(new Document("_id", "SE-1").append("severity", "INFO")));
        List<Map<String, Object>> events = repository.listRecent(10);
        assertThat(events.get(0).get("event_id")).isEqualTo("SE-1");
    }

    @Test
    void findByEventIdReturnsNullWhenMissing() {
        when(mongoTemplate.findById("missing", Document.class, "payment_service")).thenReturn(null);
        assertThat(repository.findByEventId("missing")).isNull();
    }
}
