package com.srecatalog.instruction.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.srecatalog.authzclient.AuthzClient;
import com.srecatalog.instruction.InstructionTestFixtures;
import com.srecatalog.instruction.config.ServiceTokenHolder;
import com.srecatalog.instruction.repo.InstructionNotFoundException;
import com.srecatalog.instruction.repo.InstructionRepository;
import com.srecatalog.instruction.security.SecurityEventRepository;
import com.srecatalog.sequenceclient.SequenceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionServiceNotFoundTest {

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
        assertThatThrownBy(() -> instructionService.get(
                "missing", InstructionTestFixtures.CREATOR, "t", "s"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
