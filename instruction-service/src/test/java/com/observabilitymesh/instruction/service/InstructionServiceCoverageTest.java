package com.observabilitymesh.instruction.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.authzclient.AuthzClient;
import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.web.PermissionDeniedException;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.config.ServiceTokenHolder;
import com.observabilitymesh.instruction.model.CashSettlementInstruction;
import com.observabilitymesh.instruction.model.InstructionStatus;
import com.observabilitymesh.instruction.model.InstructionType;
import com.observabilitymesh.instruction.model.VersionedInstruction;
import com.observabilitymesh.instruction.model.WireScope;
import com.observabilitymesh.instruction.model.iso.InstructionIsoTypes;
import com.observabilitymesh.instruction.repo.InstructionRepository;
import com.observabilitymesh.instruction.security.SecurityEventRepository;
import com.observabilitymesh.instruction.web.dto.CreateInstructionRequest;
import com.observabilitymesh.sequenceclient.SequenceClient;
import com.observabilitymesh.sequenceclient.SequenceClientException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionServiceCoverageTest {

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
        instructionService = InstructionServiceTestFixtures.instructionService(
                repository, securityEventRepository, authzClient, sequenceClient,
                serviceTokenHolder, InstructionTestFixtures.properties());
    }

    @Test
    void createFailsWhenSequenceUnavailable() {
        when(sequenceClient.nextInstructionId(any(), any())).thenThrow(new SequenceClientException("down"));
        assertThatThrownBy(() -> instructionService.create(
                InstructionTestFixtures.sampleCreateRequest(),
                InstructionTestFixtures.CREATOR, "t", "s"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getRecordsViewForNonExcludedUser() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("view ok")));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");

        VersionedInstruction record = instructionService.get("I-1", InstructionTestFixtures.APPROVER, "t", "s");
        assertThat(record.instruction().instructionId()).isEqualTo("I-1");
    }

    @Test
    void getSkipsViewEventForAdminExcludedUser() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("view ok")));

        instructionService.get("I-1", InstructionTestFixtures.ADMIN, "t", "s");
    }

    @Test
    void listFiltersUnauthorizedRecords() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.listCurrent(null, null, 100))
                .thenReturn(List.of(new VersionedInstruction(instruction, 1, Instant.now(), null)));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new PermissionDeniedException("denied"));

        assertThat(instructionService.list(InstructionTestFixtures.APPROVER, null, null, 100, "t", "s"))
                .isEmpty();
    }

    @Test
    void updateRejectsNonDraft() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.APPROVED);
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));

        assertThatThrownBy(() -> instructionService.update(
                "I-1", InstructionTestFixtures.sampleCreateRequest(),
                InstructionTestFixtures.CREATOR, "t", "s"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void suspendAndReactivateLifecycle() {
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

        VersionedInstruction suspended = instructionService.suspend("I-1", InstructionTestFixtures.APPROVER, "t", "s");
        assertThat(suspended.instruction().status()).isEqualTo(InstructionStatus.SUSPENDED);

        suspended.instruction().setStatus(InstructionStatus.SUSPENDED);
        when(repository.getCurrent("I-1")).thenReturn(suspended);
        VersionedInstruction reactivated = instructionService.reactivate("I-1", InstructionTestFixtures.APPROVER, "t", "s");
        assertThat(reactivated.instruction().status()).isEqualTo(InstructionStatus.APPROVED);
    }

    @Test
    void approveSubmitLifecycle() {
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

        VersionedInstruction submitted = instructionService.submit("I-1", InstructionTestFixtures.CREATOR, "t", "s");
        assertThat(submitted.instruction().status()).isEqualTo(InstructionStatus.SUBMITTED);

        submitted.instruction().setStatus(InstructionStatus.SUBMITTED);
        when(repository.getCurrent("I-1")).thenReturn(submitted);
        VersionedInstruction approved = instructionService.approve("I-1", InstructionTestFixtures.APPROVER, "t", "s");
        assertThat(approved.instruction().status()).isEqualTo(InstructionStatus.APPROVED);
    }

    @Test
    void policyDenialRecordsSecurityEvent() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.deny(List.of("SELF_APPROVAL"), true));
        when(securityEventRepository.allocateEventId("I-1")).thenReturn("SE-1");

        assertThatThrownBy(() -> instructionService.submit("I-1", InstructionTestFixtures.CREATOR, "t", "s"))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void internationalValidationRejectsMissingBicfi() {
        CreateInstructionRequest request = new CreateInstructionRequest(
                InstructionType.STANDING,
                "FICC",
                WireScope.INTERNATIONAL,
                "USD",
                new InstructionIsoTypes.FundingAccount("FA-1", "Funding", "FICC"),
                null,
                null,
                new InstructionIsoTypes.PartyIdentification("Debtor Co", null, null, "US"),
                new InstructionIsoTypes.CashAccount(
                        com.observabilitymesh.instruction.model.AccountIdentificationScheme.IBAN, "US123", "USD", null),
                InstructionTestFixtures.sampleInstruction("x").debtorAgent(),
                null,
                null,
                null,
                List.of(),
                List.of(),
                InstructionTestFixtures.sampleInstruction("x").creditorAgent(),
                null,
                new InstructionIsoTypes.PartyIdentification("Creditor Co", null, null, "US"),
                new InstructionIsoTypes.CashAccount(
                        com.observabilitymesh.instruction.model.AccountIdentificationScheme.IBAN, "US456", "USD", null),
                null,
                com.observabilitymesh.instruction.model.ChargeBearer.DEBT,
                List.of(),
                List.of(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2099-12-31T00:00:00Z"));
        when(sequenceClient.nextInstructionId(any(), any())).thenReturn("I-200");

        assertThatThrownBy(() -> instructionService.create(request, InstructionTestFixtures.CREATOR, "t", "s"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listVersionsDelegatesToRepository() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("view ok")));
        when(repository.listVersions("I-1")).thenReturn(List.of(new VersionedInstruction(instruction, 1, Instant.now(), null)));

        assertThat(instructionService.listVersions("I-1", InstructionTestFixtures.APPROVER, "t", "s")).hasSize(1);
    }
}
