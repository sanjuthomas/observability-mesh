package com.observabilitymesh.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.authzclient.AuthzClient;
import com.observabilitymesh.payment.client.InstructionClient;
import com.observabilitymesh.payment.config.PaymentProperties;
import com.observabilitymesh.payment.config.ServiceIdentity;
import com.observabilitymesh.payment.metrics.PaymentLifecycleMetrics;
import com.observabilitymesh.payment.ofac.OfacScanRequestRepository;
import com.observabilitymesh.payment.repo.PaymentRepository;
import com.observabilitymesh.payment.security.SecurityEventRepository;
import com.observabilitymesh.sequenceclient.SequenceClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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
                passthroughTransactionTemplate());
    }

    static ObjectMapper testObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    static TransactionTemplate passthroughTransactionTemplate() {
        PlatformTransactionManager txManager = new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
        return new TransactionTemplate(txManager);
    }
}
