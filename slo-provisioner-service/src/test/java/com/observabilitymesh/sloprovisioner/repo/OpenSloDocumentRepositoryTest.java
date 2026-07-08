package com.observabilitymesh.sloprovisioner.repo;

import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import com.observabilitymesh.sloprovisioner.model.OpenSloDocumentView;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenSloDocumentRepositoryTest {

    @Mock MongoTemplate mongoTemplate;

    private OpenSloDocumentRepository repository;

    @BeforeEach
    void setUp() {
        SloProvisionerProperties properties = new SloProvisionerProperties(
                60_000, "service-level-objectives", "slo-provision-state",
                "/rules", "_archive", "", "sloth", "/work", "payment-prometheus");
        repository = new OpenSloDocumentRepository(mongoTemplate, properties);
    }

    @Test
    void listActiveByKindMapsDocuments() {
        Document document = new Document("_id", "1")
                .append("logicalKey", "openslo/v1/SLO/demo")
                .append("version", 1)
                .append("stale", false)
                .append("kind", "SLO")
                .append("name", "demo")
                .append("content", Map.of("spec", Map.of()));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("service-level-objectives")))
                .thenReturn(List.of(document));

        List<OpenSloDocumentView> views = repository.listActiveByKind("SLO");

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().name()).isEqualTo("demo");
    }

    @Test
    void listActiveSloLogicalKeysReturnsKeys() {
        Document document = new Document("_id", "1")
                .append("logicalKey", "openslo/v1/SLO/demo")
                .append("version", 1)
                .append("stale", false)
                .append("kind", "SLO")
                .append("name", "demo")
                .append("content", Map.of());
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("service-level-objectives")))
                .thenReturn(List.of(document));

        assertThat(repository.listActiveSloLogicalKeys()).containsExactly("openslo/v1/SLO/demo");
    }

    @Test
    void findActiveByKindAndNameReturnsEmptyWhenMissing() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("service-level-objectives")))
                .thenReturn(null);
        assertThat(repository.findActiveByKindAndName("SLI", "missing")).isEmpty();
    }

    @Test
    void findActiveByKindAndNameReturnsOptional() {
        Document document = new Document("_id", "2")
                .append("logicalKey", "openslo/v1/SLI/demo")
                .append("version", 1)
                .append("stale", false)
                .append("kind", "SLI")
                .append("name", "demo")
                .append("content", Map.of());
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("service-level-objectives")))
                .thenReturn(document);

        assertThat(repository.findActiveByKindAndName("SLI", "demo")).isPresent();
    }
}
