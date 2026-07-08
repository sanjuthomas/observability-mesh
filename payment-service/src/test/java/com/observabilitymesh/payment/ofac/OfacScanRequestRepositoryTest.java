package com.observabilitymesh.payment.ofac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.payment.config.PaymentProperties;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OfacScanRequestRepositoryTest {

    @Mock MongoTemplate mongoTemplate;

    private OfacScanRequestRepository repository;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "", "", "", 200);
        repository = new OfacScanRequestRepository(mongoTemplate, paymentObjectMapper(), properties);
    }

    private static ObjectMapper paymentObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @Test
    void insertUsesCompositeDocumentId() {
        OfacScanRequest request = new OfacScanRequest(
                "P-1", 2, 1, "I-1", "FICC",
                Map.of("account_id", "D-1"),
                Map.of("account_id", "C-1"),
                "Acme",
                List.of(),
                Instant.parse("2026-07-08T12:00:00Z"),
                Instant.parse("2026-07-08T12:00:00Z"),
                OfacScanRequestConstants.CURRENT_OUT,
                OfacScanLifecycleStatus.OPEN,
                null);

        repository.insert(request);

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).insert(documentCaptor.capture(), eq("ofac-scan-requests"));
        Document document = documentCaptor.getValue();
        assertThat(document.get("_id")).isEqualTo("P-1|2|1");
        assertThat(document.get("payment_id")).isEqualTo("P-1");
        assertThat(document.get("payment_version")).isEqualTo(2);
        assertThat(document.get("version_number")).isEqualTo(1);
        assertThat(document.get("creditor_name")).isEqualTo("Acme");
        assertThat(document.get("in")).isEqualTo("2026-07-08T12:00:00Z");
        assertThat(document.get("out")).isEqualTo(OfacScanRequestConstants.CURRENT_OUT);
        assertThat(document.get("lifecycle_status")).isEqualTo("OPEN");
        assertThat(document.get("result")).isNull();
    }
}
