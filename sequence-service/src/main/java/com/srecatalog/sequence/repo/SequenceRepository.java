package com.srecatalog.sequence.repo;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class SequenceRepository {

    private final MongoTemplate mongoTemplate;

    public SequenceRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public long allocateNext(String counterKey) {
        Query query = Query.query(Criteria.where("_id").is(counterKey));
        Update update = new Update().inc("seq", 1).set("updated_at", Instant.now());
        SequenceCounter counter = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                SequenceCounter.class);
        if (counter == null) {
            throw new IllegalStateException("Failed to allocate sequence for " + counterKey);
        }
        return counter.getSeq();
    }
}
