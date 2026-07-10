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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionRepositoryTest {

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
    void insertInitialWritesVersionOneDocument() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(mongoTemplate.insert(any(Document.class), eq("instructions"))).thenAnswer(inv -> inv.getArgument(0));

        var saved = repository.insertInitial(instruction);
        assertThat(saved.versionNumber()).isEqualTo(1);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).insert(captor.capture(), eq("instructions"));
        assertThat(captor.getValue().getString("_id")).isEqualTo("I-1|1");
        assertThat(captor.getValue().getString("out")).isEqualTo(InstructionConstants.CURRENT_OUT);
    }

    @Test
    void appendVersionClosesCurrentAndInsertsNext() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.SUBMITTED);
        Document current = new Document("_id", "I-1|1")
                .append("version_number", 1)
                .append("out", InstructionConstants.CURRENT_OUT)
                .append("in", "2026-07-07T00:00:00Z")
                .append("status", "DRAFT")
                .append("owning_lob", "FICC")
                .append("payload", new Document());
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(current);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq("instructions")))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));
        when(mongoTemplate.insert(any(Document.class), eq("instructions"))).thenAnswer(inv -> inv.getArgument(0));

        var saved = repository.appendVersion(instruction);
        assertThat(saved.versionNumber()).isEqualTo(2);
        assertThat(saved.instruction().status()).isEqualTo(InstructionStatus.SUBMITTED);
    }

    @Test
    void getCurrentThrowsWhenMissing() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("instructions"))).thenReturn(null);
        assertThatThrownBy(() -> repository.getCurrent("I-missing"))
                .isInstanceOf(InstructionNotFoundException.class);
    }
}
