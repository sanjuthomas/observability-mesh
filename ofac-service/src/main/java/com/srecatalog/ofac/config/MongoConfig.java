package com.srecatalog.ofac.config;

import com.mongodb.client.MongoClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfig {

    @Bean
    MongoTemplate ofacMongoTemplate(
            MongoClient mongoClient,
            @Value("${spring.data.mongodb.database}") String database) {
        return new MongoTemplate(mongoClient, database);
    }

    @Bean
    MongoTransactionManager ofacTransactionManager(MongoDatabaseFactory mongoDatabaseFactory) {
        return new MongoTransactionManager(mongoDatabaseFactory);
    }
}
