package com.observabilitymesh.instruction.security;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.model.InstructionAction;
import com.observabilitymesh.sequenceclient.SequenceClient;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityEventRepositoryTest {

    @Mock MongoTemplate mongoTemplate;
    @Mock SequenceClient sequenceClient;

    private SecurityEventRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SecurityEventRepository(
                mongoTemplate,
                configuredMapper(),
                sequenceClient,
                InstructionTestFixtures.properties());
    }

    private static ObjectMapper configuredMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void listRecentSerializesEventId() {
        Document doc = new Document("_id", "SE-1").append("timestamp", "2026-01-01T00:00:00Z");
        when(mongoTemplate.find(any(), eq(Document.class), eq("instruction_service"))).thenReturn(List.of(doc));

        List<Map<String, Object>> events = repository.listRecent(10);
        assertThat(events.get(0).get("event_id")).isEqualTo("SE-1");
    }

    @Test
    void findByEventIdReturnsNullWhenMissing() {
        when(mongoTemplate.findById("missing", Document.class, "instruction_service")).thenReturn(null);
        assertThat(repository.findByEventId("missing")).isNull();
    }

    @Test
    void insertWritesDocument() {
        when(sequenceClient.nextSecurityEventId("I-1")).thenReturn("SE-9");
        var event = InstructionSecurityEvent.authorizedAction(
                InstructionAction.CREATE,
                InstructionTestFixtures.CREATOR,
                InstructionTestFixtures.sampleInstruction("I-1"),
                1,
                Map.of(),
                configuredMapper());
        when(mongoTemplate.insert(any(Document.class), eq("instruction_service"))).thenAnswer(inv -> inv.getArgument(0));

        repository.insert(event, "SE-9");
        assertThat(repository.allocateEventId("I-1")).isEqualTo("SE-9");
    }
}
