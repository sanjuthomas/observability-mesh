package com.observabilitymesh.instruction.config;

import com.mongodb.client.MongoClient;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MongoConfigTest {

    @Mock MongoClient mongoClient;

    @Test
    void createsInstructionAndSecurityTemplates() {
        MongoConfig config = new MongoConfig();
        MongoTemplate instructions = config.instructionMongoTemplate(mongoClient, "ssi_cash_instructions");
        MongoTemplate events = config.securityEventsMongoTemplate(mongoClient, InstructionTestFixtures.properties());
        assertThat(instructions).isNotNull();
        assertThat(events).isNotNull();
    }

    @Test
    void createsTransactionManager() {
        MongoConfig config = new MongoConfig();
        assertThat(config.instructionTransactionManager(mock(MongoDatabaseFactory.class))).isNotNull();
    }
}
