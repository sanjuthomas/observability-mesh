package com.srecatalog.sloprovisioner.repo;

import com.srecatalog.sloprovisioner.config.SloProvisionerProperties;
import com.srecatalog.sloprovisioner.model.ProvisionStatus;
import com.srecatalog.sloprovisioner.model.SloProvisionState;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SloProvisionStateRepositoryTest {

    @Mock MongoTemplate mongoTemplate;

    private SloProvisionStateRepository repository;

    @BeforeEach
    void setUp() {
        SloProvisionerProperties properties = new SloProvisionerProperties(
                60_000, "service-level-objectives", "slo-provision-state",
                "/rules", "_archive", "", "sloth", "/work", "payment-prometheus");
        repository = new SloProvisionStateRepository(mongoTemplate, properties);
    }

    @Test
    void findByLogicalKeyReturnsNullWhenMissing() {
        when(mongoTemplate.findById("missing", Document.class, "slo-provision-state")).thenReturn(null);
        assertThat(repository.findByLogicalKey("missing")).isNull();
    }

    @Test
    void findByLogicalKeyReturnsState() {
        Document document = new Document("_id", "key")
                .append("logicalKey", "key")
                .append("opensloVersion", 2)
                .append("status", "ACTIVE")
                .append("rulesFileName", "demo.yml")
                .append("contentHash", "abc")
                .append("lastSyncedAt", Instant.parse("2026-01-01T00:00:00Z").toString())
                .append("lastError", null);
        when(mongoTemplate.findById("key", Document.class, "slo-provision-state")).thenReturn(document);

        SloProvisionState state = repository.findByLogicalKey("key");

        assertThat(state.status()).isEqualTo(ProvisionStatus.ACTIVE);
        assertThat(state.opensloVersion()).isEqualTo(2);
    }

    @Test
    void listByStatusMapsDocuments() {
        Document document = new Document("_id", "key")
                .append("logicalKey", "key")
                .append("opensloVersion", 1)
                .append("status", "ARCHIVED")
                .append("rulesFileName", "demo.yml")
                .append("contentHash", "abc")
                .append("lastSyncedAt", Instant.now().toString())
                .append("lastError", "gone");
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("slo-provision-state")))
                .thenReturn(List.of(document));

        List<SloProvisionState> states = repository.listByStatus(ProvisionStatus.ARCHIVED);

        assertThat(states).hasSize(1);
        assertThat(states.getFirst().lastError()).isEqualTo("gone");
    }

    @Test
    void upsertPersistsDocument() {
        SloProvisionState state = new SloProvisionState(
                "key", 1, ProvisionStatus.ACTIVE, "demo.yml", "hash", Instant.now(), null);

        repository.upsert(state);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).save(captor.capture(), eq("slo-provision-state"));
        assertThat(captor.getValue().getString("status")).isEqualTo("ACTIVE");
    }
}
