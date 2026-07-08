package com.srecatalog.harness.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.srecatalog.harness.config.HarnessProperties;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityEventCounterTest {

    @Mock MongoClient mongoClient;
    @Mock MongoDatabase database;
    @Mock MongoCollection<Document> collection;

    private SecurityEventCounter counter;

    @BeforeEach
    void setUp() {
        HarnessProperties properties = new HarnessProperties(
                "http://localhost:9000",
                "/api/v1",
                "http://localhost:9093",
                "/api/v1",
                "classpath:users.yaml",
                "Password1!",
                "ssi.local",
                "admin-001",
                "security_events",
                "instruction_service",
                "payment_service",
                true);
        counter = new SecurityEventCounter(mongoClient, properties);
    }

    @Test
    void countInstructionEventsReturnsDocumentCount() {
        when(mongoClient.getDatabase("security_events")).thenReturn(database);
        when(database.getCollection("instruction_service")).thenReturn(collection);
        when(collection.countDocuments(any(Document.class))).thenReturn(7L);

        assertThat(counter.countInstructionEvents()).isEqualTo(7L);
    }

    @Test
    void countPaymentEventsReturnsNegativeOneOnFailure() {
        when(mongoClient.getDatabase("security_events")).thenThrow(new RuntimeException("down"));

        assertThat(counter.countPaymentEvents()).isEqualTo(-1L);
    }

    @Test
    void countPaymentEventsFiltersBySeverityAndOutcome() {
        when(mongoClient.getDatabase("security_events")).thenReturn(database);
        when(database.getCollection("payment_service")).thenReturn(collection);
        when(collection.countDocuments(any(Document.class))).thenReturn(3L);

        assertThat(counter.countPaymentEvents("HIGH", "FAILURE")).isEqualTo(3L);
    }
}
