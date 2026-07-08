package com.srecatalog.instruction.repo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.srecatalog.instruction.InstructionTestFixtures;
import com.srecatalog.instruction.model.InstructionConstants;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionRepositoryRoundTripTest {

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
    void listCurrentFiltersByOwningLobOnly() {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        Document document = documentFor(instruction, 1, Instant.now(), null);
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(List.of(document));

        assertThat(repository.listCurrent("FICC", null, 5)).hasSize(1);
    }

    @Test
    void listCurrentFiltersByStatusOnly() {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        Document document = documentFor(instruction, 1, Instant.now(), null);
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(List.of(document));

        assertThat(repository.listCurrent(null, "DRAFT", 5)).hasSize(1);
    }

    @Test
    void getCurrentDeserializesClosedVersion() {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        Instant closed = Instant.parse("2026-06-01T00:00:00Z");
        Document document = documentFor(instruction, 2, Instant.parse("2026-05-01T00:00:00Z"), closed);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(document);

        var record = repository.getCurrent("I-1");
        assertThat(record.versionNumber()).isEqualTo(2);
        assertThat(record.validOut()).isEqualTo(closed);
    }

    private static Document documentFor(
            com.srecatalog.instruction.model.CashSettlementInstruction instruction,
            int version,
            Instant validIn,
            Instant validOut) {
        ObjectMapper mapper = configuredMapper();
        Map<String, Object> payload = mapper.convertValue(instruction, Map.class);
        payload.remove("instruction_id");
        return new Document("_id", InstructionConstants.documentKey(instruction.instructionId(), version))
                .append("version_number", version)
                .append("in", validIn.toString())
                .append("out", validOut == null ? InstructionConstants.CURRENT_OUT : validOut.toString())
                .append("status", instruction.status().name())
                .append("owning_lob", instruction.owningLob())
                .append("payload", payload);
    }
}
