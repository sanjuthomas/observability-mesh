package com.observabilitymesh.payment.config;

import com.mongodb.client.MongoClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class MongoConfig {

    @Bean
    @Primary
    MongoTemplate paymentMongoTemplate(
            MongoClient mongoClient,
            @Value("${spring.data.mongodb.database}") String database) {
        return new MongoTemplate(mongoClient, database);
    }

    @Bean
    MongoTemplate securityEventsMongoTemplate(
            MongoClient mongoClient,
            PaymentProperties properties) {
        return new MongoTemplate(mongoClient, properties.securityEventsDatabase());
    }

    @Bean
    MongoTransactionManager paymentTransactionManager(MongoDatabaseFactory mongoDatabaseFactory) {
        return new MongoTransactionManager(mongoDatabaseFactory);
    }

    @Bean
    TransactionTemplate paymentTransactionTemplate(MongoTransactionManager paymentTransactionManager) {
        return new TransactionTemplate(paymentTransactionManager);
    }
}
