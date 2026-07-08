package com.observabilitymesh.sloauthor.service;

import com.observabilitymesh.sloauthor.dto.OpenSloDetailDto;
import com.observabilitymesh.sloauthor.dto.OpenSloSummaryDto;
import com.observabilitymesh.sloauthor.exception.DuplicateOpenSloException;
import com.observabilitymesh.sloauthor.exception.OpenSloNotFoundException;
import com.observabilitymesh.sloauthor.model.OpenSloDocument;
import com.observabilitymesh.sloauthor.repository.OpenSloDocumentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class OpenSloService {

    private final OpenSloDocumentRepository repository;
    private final OpenSloValidator validator;

    public OpenSloService(OpenSloDocumentRepository repository, OpenSloValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public List<OpenSloSummaryDto> listActive() {
        return repository.findByStaleFalseOrderByKindAscNameAsc().stream()
            .map(this::toSummary)
            .toList();
    }

    public OpenSloDetailDto getActiveByLogicalKey(String logicalKey) {
        OpenSloDocument doc = repository.findByLogicalKeyAndStaleFalse(logicalKey)
            .orElseThrow(() -> new OpenSloNotFoundException("No active document found for key: " + logicalKey));
        return toDetail(doc);
    }

    public OpenSloDetailDto getById(String id) {
        OpenSloDocument doc = repository.findById(id)
            .orElseThrow(() -> new OpenSloNotFoundException("Document not found: " + id));
        return toDetail(doc);
    }

    public List<OpenSloSummaryDto> listVersions(String logicalKey) {
        List<OpenSloDocument> versions = repository.findByLogicalKeyOrderByVersionDesc(logicalKey);
        if (versions.isEmpty()) {
            throw new OpenSloNotFoundException("No versions found for key: " + logicalKey);
        }
        return versions.stream().map(this::toSummary).toList();
    }

    public OpenSloDetailDto create(Map<String, Object> content) {
        validator.validate(content);

        String logicalKey = buildLogicalKey(content);
        if (repository.existsByLogicalKeyAndStaleFalse(logicalKey)) {
            throw new DuplicateOpenSloException(logicalKey);
        }

        OpenSloDocument document = buildDocument(content, logicalKey, 1, false);
        return toDetail(repository.save(document));
    }

    public OpenSloDetailDto update(String logicalKey, Map<String, Object> content) {
        validator.validate(content);

        String newLogicalKey = buildLogicalKey(content);
        OpenSloDocument current = repository.findByLogicalKeyAndStaleFalse(logicalKey)
            .orElseThrow(() -> new OpenSloNotFoundException("No active document found for key: " + logicalKey));

        if (!logicalKey.equals(newLogicalKey) && repository.existsByLogicalKeyAndStaleFalse(newLogicalKey)) {
            throw new DuplicateOpenSloException(newLogicalKey);
        }

        current.setStale(true);
        repository.save(current);

        int nextVersion = current.getVersion() + 1;
        if (!logicalKey.equals(newLogicalKey)) {
            markAllVersionsStale(logicalKey);
        }

        OpenSloDocument newVersion = buildDocument(content, newLogicalKey, nextVersion, false);
        return toDetail(repository.save(newVersion));
    }

    public boolean existsActive(String logicalKey) {
        return repository.existsByLogicalKeyAndStaleFalse(logicalKey);
    }

    public void validateOnly(Map<String, Object> content) {
        validator.validate(content);
    }

    public String buildLogicalKey(Map<String, Object> content) {
        String apiVersion = (String) content.get("apiVersion");
        String kind = (String) content.get("kind");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) content.get("metadata");
        String name = metadata != null ? (String) metadata.get("name") : null;
        if (apiVersion == null || kind == null || name == null) {
            throw new IllegalArgumentException("Cannot build logical key from incomplete document");
        }
        return apiVersion + "/" + kind + "/" + name;
    }

    private void markAllVersionsStale(String logicalKey) {
        List<OpenSloDocument> versions = repository.findByLogicalKeyOrderByVersionDesc(logicalKey);
        for (OpenSloDocument version : versions) {
            if (!version.isStale()) {
                version.setStale(true);
                repository.save(version);
            }
        }
    }

    private OpenSloDocument buildDocument(Map<String, Object> content, String logicalKey, int version, boolean stale) {
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) content.get("metadata");

        OpenSloDocument document = new OpenSloDocument();
        document.setLogicalKey(logicalKey);
        document.setVersion(version);
        document.setStale(stale);
        document.setApiVersion((String) content.get("apiVersion"));
        document.setKind((String) content.get("kind"));
        document.setName((String) metadata.get("name"));
        document.setContent(content);
        document.setCreatedBy(currentUsername());
        document.setCreatedAt(Instant.now());
        return document;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "anonymous";
        }
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String preferred = jwtAuth.getToken().getClaimAsString("preferred_username");
            if (preferred == null || preferred.isBlank()) {
                preferred = jwtAuth.getToken().getClaimAsString("subject_user_id");
            }
            if (preferred != null && !preferred.isBlank()) {
                return preferred;
            }
        }
        return auth.getName();
    }

    private OpenSloSummaryDto toSummary(OpenSloDocument doc) {
        String displayName = extractDisplayName(doc.getContent());
        return new OpenSloSummaryDto(
            doc.getId(),
            doc.getLogicalKey(),
            doc.getApiVersion(),
            doc.getKind(),
            doc.getName(),
            displayName,
            doc.getVersion(),
            doc.isStale(),
            doc.getCreatedAt(),
            doc.getCreatedBy()
        );
    }

    private OpenSloDetailDto toDetail(OpenSloDocument doc) {
        return new OpenSloDetailDto(
            doc.getId(),
            doc.getLogicalKey(),
            doc.getVersion(),
            doc.isStale(),
            doc.getContent(),
            doc.getCreatedAt(),
            doc.getCreatedBy()
        );
    }

    @SuppressWarnings("unchecked")
    private String extractDisplayName(Map<String, Object> content) {
        if (content == null) {
            return null;
        }
        Object metadataObj = content.get("metadata");
        if (metadataObj instanceof Map<?, ?> metadata) {
            Object displayName = metadata.get("displayName");
            return displayName instanceof String str ? str : null;
        }
        return null;
    }
}
