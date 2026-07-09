package com.observabilitymesh.ofac.repo;

import com.observabilitymesh.ofac.config.OfacProperties;
import com.observabilitymesh.ofac.model.OfacScanLifecycleStatus;
import com.observabilitymesh.ofac.model.OfacScanRequestConstants;
import com.observabilitymesh.ofac.model.OfacScanRequestRef;
import com.observabilitymesh.ofac.model.OfacScanRequestView;
import com.observabilitymesh.ofac.model.OfacScanResult;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OfacScanRequestRepository {

    private final MongoTemplate mongoTemplate;
    private final String collection;

    public OfacScanRequestRepository(MongoTemplate ofacMongoTemplate, OfacProperties properties) {
        this.mongoTemplate = ofacMongoTemplate;
        this.collection = properties.scanRequestsCollection();
    }

    public List<OfacScanRequestRef> listOpenCurrent() {
        Query query = new Query(Criteria.where("out").is(OfacScanRequestConstants.CURRENT_OUT)
                .and("lifecycle_status").is(OfacScanLifecycleStatus.OPEN.name()));
        List<OfacScanRequestRef> refs = new ArrayList<>();
        for (Document document : mongoTemplate.find(query, Document.class, collection)) {
            refs.add(toRef(document));
        }
        return refs;
    }

    public List<OfacScanRequestView> listCurrent(
            String owningLob, String lifecycleStatus, String result, int limit) {
        Criteria criteria = Criteria.where("out").is(OfacScanRequestConstants.CURRENT_OUT);
        if (owningLob != null && !owningLob.isBlank()) {
            criteria = criteria.and("owning_lob").is(owningLob);
        }
        if (lifecycleStatus != null && !lifecycleStatus.isBlank()) {
            criteria = criteria.and("lifecycle_status").is(lifecycleStatus);
        }
        if (result != null && !result.isBlank()) {
            criteria = criteria.and("result").is(result);
        }
        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "requested_at"))
                .limit(Math.min(limit, 500));
        return mongoTemplate.find(query, Document.class, collection).stream()
                .map(OfacScanRequestView::fromDocument)
                .toList();
    }

    public OfacScanRequestView getCurrent(String paymentId, int paymentVersion) {
        Document current = findCurrentDocument(paymentId, paymentVersion);
        if (current == null) {
            throw new OfacScanRequestNotFoundException(paymentId, paymentVersion);
        }
        return OfacScanRequestView.fromDocument(current);
    }

    @Transactional("ofacTransactionManager")
    public OfacScanRequestRef transition(
            String paymentId,
            int paymentVersion,
            int expectedVersionNumber,
            OfacScanLifecycleStatus newLifecycleStatus,
            OfacScanResult result) {
        Document current = findCurrentDocument(paymentId, paymentVersion);
        if (current == null) {
            throw new IllegalStateException(
                    "OFAC scan request not found for payment " + paymentId + " version " + paymentVersion);
        }
        int currentVersion = current.getInteger("version_number");
        if (currentVersion != expectedVersionNumber) {
            throw new ConcurrentModificationException(
                    "OFAC scan request " + paymentId + "|" + paymentVersion
                            + " was modified concurrently (expected version " + expectedVersionNumber
                            + ", found " + currentVersion + ")");
        }

        Instant now = Instant.now();
        int nextVersion = currentVersion + 1;
        String conflictMsg = "OFAC scan request " + paymentId + "|" + paymentVersion
                + " was modified concurrently (expected version " + currentVersion + ")";

        Update closeUpdate = new Update().set("out", now.toString());
        var updateResult = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(current.get("_id"))
                        .and("version_number").is(currentVersion)
                        .and("out").is(OfacScanRequestConstants.CURRENT_OUT)),
                closeUpdate,
                collection);
        if (updateResult.getModifiedCount() != 1) {
            throw new ConcurrentModificationException(conflictMsg);
        }

        Document next = copyForNextVersion(current, nextVersion, now, newLifecycleStatus, result);
        try {
            mongoTemplate.insert(next, collection);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ConcurrentModificationException(conflictMsg);
        }
        return toRef(next);
    }

    private Document findCurrentDocument(String paymentId, int paymentVersion) {
        Query query = new Query(Criteria.where("payment_id").is(paymentId)
                .and("payment_version").is(paymentVersion)
                .and("out").is(OfacScanRequestConstants.CURRENT_OUT));
        return mongoTemplate.findOne(query, Document.class, collection);
    }

    private static Document copyForNextVersion(
            Document current,
            int nextVersion,
            Instant validIn,
            OfacScanLifecycleStatus lifecycleStatus,
            OfacScanResult result) {
        Document next = new Document(current);
        next.remove("_id");
        next.put("_id", OfacScanRequestConstants.documentKey(
                current.getString("payment_id"),
                current.getInteger("payment_version"),
                nextVersion));
        next.put("version_number", nextVersion);
        next.put("in", validIn.toString());
        next.put("out", OfacScanRequestConstants.CURRENT_OUT);
        next.put("lifecycle_status", lifecycleStatus.name());
        if (result == null) {
            next.remove("result");
        } else {
            next.put("result", result.name());
        }
        return next;
    }

    private static OfacScanRequestRef toRef(Document document) {
        String requestedAtValue = document.getString("requested_at");
        Instant requestedAt = requestedAtValue == null ? null : Instant.parse(requestedAtValue);
        return new OfacScanRequestRef(
                document.getString("payment_id"),
                document.getInteger("payment_version"),
                document.getInteger("version_number"),
                requestedAt);
    }
}
