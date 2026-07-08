package com.observabilitymesh.instruction.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.authzclient.AuthzClient;
import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.config.ServiceTokenHolder;
import com.observabilitymesh.instruction.model.CashSettlementInstruction;
import com.observabilitymesh.instruction.model.InstructionStatus;
import com.observabilitymesh.instruction.model.InstructionType;
import com.observabilitymesh.instruction.model.VersionedInstruction;
import com.observabilitymesh.instruction.repo.ConcurrentModificationException;
import com.observabilitymesh.instruction.repo.InstructionNotFoundException;
import com.observabilitymesh.instruction.repo.InstructionRepository;
import com.observabilitymesh.instruction.security.SecurityEventRepository;
import com.observabilitymesh.instruction.web.dto.CancelInstructionRequest;
import com.observabilitymesh.instruction.web.dto.ReleaseUseInstructionRequest;
import com.observabilitymesh.sequenceclient.SequenceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionServiceAdditionalBranchesTest {

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
    void getMapsNotFoundTo404() {
        when(repository.getCurrent("missing")).thenThrow(new InstructionNotFoundException("missing"));
        assertThatThrownBy(() -> instructionService.get("missing", InstructionTestFixtures.CREATOR, "t", "s"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void getMapsConcurrentModificationTo409() {
        when(repository.getCurrent("I-1")).thenThrow(new ConcurrentModificationException("conflict"));
        assertThatThrownBy(() -> instructionService.get("I-1", InstructionTestFixtures.CREATOR, "t", "s"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("conflict");
    }

    @Test
    void listExcludesCancelledInstructions() {
        CashSettlementInstruction cancelled = InstructionTestFixtures.sampleInstruction("I-1");
        cancelled.setStatus(InstructionStatus.CANCELLED);
        when(repository.listCurrent(null, null, 100))
                .thenReturn(List.of(new VersionedInstruction(cancelled, 1, Instant.now(), null)));

        assertThat(instructionService.list(InstructionTestFixtures.APPROVER, null, null, 100, "t", "s"))
                .isEmpty();
    }

    @Test
    void cancelSubmittedInstructionWithReason() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.SUBMITTED);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv ->
                new VersionedInstruction(inv.getArgument(0), 2, Instant.now(), null));

        var saved = instructionService.cancel(
                "I-1", InstructionTestFixtures.CREATOR, new CancelInstructionRequest("obsolete"), "t", "s");
        assertThat(saved.instruction().status()).isEqualTo(InstructionStatus.CANCELLED);
    }

    @Test
    void releaseUseSkipsUsageDecrementWhenAlreadyZero() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setInstructionType(InstructionType.SINGLE_USE);
        instruction.setStatus(InstructionStatus.USED);
        instruction.setUsedBy("P-1");
        instruction.setUsageCount(0);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv ->
                new VersionedInstruction(inv.getArgument(0), 3, Instant.now(), null));

        var saved = instructionService.releaseUse(
                "I-1", InstructionTestFixtures.CREATOR, new ReleaseUseInstructionRequest("P-1"), "t", "s");
        assertThat(saved.instruction().usageCount()).isZero();
    }
}
