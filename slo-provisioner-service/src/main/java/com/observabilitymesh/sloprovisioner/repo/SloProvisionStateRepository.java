package com.observabilitymesh.sloprovisioner.repo;

import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import com.observabilitymesh.sloprovisioner.model.ProvisionStatus;
import com.observabilitymesh.sloprovisioner.model.SloProvisionState;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SloProvisionStateRepository {

    private final MongoTemplate mongoTemplate;
    private final String collection;

    public SloProvisionStateRepository(MongoTemplate sloProvisionerMongoTemplate, SloProvisionerProperties properties) {
        this.mongoTemplate = sloProvisionerMongoTemplate;
        this.collection = properties.provisionStateCollection();
    }

    public SloProvisionState findByLogicalKey(String logicalKey) {
        Document document = mongoTemplate.findById(logicalKey, Document.class, collection);
        return document == null ? null : toState(document);
    }

    public List<SloProvisionState> listByStatus(ProvisionStatus status) {
        Query query = new Query(Criteria.where("status").is(status.name()));
        List<SloProvisionState> states = new ArrayList<>();
        for (Document document : mongoTemplate.find(query, Document.class, collection)) {
            states.add(toState(document));
        }
        return states;
    }

    public void upsert(SloProvisionState state) {
        Document document = new Document("_id", state.logicalKey())
                .append("logicalKey", state.logicalKey())
                .append("opensloVersion", state.opensloVersion())
                .append("status", state.status().name())
                .append("rulesFileName", state.rulesFileName())
                .append("contentHash", state.contentHash())
                .append("lastSyncedAt", state.lastSyncedAt().toString())
                .append("lastError", state.lastError());
        mongoTemplate.save(document, collection);
    }

    private static SloProvisionState toState(Document document) {
        return new SloProvisionState(
                document.getString("logicalKey"),
                document.getInteger("opensloVersion"),
                ProvisionStatus.valueOf(document.getString("status")),
                document.getString("rulesFileName"),
                document.getString("contentHash"),
                Instant.parse(document.getString("lastSyncedAt")),
                document.getString("lastError"));
    }
}
