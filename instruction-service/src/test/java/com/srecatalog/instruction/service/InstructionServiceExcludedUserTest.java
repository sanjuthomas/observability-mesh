package com.srecatalog.instruction.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.srecatalog.authzclient.AuthzClient;
import com.srecatalog.common.model.PolicyDecision;
import com.srecatalog.common.model.Subject;
import com.srecatalog.common.web.PermissionDeniedException;
import com.srecatalog.instruction.InstructionTestFixtures;
import com.srecatalog.instruction.config.InstructionProperties;
import com.srecatalog.instruction.config.ServiceTokenHolder;
import com.srecatalog.instruction.model.CashSettlementInstruction;
import com.srecatalog.instruction.model.VersionedInstruction;
import com.srecatalog.instruction.repo.InstructionRepository;
import com.srecatalog.instruction.security.SecurityEventRepository;
import com.srecatalog.sequenceclient.SequenceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionServiceExcludedUserTest {

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
        InstructionProperties properties = new InstructionProperties(
                "instructions", "security_events", "instruction_service",
                "svc-instruction", "Password1!", "", "bot-001", "admin-001", 200);
        instructionService = new InstructionService(
                repository, securityEventRepository, authzClient, sequenceClient,
                serviceTokenHolder, properties, objectMapper);
    }

    @Test
    void policyDenialSkipsSecurityEventForExcludedUser() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));
        when(serviceTokenHolder.token()).thenReturn("svc");
        when(authzClient.evaluateInstruction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.deny(List.of("VIEWER_ACCESS_DENIED"), false));
        Subject bot = new Subject(
                "bot-001", null, null, "Bot", "FICC",
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());

        assertThatThrownBy(() -> instructionService.get("I-1", bot, "t", "s"))
                .isInstanceOf(PermissionDeniedException.class);
        verify(securityEventRepository, never()).insert(any(), any());
    }
}
