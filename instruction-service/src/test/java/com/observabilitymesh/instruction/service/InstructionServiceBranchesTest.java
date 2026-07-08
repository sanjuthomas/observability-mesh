package com.observabilitymesh.instruction.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.authzclient.AuthzClient;
import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.config.ServiceTokenHolder;
import com.observabilitymesh.instruction.model.CashSettlementInstruction;
import com.observabilitymesh.instruction.model.VersionedInstruction;
import com.observabilitymesh.instruction.repo.InstructionRepository;
import com.observabilitymesh.instruction.security.SecurityEventRepository;
import com.observabilitymesh.instruction.web.dto.CreateInstructionRequest;
import com.observabilitymesh.sequenceclient.SequenceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionServiceBranchesTest {

    @Mock InstructionRepository repository;
    @Mock SecurityEventRepository securityEventRepository;
    @Mock AuthzClient authzClient;
    @Mock SequenceClient sequenceClient;
    @Mock ServiceTokenHolder serviceTokenHolder;

    private InstructionService instructionService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        instructionService = new InstructionService(
                repository, securityEventRepository, authzClient, sequenceClient,
                serviceTokenHolder, InstructionTestFixtures.properties(), objectMapper);
    }

    @Test
    void updateAuthorizesAndAppendsVersion() {
        CashSettlementInstruction existing = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(existing, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("update ok")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv -> {
            CashSettlementInstruction updated = inv.getArgument(0);
            return new VersionedInstruction(updated, 2, Instant.now(), null);
        });

        CreateInstructionRequest request = InstructionTestFixtures.sampleCreateRequest();
        VersionedInstruction saved = instructionService.update(
                "I-1", request, InstructionTestFixtures.CREATOR, "token", "sess");
        assertThat(saved.versionNumber()).isEqualTo(2);
    }

    @Test
    void listIncludesAuthorizedRecords() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.listCurrent("FICC", "DRAFT", 50))
                .thenReturn(List.of(new VersionedInstruction(instruction, 1, Instant.now(), null)));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("view ok")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");

        assertThat(instructionService.list(
                InstructionTestFixtures.APPROVER, "FICC", "DRAFT", 50, "t", "s")).hasSize(1);
    }

    @Test
    void cancelWithoutReasonUsesEmptyDetails() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv ->
                new VersionedInstruction(inv.getArgument(0), 2, Instant.now(), null));

        assertThat(instructionService.cancel("I-1", InstructionTestFixtures.CREATOR, null, "t", "s")
                .instruction().status().name()).isEqualTo("CANCELLED");
    }

    @Test
    void subjectWithoutAccessCannotViewOthersInstruction() {
        Subject outsider = new Subject(
                "outsider", null, null, "VP", "FX",
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of("FX"), null, List.of());
        assertThat(SubjectAccess.canViewInstruction(
                outsider, InstructionTestFixtures.sampleInstruction("I-1"))).isFalse();
    }
}
