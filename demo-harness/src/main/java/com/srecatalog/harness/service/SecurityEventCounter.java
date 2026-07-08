package com.srecatalog.harness.service;

import com.mongodb.client.MongoClient;
import com.srecatalog.harness.config.HarnessProperties;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class SecurityEventCounter {

    private final MongoClient mongoClient;
    private final HarnessProperties properties;

    public SecurityEventCounter(MongoClient mongoClient, HarnessProperties properties) {
        this.mongoClient = mongoClient;
        this.properties = properties;
    }

    public long countInstructionEvents() {
        return countCollection(properties.securityEventsCollection(), null, null);
    }

    public long countPaymentEvents() {
        return countCollection(properties.paymentSecurityEventsCollection(), null, null);
    }

    public long countPaymentEvents(String severity, String outcome) {
        return countCollection(properties.paymentSecurityEventsCollection(), severity, outcome);
    }

    private long countCollection(String collection, String severity, String outcome) {
        try {
            Document query = new Document();
            if (severity != null) {
                query.append("severity", severity);
            }
            if (outcome != null) {
                query.append("event.outcome", outcome);
            }
            return mongoClient.getDatabase(properties.securityEventsDatabase())
                    .getCollection(collection)
                    .countDocuments(query);
        } catch (Exception ex) {
            return -1;
        }
    }
}
