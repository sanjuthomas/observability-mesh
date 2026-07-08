package com.srecatalog.payment.config;

import com.mongodb.client.MongoClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfig {

    @Bean
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
}
