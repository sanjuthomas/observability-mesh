package com.srecatalog.payment.repo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.srecatalog.common.model.Subject;
import com.srecatalog.payment.config.PaymentProperties;
import com.srecatalog.payment.model.Payment;
import com.srecatalog.payment.model.PaymentConstants;
import com.srecatalog.payment.model.PaymentStatus;
import com.srecatalog.payment.service.PaymentAuthorization;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRepositoryExtendedTest {

    @Mock MongoTemplate mongoTemplate;

    private PaymentRepository repository;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "", "", "", 200);
        repository = new PaymentRepository(mongoTemplate, configuredMapper(), properties);
    }

    private static ObjectMapper configuredMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void appendVersionThrowsWhenCurrentMissing() {
        Payment payment = samplePayment("P-1");
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("payments"))).thenReturn(null);
        assertThatThrownBy(() -> repository.appendVersion(payment))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void appendVersionThrowsOnDuplicateKey() {
        Payment payment = samplePayment("P-1");
        Document current = currentDocument("P-1", 1);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("payments"))).thenReturn(current);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq("payments")))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));
        when(mongoTemplate.insert(any(Document.class), eq("payments")))
                .thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> repository.appendVersion(payment))
                .isInstanceOf(ConcurrentModificationException.class);
    }

    @Test
    void getCurrentRoundTripsPayload() {
        Document doc = currentDocument("P-1", 1);
        doc.put("payload", payloadFor("P-1", PaymentStatus.DRAFT));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("payments"))).thenReturn(doc);

        var record = repository.getCurrent("P-1");
        assertThat(record.payment().paymentId()).isEqualTo("P-1");
        assertThat(record.payment().status()).isEqualTo(PaymentStatus.DRAFT);
        assertThat(record.validOut()).isNull();
    }

    @Test
    void getCurrentParsesHistoricalOutTimestamp() {
        Document doc = currentDocument("P-1", 2);
        doc.put("out", "2026-07-01T00:00:00Z");
        doc.put("payload", payloadFor("P-1", PaymentStatus.APPROVED));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("payments"))).thenReturn(doc);

        var record = repository.getCurrent("P-1");
        assertThat(record.validOut()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
    }

    @Test
    void listCurrentAppliesInstructionAndStatusFilters() {
        Document doc = currentDocument("P-1", 1);
        doc.put("payload", payloadFor("P-1", PaymentStatus.DRAFT));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("payments"))).thenReturn(List.of(doc));

        assertThat(repository.listCurrent("I-1", "DRAFT", 5, true)).hasSize(1);
    }

    private static Document currentDocument(String paymentId, int version) {
        return new Document("_id", paymentId + "|" + version)
                .append("version_number", version)
                .append("out", PaymentConstants.CURRENT_OUT)
                .append("in", "2026-07-07T00:00:00Z")
                .append("status", "DRAFT")
                .append("owning_lob", "FICC")
                .append("instruction_id", "I-1");
    }

    private static Document payloadFor(String paymentId, PaymentStatus status) {
        return new Document("status", status.name())
                .append("instruction_id", "I-1")
                .append("instruction_version", 1)
                .append("amount", 100.0)
                .append("currency", "USD")
                .append("value_date", "2026-07-01")
                .append("owning_lob", "FICC")
                .append("instruction_type", "STANDING")
                .append("created_by", new Document("user_id", "u1").append("title", "VP").append("roles", List.of("PAYMENT_CREATOR")))
                .append("created_at", "2026-07-07T00:00:00Z")
                .append("updated_at", "2026-07-07T00:00:00Z")
                .append("lifecycle_events", List.of());
    }

    private static Payment samplePayment(String paymentId) {
        Subject subject = new Subject("u1", "A", "B", "VP", "FICC", List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());
        return Payment.create(paymentId, "I-1", 1, 100.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
    }
}
