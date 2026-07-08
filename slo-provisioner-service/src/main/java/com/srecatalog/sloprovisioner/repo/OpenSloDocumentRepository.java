package com.srecatalog.sloprovisioner.repo;

import com.srecatalog.sloprovisioner.config.SloProvisionerProperties;
import com.srecatalog.sloprovisioner.model.OpenSloDocumentView;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class OpenSloDocumentRepository {

    private final MongoTemplate mongoTemplate;
    private final String collection;

    public OpenSloDocumentRepository(MongoTemplate sloProvisionerMongoTemplate, SloProvisionerProperties properties) {
        this.mongoTemplate = sloProvisionerMongoTemplate;
        this.collection = properties.opensloCollection();
    }

    public List<OpenSloDocumentView> listActiveByKind(String kind) {
        Query query = new Query(Criteria.where("stale").is(false).and("kind").is(kind));
        List<OpenSloDocumentView> documents = new ArrayList<>();
        for (Document document : mongoTemplate.find(query, Document.class, collection)) {
            documents.add(toView(document));
        }
        return documents;
    }

    public Optional<OpenSloDocumentView> findActiveByKindAndName(String kind, String name) {
        Query query = new Query(Criteria.where("stale").is(false).and("kind").is(kind).and("name").is(name));
        Document document = mongoTemplate.findOne(query, Document.class, collection);
        return document == null ? Optional.empty() : Optional.of(toView(document));
    }

    public List<String> listActiveSloLogicalKeys() {
        return listActiveByKind("SLO").stream().map(OpenSloDocumentView::logicalKey).toList();
    }

    @SuppressWarnings("unchecked")
    private static OpenSloDocumentView toView(Document document) {
        return new OpenSloDocumentView(
                document.getString("_id"),
                document.getString("logicalKey"),
                document.getInteger("version"),
                Boolean.TRUE.equals(document.getBoolean("stale")),
                document.getString("kind"),
                document.getString("name"),
                (java.util.Map<String, Object>) document.get("content"));
    }
}
