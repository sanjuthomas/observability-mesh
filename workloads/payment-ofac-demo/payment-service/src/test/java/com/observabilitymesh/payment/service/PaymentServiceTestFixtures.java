package com.observabilitymesh.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.authzclient.AuthzClient;
import com.observabilitymesh.payment.client.InstructionClient;
import com.observabilitymesh.payment.config.PaymentProperties;
import com.observabilitymesh.payment.config.ServiceIdentity;
import com.observabilitymesh.payment.metrics.PaymentLifecycleMetrics;
import com.observabilitymesh.payment.metrics.PaymentSecurityEventMetrics;
import com.observabilitymesh.payment.ofac.OfacScanRequestRepository;
import com.observabilitymesh.payment.repo.PaymentRepository;
import com.observabilitymesh.payment.security.SecurityEventRepository;
import com.observabilitymesh.sequenceclient.SequenceClient;

import static org.mockito.Mockito.mock;

final class PaymentServiceTestFixtures {

    private PaymentServiceTestFixtures() {
    }

    static PaymentService paymentService(
            PaymentRepository repository,
            SecurityEventRepository securityEventRepository,
            OfacScanRequestRepository ofacScanRequestRepository,
            AuthzClient authzClient,
            InstructionClient instructionClient,
            SequenceClient sequenceClient,
            ServiceIdentity serviceIdentity,
            PaymentProperties properties) {
        return new PaymentService(
                repository,
                securityEventRepository,
                ofacScanRequestRepository,
                authzClient,
                instructionClient,
                sequenceClient,
                serviceIdentity,
                properties,
                testObjectMapper(),
                mock(PaymentLifecycleMetrics.class),
                mock(PaymentSecurityEventMetrics.class));
    }

    static ObjectMapper testObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
