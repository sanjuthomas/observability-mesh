package com.observabilitymesh.sequence.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfig {

    @Bean
    MongoClient mongoClient(@Value("${spring.data.mongodb.uri}") String uri) {
        return MongoClients.create(uri);
    }

    @Bean
    @Primary
    MongoTemplate mongoTemplate(
            MongoClient mongoClient,
            @Value("${spring.data.mongodb.database}") String database) {
        return new MongoTemplate(mongoClient, database);
    }
}
