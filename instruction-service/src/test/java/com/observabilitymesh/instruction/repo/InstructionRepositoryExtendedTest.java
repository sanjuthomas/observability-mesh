package com.observabilitymesh.instruction.repo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.model.CashSettlementInstruction;
import com.observabilitymesh.instruction.model.InstructionConstants;
import com.observabilitymesh.instruction.model.InstructionStatus;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionRepositoryExtendedTest {

    @Mock MongoTemplate mongoTemplate;

    private InstructionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InstructionRepository(mongoTemplate, configuredMapper(), InstructionTestFixtures.properties());
    }

    private static ObjectMapper configuredMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void listCurrentReturnsDeserializedRecords() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        Document document = new Document("_id", "I-1|1")
                .append("version_number", 1)
                .append("out", InstructionConstants.CURRENT_OUT)
                .append("in", Instant.now().toString())
                .append("status", "DRAFT")
                .append("owning_lob", "FICC")
                .append("payload", configuredMapper().convertValue(instruction, Map.class));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(List.of(document));

        var records = repository.listCurrent(null, null, 10);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).instruction().instructionId()).isEqualTo("I-1");
    }

    @Test
    void appendVersionThrowsConcurrentModificationWhenUpdateFails() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        Document current = new Document("_id", "I-1|1")
                .append("version_number", 1)
                .append("out", InstructionConstants.CURRENT_OUT);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(current);
        when(mongoTemplate.updateFirst(any(Query.class), any(), eq("instructions")))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(0, 0L, null));

        assertThatThrownBy(() -> repository.appendVersion(instruction))
                .isInstanceOf(ConcurrentModificationException.class);
    }

    @Test
    void listCurrentAppliesOwningLobAndStatusFilters() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(List.of());
        assertThat(repository.listCurrent("FICC", "DRAFT", 5)).isEmpty();
    }

    @Test
    void isCancelledDetectsCancelledStatus() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.CANCELLED);
        assertThat(repository.isCancelled(new com.observabilitymesh.instruction.model.VersionedInstruction(
                instruction, 1, Instant.now(), null))).isTrue();
    }

    @Test
    void fromDocumentParsesClosedOutTimestamp() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        Instant closed = Instant.parse("2026-06-01T00:00:00Z");
        Document document = new Document("_id", "I-1|1")
                .append("version_number", 1)
                .append("out", closed.toString())
                .append("in", Instant.parse("2026-05-01T00:00:00Z").toString())
                .append("status", "DRAFT")
                .append("owning_lob", "FICC")
                .append("payload", configuredMapper().convertValue(instruction, Map.class));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(List.of(document));

        var versions = repository.listVersions("I-1");
        assertThat(versions.get(0).validOut()).isEqualTo(closed);
    }

    @Test
    void getCurrentThrowsWhenMissing() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(null);
        assertThatThrownBy(() -> repository.getCurrent("missing"))
                .isInstanceOf(InstructionNotFoundException.class);
    }

    @Test
    void appendVersionThrowsWhenCurrentMissing() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(null);
        assertThatThrownBy(() -> repository.appendVersion(instruction))
                .isInstanceOf(InstructionNotFoundException.class);
    }

    @Test
    void listCurrentIgnoresBlankFilters() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(List.of());
        assertThat(repository.listCurrent("  ", "  ", 5)).isEmpty();
    }

    @Test
    void isCancelledReturnsFalseForActiveInstruction() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        assertThat(repository.isCancelled(new com.observabilitymesh.instruction.model.VersionedInstruction(
                instruction, 1, Instant.now(), null))).isFalse();
    }

    @Test
    void appendVersionThrowsOnDuplicateKey() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        Document current = new Document("_id", "I-1|1")
                .append("version_number", 1)
                .append("out", InstructionConstants.CURRENT_OUT);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(current);
        when(mongoTemplate.updateFirst(any(Query.class), any(), eq("instructions")))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));
        when(mongoTemplate.insert(any(Document.class), eq("instructions")))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));

        assertThatThrownBy(() -> repository.appendVersion(instruction))
                .isInstanceOf(ConcurrentModificationException.class);
    }
}
