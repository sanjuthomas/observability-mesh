package com.observabilitymesh.instruction.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfig {

    @Bean
    MongoClient mongoClient(@Value("${spring.data.mongodb.uri}") String uri) {
        return MongoClients.create(uri);
    }

    @Bean
    @Primary
    MongoTemplate instructionMongoTemplate(
            MongoClient mongoClient,
            @Value("${spring.data.mongodb.database}") String database) {
        return new MongoTemplate(mongoClient, database);
    }

    @Bean
    MongoTemplate securityEventsMongoTemplate(
            MongoClient mongoClient,
            InstructionProperties properties) {
        return new MongoTemplate(mongoClient, properties.securityEventsDatabase());
    }

    @Bean
    MongoTransactionManager instructionTransactionManager(MongoDatabaseFactory mongoDatabaseFactory) {
        return new MongoTransactionManager(mongoDatabaseFactory);
    }
}
