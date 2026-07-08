package com.srecatalog.payment.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srecatalog.payment.model.Payment;
import com.srecatalog.payment.model.PaymentConstants;
import com.srecatalog.payment.model.PaymentStatus;
import com.srecatalog.payment.model.VersionedPayment;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Repository
public class PaymentRepository {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final String collection;

    public PaymentRepository(
            MongoTemplate paymentMongoTemplate,
            ObjectMapper paymentObjectMapper,
            com.srecatalog.payment.config.PaymentProperties properties) {
        this.mongoTemplate = paymentMongoTemplate;
        this.objectMapper = paymentObjectMapper;
        this.collection = properties.collection();
    }

    public VersionedPayment insertInitial(Payment payment) {
        Instant now = Instant.now();
        Document document = toDocument(payment, 1, now, null);
        mongoTemplate.insert(document, collection);
        return fromDocument(document);
    }

    public VersionedPayment appendVersion(Payment payment) {
        Instant now = Instant.now();
        Query currentQuery = new Query(Criteria.where("_id")
                .regex("^" + Pattern.quote(payment.paymentId()) + "\\|\\d+$")
                .and("out").is(PaymentConstants.CURRENT_OUT));
        Document current = mongoTemplate.findOne(currentQuery, Document.class, collection);
        if (current == null) {
            throw new PaymentNotFoundException(payment.paymentId());
        }
        int currentVersion = current.getInteger("version_number");
        payment.setUpdatedAt(now);
        int nextVersion = currentVersion + 1;
        String conflictMsg = "payment " + payment.paymentId() + " was modified concurrently "
                + "(expected version " + currentVersion + "); please retry";

        Update closeUpdate = new Update().set("out", now.toString());
        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(current.get("_id"))
                        .and("version_number").is(currentVersion)
                        .and("out").is(PaymentConstants.CURRENT_OUT)),
                closeUpdate,
                collection);
        if (result.getModifiedCount() != 1) {
            throw new ConcurrentModificationException(conflictMsg);
        }

        Document document = toDocument(payment, nextVersion, now, null);
        try {
            mongoTemplate.insert(document, collection);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ConcurrentModificationException(conflictMsg);
        }
        return fromDocument(document);
    }

    public VersionedPayment getCurrent(String paymentId) {
        Query query = new Query(Criteria.where("_id")
                .regex("^" + Pattern.quote(paymentId) + "\\|\\d+$")
                .and("out").is(PaymentConstants.CURRENT_OUT));
        Document document = mongoTemplate.findOne(query, Document.class, collection);
        if (document == null) {
            throw new PaymentNotFoundException(paymentId);
        }
        return fromDocument(document);
    }

    public List<VersionedPayment> listCurrent(String instructionId, String status, int limit, boolean includeCancelled) {
        Criteria criteria = Criteria.where("out").is(PaymentConstants.CURRENT_OUT);
        if (instructionId != null && !instructionId.isBlank()) {
            criteria = criteria.and("instruction_id").is(instructionId);
        }
        if (status != null && !status.isBlank()) {
            criteria = criteria.and("status").is(status);
        }
        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "in")).limit(limit);
        List<VersionedPayment> records = new ArrayList<>();
        for (Document doc : mongoTemplate.find(query, Document.class, collection)) {
            VersionedPayment record = fromDocument(doc);
            if (includeCancelled || record.payment().status() != PaymentStatus.CANCELLED) {
                records.add(record);
            }
        }
        return records;
    }

    private Document toDocument(Payment payment, int versionNumber, Instant validIn, Instant validOut) {
        Map<String, Object> payload = objectMapper.convertValue(payment, Map.class);
        payload.remove("payment_id");

        Document document = new Document();
        document.put("_id", PaymentConstants.documentKey(payment.paymentId(), versionNumber));
        document.put("version_number", versionNumber);
        document.put("in", validIn.toString());
        document.put("out", validOut == null ? PaymentConstants.CURRENT_OUT : validOut.toString());
        document.put("status", payment.status().name());
        document.put("owning_lob", payment.owningLob());
        document.put("instruction_id", payment.instructionId());
        document.put("payload", payload);
        return document;
    }

    @SuppressWarnings("unchecked")
    private VersionedPayment fromDocument(Document document) {
        String documentKey = document.getString("_id");
        String paymentId = PaymentConstants.paymentIdFromDocumentKey(documentKey);
        Map<String, Object> payload = new LinkedHashMap<>((Map<String, Object>) document.get("payload"));
        payload.put("payment_id", paymentId);
        Payment payment = objectMapper.convertValue(payload, Payment.class);

        String outRaw = document.getString("out");
        Instant validOut = PaymentConstants.CURRENT_OUT.equals(outRaw) ? null : Instant.parse(outRaw);
        Instant validIn = Instant.parse(document.getString("in"));

        return new VersionedPayment(payment, document.getInteger("version_number"), validIn, validOut);
    }
}
