package com.observabilitymesh.ofac.config;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MongoConfigTest {

    @Mock MongoClient mongoClient;
    @Mock MongoDatabaseFactory mongoDatabaseFactory;

    @Test
    void createsMongoTemplateAndTransactionManager() {
        MongoConfig config = new MongoConfig();

        MongoTemplate template = config.ofacMongoTemplate(mongoClient, "ofac");
        MongoTransactionManager txManager = config.ofacTransactionManager(mongoDatabaseFactory);

        assertThat(template).isNotNull();
        assertThat(txManager).isNotNull();
    }
}
