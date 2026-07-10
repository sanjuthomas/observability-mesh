package com.observabilitymesh.instruction.repo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.model.InstructionConstants;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionRepositoryDuplicateKeyTest {

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
    void appendVersionMapsDuplicateKeyToConcurrentModification() {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        Document current = new Document("_id", "I-1|1")
                .append("version_number", 1)
                .append("out", InstructionConstants.CURRENT_OUT);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(current);
        when(mongoTemplate.updateFirst(any(Query.class), any(), eq("instructions")))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));
        when(mongoTemplate.insert(any(Document.class), eq("instructions"))).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> repository.appendVersion(instruction))
                .isInstanceOf(ConcurrentModificationException.class);
    }

    @Test
    void listVersionsReturnsSortedRecords() {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        Document v1 = new Document("_id", "I-1|1")
                .append("version_number", 1)
                .append("out", "2026-01-02T00:00:00Z")
                .append("in", "2026-01-01T00:00:00Z")
                .append("status", "DRAFT")
                .append("owning_lob", "FICC")
                .append("payload", configuredMapper().convertValue(instruction, java.util.Map.class));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(List.of(v1));

        assertThat(repository.listVersions("I-1")).hasSize(1);
    }
}
