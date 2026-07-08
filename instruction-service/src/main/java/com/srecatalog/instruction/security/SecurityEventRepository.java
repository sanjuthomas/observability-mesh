package com.srecatalog.instruction.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srecatalog.instruction.config.InstructionProperties;
import com.srecatalog.sequenceclient.SequenceClient;
import com.srecatalog.sequenceclient.SequenceClientException;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SecurityEventRepository {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final SequenceClient sequenceClient;
    private final String collection;

    public SecurityEventRepository(
            @Qualifier("securityEventsMongoTemplate") MongoTemplate mongoTemplate,
            ObjectMapper instructionObjectMapper,
            SequenceClient sequenceClient,
            InstructionProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = instructionObjectMapper;
        this.sequenceClient = sequenceClient;
        this.collection = properties.securityEventsCollection();
    }

    public String allocateEventId(String resourceId) {
        try {
            return sequenceClient.nextSecurityEventId(resourceId);
        } catch (SequenceClientException ex) {
            throw new IllegalStateException("security event sequence allocation failed: " + ex.getMessage(), ex);
        }
    }

    public void insert(InstructionSecurityEvent event, String documentId) {
        Document document = objectMapper.convertValue(event, Document.class);
        document.put("_id", documentId);
        mongoTemplate.insert(document, collection);
    }

    public List<Map<String, Object>> listRecent(int limit) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(limit);
        List<Map<String, Object>> events = new ArrayList<>();
        for (Document doc : mongoTemplate.find(query, Document.class, collection)) {
            events.add(serialize(doc));
        }
        return events;
    }

    public Map<String, Object> findByEventId(String eventId) {
        Document doc = mongoTemplate.findById(eventId, Document.class, collection);
        return doc == null ? null : serialize(doc);
    }

    private Map<String, Object> serialize(Document document) {
        Map<String, Object> result = new LinkedHashMap<>(document);
        Object id = result.remove("_id");
        if (id != null) {
            result.put("event_id", String.valueOf(id));
        }
        return result;
    }
}
