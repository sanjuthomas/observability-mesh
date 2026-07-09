package com.observabilitymesh.payment.ofac;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;

@Repository
public class OfacScanRequestRepository {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final String collection;

    public OfacScanRequestRepository(
            MongoTemplate ofacMongoTemplate,
            ObjectMapper paymentObjectMapper,
            com.observabilitymesh.payment.config.PaymentProperties properties) {
        this.mongoTemplate = ofacMongoTemplate;
        this.objectMapper = paymentObjectMapper;
        this.collection = properties.scanRequestsCollection();
    }

    public void insert(OfacScanRequest request) {
        Document document = objectMapper.convertValue(request, Document.class);
        document.put("_id", OfacScanRequestConstants.documentKey(
                request.paymentId(), request.paymentVersion(), request.versionNumber()));
        document.put("requested_at", request.requestedAt().toString());
        document.put("in", request.in().toString());
        document.put("out", request.out());
        mongoTemplate.insert(document, collection);
    }
}
