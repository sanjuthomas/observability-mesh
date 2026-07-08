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
import com.observabilitymesh.instruction.repo.InstructionRepository;
import com.observabilitymesh.instruction.security.SecurityEventRepository;
import com.observabilitymesh.instruction.web.dto.CancelInstructionRequest;
import com.observabilitymesh.instruction.web.dto.ReleaseUseInstructionRequest;
import com.observabilitymesh.instruction.web.dto.UseInstructionRequest;
import com.observabilitymesh.sequenceclient.SequenceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionServiceLifecycleTest {

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
    void cancelDraftInstruction() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv -> {
            CashSettlementInstruction updated = inv.getArgument(0);
            return new VersionedInstruction(updated, 2, Instant.now(), null);
        });

        VersionedInstruction saved = instructionService.cancel(
                "I-1", InstructionTestFixtures.CREATOR, new CancelInstructionRequest("reason"), "t", "s");
        assertThat(saved.instruction().status()).isEqualTo(InstructionStatus.CANCELLED);
    }

    @Test
    void useStandingKeepsApprovedStatus() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.APPROVED);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv -> {
            CashSettlementInstruction updated = inv.getArgument(0);
            return new VersionedInstruction(updated, 2, Instant.now(), null);
        });

        VersionedInstruction saved = instructionService.use(
                "I-1", InstructionTestFixtures.CREATOR, new UseInstructionRequest("P-1", "P-1"), "t", "s");
        assertThat(saved.instruction().status()).isEqualTo(InstructionStatus.APPROVED);
        assertThat(saved.instruction().usageCount()).isEqualTo(1);
    }

    @Test
    void releaseUseRejectsMismatchedPaymentReference() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setInstructionType(InstructionType.SINGLE_USE);
        instruction.setStatus(InstructionStatus.USED);
        instruction.setUsedBy("P-OTHER");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));

        assertThatThrownBy(() -> instructionService.releaseUse(
                "I-1",
                InstructionTestFixtures.CREATOR,
                new ReleaseUseInstructionRequest("P-1"),
                "t",
                "s"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
