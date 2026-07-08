package com.observabilitymesh.instruction.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.authzclient.AuthzClient;
import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.web.PermissionDeniedException;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.config.InstructionProperties;
import com.observabilitymesh.instruction.config.ServiceTokenHolder;
import com.observabilitymesh.instruction.model.CashSettlementInstruction;
import com.observabilitymesh.instruction.model.InstructionStatus;
import com.observabilitymesh.instruction.model.InstructionType;
import com.observabilitymesh.instruction.model.VersionedInstruction;
import com.observabilitymesh.instruction.repo.ConcurrentModificationException;
import com.observabilitymesh.instruction.repo.InstructionRepository;
import com.observabilitymesh.instruction.security.SecurityEventRepository;
import com.observabilitymesh.instruction.web.dto.RejectInstructionRequest;
import com.observabilitymesh.instruction.web.dto.ReleaseUseInstructionRequest;
import com.observabilitymesh.instruction.web.dto.UseInstructionRequest;
import com.observabilitymesh.sequenceclient.SequenceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionServiceRemainingBranchesTest {

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
    void rejectTransitionsToRejected() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.SUBMITTED);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv ->
                new VersionedInstruction(inv.getArgument(0), 2, Instant.now(), null));

        VersionedInstruction saved = instructionService.reject(
                "I-1", InstructionTestFixtures.APPROVER, new RejectInstructionRequest("policy"), "t", "s");
        assertThat(saved.instruction().status()).isEqualTo(InstructionStatus.REJECTED);
        assertThat(saved.instruction().rejectionReason()).isEqualTo("policy");
    }

    @Test
    void singleUseTransitionsToUsed() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setInstructionType(InstructionType.SINGLE_USE);
        instruction.setStatus(InstructionStatus.APPROVED);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv ->
                new VersionedInstruction(inv.getArgument(0), 2, Instant.now(), null));

        VersionedInstruction saved = instructionService.use(
                "I-1", InstructionTestFixtures.CREATOR, new UseInstructionRequest("P-1", "P-1"), "t", "s");
        assertThat(saved.instruction().status()).isEqualTo(InstructionStatus.USED);
        assertThat(saved.instruction().usedBy()).isEqualTo("P-1");
    }

    @Test
    void releaseUseRestoresApprovedStatus() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setInstructionType(InstructionType.SINGLE_USE);
        instruction.setStatus(InstructionStatus.USED);
        instruction.setUsedBy("P-1");
        instruction.setUsageCount(1);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv ->
                new VersionedInstruction(inv.getArgument(0), 3, Instant.now(), null));

        VersionedInstruction saved = instructionService.releaseUse(
                "I-1", InstructionTestFixtures.CREATOR, new ReleaseUseInstructionRequest("P-1"), "t", "s");
        assertThat(saved.instruction().status()).isEqualTo(InstructionStatus.APPROVED);
        assertThat(saved.instruction().usedBy()).isNull();
        assertThat(saved.instruction().usageCount()).isZero();
    }

    @Test
    void cancelRejectsAlreadyCancelled() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.CANCELLED);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));

        assertThatThrownBy(() -> instructionService.cancel("I-1", InstructionTestFixtures.CREATOR, null, "t", "s"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancelRejectsApprovedInstruction() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.APPROVED);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));

        assertThatThrownBy(() -> instructionService.cancel("I-1", InstructionTestFixtures.CREATOR, null, "t", "s"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void releaseUseRejectsStandingInstruction() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.USED);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));

        assertThatThrownBy(() -> instructionService.releaseUse(
                "I-1", InstructionTestFixtures.CREATOR, new ReleaseUseInstructionRequest("P-1"), "t", "s"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void getMapsConcurrentModificationTo409() {
        when(repository.getCurrent("I-1")).thenThrow(new ConcurrentModificationException("conflict"));
        assertThatThrownBy(() -> instructionService.get("I-1", InstructionTestFixtures.CREATOR, "t", "s"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
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
    void policyDenialSkipsSecurityEventForExcludedUser() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        InstructionProperties properties = new InstructionProperties(
                "instructions", "security_events", "instruction_service",
                "svc-instruction", "Password1!", "COMPLIANCE_ANALYST", "user-001", "admin-001", 200);
        InstructionService service = new InstructionService(
                repository, securityEventRepository, authzClient, sequenceClient,
                serviceTokenHolder, properties, objectMapper);

        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.deny(List.of("VIEWER_ACCESS_DENIED"), false));

        assertThatThrownBy(() -> service.submit("I-1", InstructionTestFixtures.CREATOR, "t", "s"))
                .isInstanceOf(PermissionDeniedException.class);
        verify(securityEventRepository, never()).insert(any(), any());
    }

    @Test
    void eligibleApproversDelegatesToAuthzClient() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(serviceTokenHolder.sessionId()).thenReturn("sess");
        when(authzClient.eligibleInstructionApprovers(any(), eq("svc"), eq("sess")))
                .thenReturn(Map.of("instruction_id", "I-1"));

        assertThat(instructionService.eligibleApprovers("I-1")).containsEntry("instruction_id", "I-1");
    }
}
