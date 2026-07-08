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
import com.observabilitymesh.instruction.web.dto.RejectInstructionRequest;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionServiceTest {

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
                repository,
                securityEventRepository,
                authzClient,
                sequenceClient,
                serviceTokenHolder,
                InstructionTestFixtures.properties(),
                objectMapper);
    }

    @Test
    void createAllocatesInstructionIdAndPersistsDraft() {
        when(sequenceClient.nextInstructionId(any(), eq("FICC"))).thenReturn("I-100");
        when(serviceTokenHolder.token()).thenReturn("svc-token");
        when(serviceTokenHolder.sessionId()).thenReturn("svc-session");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("creator may draft")));
        when(securityEventRepository.allocateEventId("I-100")).thenReturn("SE-1");
        when(repository.insertInitial(any())).thenAnswer(inv -> {
            CashSettlementInstruction instruction = inv.getArgument(0);
            return new VersionedInstruction(instruction, 1, Instant.now(), null);
        });

        VersionedInstruction saved = instructionService.create(
                InstructionTestFixtures.sampleCreateRequest(),
                InstructionTestFixtures.CREATOR,
                "user-token",
                "sess");
        assertThat(saved.instruction().instructionId()).isEqualTo("I-100");
        assertThat(saved.instruction().status()).isEqualTo(InstructionStatus.DRAFT);
        verify(repository).insertInitial(any());
    }

    @Test
    void submitRejectsNonDraft() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.SUBMITTED);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));

        assertThatThrownBy(() -> instructionService.submit("I-1", InstructionTestFixtures.CREATOR, "token", "sess"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void rejectUpdatesStatus() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.SUBMITTED);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc-token");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("approver may reject")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv -> {
            CashSettlementInstruction updated = inv.getArgument(0);
            return new VersionedInstruction(updated, 2, Instant.now(), null);
        });

        VersionedInstruction saved = instructionService.reject(
                "I-1", InstructionTestFixtures.APPROVER, new RejectInstructionRequest("no"), "token", "sess");
        assertThat(saved.instruction().status()).isEqualTo(InstructionStatus.REJECTED);
    }

    @Test
    void useMarksSingleUseAsUsed() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setInstructionType(InstructionType.SINGLE_USE);
        instruction.setStatus(InstructionStatus.APPROVED);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc-token");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("marker may use")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv -> {
            CashSettlementInstruction updated = inv.getArgument(0);
            return new VersionedInstruction(updated, 2, Instant.now(), null);
        });

        VersionedInstruction saved = instructionService.use(
                "I-1",
                InstructionTestFixtures.CREATOR,
                new UseInstructionRequest("P-1", "P-1"),
                "token",
                "sess");
        assertThat(saved.instruction().status()).isEqualTo(InstructionStatus.USED);
        assertThat(saved.instruction().usedBy()).isEqualTo("P-1");
    }

    @Test
    void releaseUseRestoresApproved() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setInstructionType(InstructionType.SINGLE_USE);
        instruction.setStatus(InstructionStatus.USED);
        instruction.setUsedBy("P-1");
        instruction.setUsageCount(1);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc-token");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("release allowed")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-2");
        when(repository.appendVersion(any())).thenAnswer(inv -> {
            CashSettlementInstruction updated = inv.getArgument(0);
            return new VersionedInstruction(updated, 3, Instant.now(), null);
        });

        VersionedInstruction saved = instructionService.releaseUse(
                "I-1",
                InstructionTestFixtures.CREATOR,
                new ReleaseUseInstructionRequest("P-1"),
                "token",
                "sess");
        assertThat(saved.instruction().status()).isEqualTo(InstructionStatus.APPROVED);
        assertThat(saved.instruction().usedBy()).isNull();
    }

    @Test
    void cancelRejectsAlreadyCancelled() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.CANCELLED);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));

        assertThatThrownBy(() -> instructionService.cancel(
                "I-1", InstructionTestFixtures.CREATOR, new CancelInstructionRequest("x"), "token", "sess"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void eligibleApproversDelegatesToAuthz() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc-token");
        when(authzClient.eligibleInstructionApprovers(any(), any(), any()))
                .thenReturn(Map.of("instruction_id", "I-1", "eligible", List.of()));

        Map<String, Object> result = instructionService.eligibleApprovers("I-1");
        assertThat(result.get("instruction_id")).isEqualTo("I-1");
    }
}
