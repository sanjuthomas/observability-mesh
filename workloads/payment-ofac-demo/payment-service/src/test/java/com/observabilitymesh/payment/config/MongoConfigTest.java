package com.observabilitymesh.payment.config;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MongoConfigTest {

    @Mock MongoClient mongoClient;
    @Mock MongoDatabaseFactory mongoDatabaseFactory;

    @Test
    void createsPaymentAndSecurityEventTemplates() {
        MongoConfig config = new MongoConfig();
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac", "scan-requests", "security_events_db", "payment_service",
                "svc-payment", "Password1!", "", "", "", 200);

        MongoTemplate paymentTemplate = config.paymentMongoTemplate(mongoClient, "ssi_cash_activities");
        MongoTemplate ofacTemplate = config.ofacMongoTemplate(mongoClient, properties);
        MongoTemplate securityTemplate = config.securityEventsMongoTemplate(mongoClient, properties);

        assertThat(paymentTemplate).isNotNull();
        assertThat(ofacTemplate).isNotNull();
        assertThat(securityTemplate).isNotNull();

        MongoTransactionManager txManager = config.paymentTransactionManager(mongoDatabaseFactory);
        TransactionTemplate transactionTemplate = config.paymentTransactionTemplate(txManager);
        assertThat(txManager).isNotNull();
        assertThat(transactionTemplate).isNotNull();
    }
}
