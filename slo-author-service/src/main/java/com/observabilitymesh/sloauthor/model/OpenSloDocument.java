package com.observabilitymesh.sloauthor.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "service-level-objectives")
@CompoundIndexes({
    @CompoundIndex(name = "logical_key_version", def = "{'logicalKey': 1, 'version': -1}"),
    @CompoundIndex(name = "logical_key_stale", def = "{'logicalKey': 1, 'stale': 1}")
})
public class OpenSloDocument {

    @Id
    private String id;

    private String logicalKey;

    private int version;

    private boolean stale;

    private String apiVersion;

    private String kind;

    private String name;

    private Map<String, Object> content;

    private String createdBy;

    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLogicalKey() {
        return logicalKey;
    }

    public void setLogicalKey(String logicalKey) {
        this.logicalKey = logicalKey;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
