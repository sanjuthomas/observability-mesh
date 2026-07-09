package com.observabilitymesh.instruction.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.authzclient.AuthzClient;
import com.observabilitymesh.instruction.config.InstructionProperties;
import com.observabilitymesh.instruction.config.ServiceTokenHolder;
import com.observabilitymesh.instruction.metrics.InstructionLifecycleMetrics;
import com.observabilitymesh.instruction.repo.InstructionRepository;
import com.observabilitymesh.instruction.security.SecurityEventRepository;
import com.observabilitymesh.sequenceclient.SequenceClient;

import static org.mockito.Mockito.mock;

final class InstructionServiceTestFixtures {

    private InstructionServiceTestFixtures() {
    }

    static InstructionService instructionService(
            InstructionRepository repository,
            SecurityEventRepository securityEventRepository,
            AuthzClient authzClient,
            SequenceClient sequenceClient,
            ServiceTokenHolder serviceTokenHolder,
            InstructionProperties properties) {
        return new InstructionService(
                repository,
                securityEventRepository,
                authzClient,
                sequenceClient,
                serviceTokenHolder,
                properties,
                testObjectMapper(),
                mock(InstructionLifecycleMetrics.class));
    }

    static ObjectMapper testObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
