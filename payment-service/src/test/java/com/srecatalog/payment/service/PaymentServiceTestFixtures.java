package com.srecatalog.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.srecatalog.authzclient.AuthzClient;
import com.srecatalog.payment.client.InstructionClient;
import com.srecatalog.payment.config.PaymentProperties;
import com.srecatalog.payment.config.ServiceIdentity;
import com.srecatalog.payment.ofac.OfacScanRequestRepository;
import com.srecatalog.payment.repo.PaymentRepository;
import com.srecatalog.payment.security.SecurityEventRepository;
import com.srecatalog.sequenceclient.SequenceClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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
