package com.observabilitymesh.ofac.repo;

import com.observabilitymesh.ofac.config.OfacProperties;
import com.observabilitymesh.ofac.model.OfacScanLifecycleStatus;
import com.observabilitymesh.ofac.model.OfacScanRequestConstants;
import com.observabilitymesh.ofac.model.OfacScanRequestRef;
import com.observabilitymesh.ofac.model.OfacScanResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfacScanRequestRepositoryTest {

    @Mock MongoTemplate mongoTemplate;

    private OfacScanRequestRepository repository;

    @BeforeEach
    void setUp() {
        OfacProperties properties = new OfacProperties("ofac-scan-requests", 30_000, 30_000, 60_000);
        repository = new OfacScanRequestRepository(mongoTemplate, properties);
    }

    @Test
    void listOpenCurrentReturnsRefs() {
        Document open = sampleDocument("P-1", 2, 1, OfacScanLifecycleStatus.OPEN, null);
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("ofac-scan-requests")))
                .thenReturn(List.of(open));

        List<OfacScanRequestRef> refs = repository.listOpenCurrent();

        assertThat(refs).containsExactly(
                new OfacScanRequestRef("P-1", 2, 1, Instant.parse("2026-07-08T12:00:00Z")));
    }

    @Test
    void transitionClosesCurrentAndInsertsNextVersion() {
        Document current = sampleDocument("P-1", 2, 1, OfacScanLifecycleStatus.OPEN, null);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("ofac-scan-requests")))
                .thenReturn(current);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq("ofac-scan-requests")))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));
        when(mongoTemplate.insert(any(Document.class), eq("ofac-scan-requests")))
                .thenAnswer(inv -> inv.getArgument(0));

        OfacScanRequestRef saved = repository.transition(
                "P-1", 2, 1, OfacScanLifecycleStatus.IN_PROGRESS, null);

        assertThat(saved).isEqualTo(
                new OfacScanRequestRef("P-1", 2, 2, Instant.parse("2026-07-08T12:00:00Z")));

        ArgumentCaptor<Document> insertCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).insert(insertCaptor.capture(), eq("ofac-scan-requests"));
        Document inserted = insertCaptor.getValue();
        assertThat(inserted.getString("_id")).isEqualTo("P-1|2|2");
        assertThat(inserted.getString("lifecycle_status")).isEqualTo("IN_PROGRESS");
        assertThat(inserted.getString("out")).isEqualTo(OfacScanRequestConstants.CURRENT_OUT);
        assertThat(inserted.containsKey("result")).isFalse();
    }

    @Test
    void transitionWritesProcessedResult() {
        Document current = sampleDocument("P-1", 2, 2, OfacScanLifecycleStatus.IN_PROGRESS, null);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("ofac-scan-requests")))
                .thenReturn(current);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq("ofac-scan-requests")))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));
        when(mongoTemplate.insert(any(Document.class), eq("ofac-scan-requests")))
                .thenAnswer(inv -> inv.getArgument(0));

        OfacScanRequestRef saved = repository.transition(
                "P-1", 2, 2, OfacScanLifecycleStatus.PROCESSED, OfacScanResult.PASSED);

        assertThat(saved.versionNumber()).isEqualTo(3);

        ArgumentCaptor<Document> insertCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).insert(insertCaptor.capture(), eq("ofac-scan-requests"));
        assertThat(insertCaptor.getValue().getString("lifecycle_status")).isEqualTo("PROCESSED");
        assertThat(insertCaptor.getValue().getString("result")).isEqualTo("PASSED");
    }

    @Test
    void transitionThrowsWhenCurrentMissing() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("ofac-scan-requests")))
                .thenReturn(null);

        assertThatThrownBy(() -> repository.transition(
                "missing", 1, 1, OfacScanLifecycleStatus.IN_PROGRESS, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transitionThrowsOnVersionMismatch() {
        Document current = sampleDocument("P-1", 2, 2, OfacScanLifecycleStatus.IN_PROGRESS, null);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("ofac-scan-requests")))
                .thenReturn(current);

        assertThatThrownBy(() -> repository.transition(
                "P-1", 2, 1, OfacScanLifecycleStatus.PROCESSED, OfacScanResult.FAILED))
                .isInstanceOf(ConcurrentModificationException.class);
    }

    @Test
    void transitionThrowsOnCloseRace() {
        Document current = sampleDocument("P-1", 2, 1, OfacScanLifecycleStatus.OPEN, null);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("ofac-scan-requests")))
                .thenReturn(current);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq("ofac-scan-requests")))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(0, 0L, null));

        assertThatThrownBy(() -> repository.transition(
                "P-1", 2, 1, OfacScanLifecycleStatus.IN_PROGRESS, null))
                .isInstanceOf(ConcurrentModificationException.class);
    }

    private static Document sampleDocument(
            String paymentId,
            int paymentVersion,
            int versionNumber,
            OfacScanLifecycleStatus lifecycleStatus,
            OfacScanResult result) {
        Document document = new Document("_id", OfacScanRequestConstants.documentKey(paymentId, paymentVersion, versionNumber))
                .append("payment_id", paymentId)
                .append("payment_version", paymentVersion)
                .append("version_number", versionNumber)
                .append("instruction_id", "I-1")
                .append("owning_lob", "FICC")
                .append("debtor_account", new Document("account_id", "D-1"))
                .append("creditor_account", new Document("account_id", "C-1"))
                .append("creditor_name", "Acme")
                .append("intermediaries", List.of())
                .append("requested_at", "2026-07-08T12:00:00Z")
                .append("in", "2026-07-08T12:00:00Z")
                .append("out", OfacScanRequestConstants.CURRENT_OUT)
                .append("lifecycle_status", lifecycleStatus.name());
        if (result != null) {
            document.append("result", result.name());
        }
        return document;
    }
}
