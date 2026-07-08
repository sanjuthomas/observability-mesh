package com.srecatalog.instruction.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srecatalog.instruction.config.InstructionProperties;
import com.srecatalog.instruction.model.CashSettlementInstruction;
import com.srecatalog.instruction.model.InstructionConstants;
import com.srecatalog.instruction.model.InstructionStatus;
import com.srecatalog.instruction.model.VersionedInstruction;
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
public class InstructionRepository {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final String collection;

    public InstructionRepository(
            MongoTemplate instructionMongoTemplate,
            ObjectMapper instructionObjectMapper,
            InstructionProperties properties) {
        this.mongoTemplate = instructionMongoTemplate;
        this.objectMapper = instructionObjectMapper;
        this.collection = properties.collection();
    }

    public VersionedInstruction insertInitial(CashSettlementInstruction instruction) {
        Instant now = Instant.now();
        Document document = toDocument(instruction, 1, now, null);
        mongoTemplate.insert(document, collection);
        return fromDocument(document);
    }

    public VersionedInstruction appendVersion(CashSettlementInstruction instruction) {
        Instant now = Instant.now();
        Query currentQuery = new Query(Criteria.where("_id")
                .regex("^" + Pattern.quote(instruction.instructionId()) + "\\|\\d+$")
                .and("out").is(InstructionConstants.CURRENT_OUT));
        Document current = mongoTemplate.findOne(currentQuery, Document.class, collection);
        if (current == null) {
            throw new InstructionNotFoundException(instruction.instructionId());
        }
        int currentVersion = current.getInteger("version_number");
        instruction.setUpdatedAt(now);
        int nextVersion = currentVersion + 1;
        String conflictMsg = "instruction " + instruction.instructionId() + " was modified concurrently "
                + "(expected version " + currentVersion + "); please retry";

        Update closeUpdate = new Update().set("out", now.toString());
        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(current.get("_id"))
                        .and("version_number").is(currentVersion)
                        .and("out").is(InstructionConstants.CURRENT_OUT)),
                closeUpdate,
                collection);
        if (result.getModifiedCount() != 1) {
            throw new ConcurrentModificationException(conflictMsg);
        }

        Document document = toDocument(instruction, nextVersion, now, null);
        try {
            mongoTemplate.insert(document, collection);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ConcurrentModificationException(conflictMsg);
        }
        return fromDocument(document);
    }

    public VersionedInstruction getCurrent(String instructionId) {
        Query query = new Query(Criteria.where("_id")
                .regex("^" + Pattern.quote(instructionId) + "\\|\\d+$")
                .and("out").is(InstructionConstants.CURRENT_OUT));
        Document document = mongoTemplate.findOne(query, Document.class, collection);
        if (document == null) {
            throw new InstructionNotFoundException(instructionId);
        }
        return fromDocument(document);
    }

    public List<VersionedInstruction> listCurrent(String owningLob, String status, int limit) {
        Criteria criteria = Criteria.where("out").is(InstructionConstants.CURRENT_OUT);
        if (owningLob != null && !owningLob.isBlank()) {
            criteria = criteria.and("owning_lob").is(owningLob);
        }
        if (status != null && !status.isBlank()) {
            criteria = criteria.and("status").is(status);
        }
        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "in")).limit(limit);
        List<VersionedInstruction> records = new ArrayList<>();
        for (Document doc : mongoTemplate.find(query, Document.class, collection)) {
            records.add(fromDocument(doc));
        }
        return records;
    }

    public List<VersionedInstruction> listVersions(String instructionId) {
        Query query = new Query(Criteria.where("_id")
                .regex("^" + Pattern.quote(instructionId) + "\\|\\d+$"))
                .with(Sort.by(Sort.Direction.ASC, "version_number"));
        List<VersionedInstruction> records = new ArrayList<>();
        for (Document doc : mongoTemplate.find(query, Document.class, collection)) {
            records.add(fromDocument(doc));
        }
        return records;
    }

    private Document toDocument(CashSettlementInstruction instruction, int versionNumber, Instant validIn, Instant validOut) {
        Map<String, Object> payload = objectMapper.convertValue(instruction, Map.class);
        payload.remove("instruction_id");

        Document document = new Document();
        document.put("_id", InstructionConstants.documentKey(instruction.instructionId(), versionNumber));
        document.put("version_number", versionNumber);
        document.put("in", validIn.toString());
        document.put("out", validOut == null ? InstructionConstants.CURRENT_OUT : validOut.toString());
        document.put("status", instruction.status().name());
        document.put("owning_lob", instruction.owningLob());
        document.put("payload", payload);
        return document;
    }

    @SuppressWarnings("unchecked")
    private VersionedInstruction fromDocument(Document document) {
        String documentKey = document.getString("_id");
        String instructionId = InstructionConstants.instructionIdFromDocumentKey(documentKey);
        Map<String, Object> payload = new LinkedHashMap<>((Map<String, Object>) document.get("payload"));
        payload.put("instruction_id", instructionId);
        CashSettlementInstruction instruction = objectMapper.convertValue(payload, CashSettlementInstruction.class);

        String outRaw = document.getString("out");
        Instant validOut = InstructionConstants.CURRENT_OUT.equals(outRaw) ? null : Instant.parse(outRaw);
        Instant validIn = Instant.parse(document.getString("in"));

        return new VersionedInstruction(instruction, document.getInteger("version_number"), validIn, validOut);
    }

    public boolean isCancelled(VersionedInstruction record) {
        return record.instruction().status() == InstructionStatus.CANCELLED;
    }
}
