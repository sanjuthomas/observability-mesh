package com.observabilitymesh.sloauthor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.observabilitymesh.sloauthor.exception.DuplicateOpenSloException;
import com.observabilitymesh.sloauthor.exception.OpenSloNotFoundException;
import com.observabilitymesh.sloauthor.exception.OpenSloValidationException;
import com.observabilitymesh.sloauthor.model.OpenSloDocument;
import com.observabilitymesh.sloauthor.repository.OpenSloDocumentRepository;
import com.observabilitymesh.sloauthor.support.OpenSloTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenSloServiceTest {

    @Mock
    private OpenSloDocumentRepository repository;

    private OpenSloService service;

    @BeforeEach
    void setUp() {
        ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory());
        service = new OpenSloService(
            repository,
            new OpenSloValidator(new YamlConversionService(yamlObjectMapper)));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("testuser", "secret"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPersistsFirstVersion() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("checkout");
        when(repository.existsByLogicalKeyAndStaleFalse("openslo/v1/Service/checkout")).thenReturn(false);
        when(repository.save(any(OpenSloDocument.class))).thenAnswer(invocation -> {
            OpenSloDocument doc = invocation.getArgument(0);
            doc.setId("generated-id");
            return doc;
        });

        var result = service.create(content);

        assertThat(result.version()).isEqualTo(1);
        assertThat(result.stale()).isFalse();
        assertThat(result.logicalKey()).isEqualTo("openslo/v1/Service/checkout");
        assertThat(result.createdBy()).isEqualTo("testuser");
    }

    @Test
    void createRejectsDuplicateActiveDocument() {
        Map<String, Object> content = OpenSloTestSupport.sloDocument("duplicate-slo");
        when(repository.existsByLogicalKeyAndStaleFalse("openslo/v1/SLO/duplicate-slo")).thenReturn(true);

        assertThatThrownBy(() -> service.create(content))
            .isInstanceOf(DuplicateOpenSloException.class);
    }

    @Test
    void updateCreatesNewVersionAndMarksPreviousStale() {
        Map<String, Object> content = OpenSloTestSupport.sloDocument("versioned-slo");
        OpenSloDocument current = document("openslo/v1/SLO/versioned-slo", 2, false, content);

        when(repository.findByLogicalKeyAndStaleFalse("openslo/v1/SLO/versioned-slo"))
            .thenReturn(Optional.of(current));
        when(repository.save(any(OpenSloDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.update("openslo/v1/SLO/versioned-slo", content);

        ArgumentCaptor<OpenSloDocument> captor = ArgumentCaptor.forClass(OpenSloDocument.class);
        verify(repository, org.mockito.Mockito.atLeast(2)).save(captor.capture());

        List<OpenSloDocument> saved = captor.getAllValues();
        assertThat(saved.stream().anyMatch(doc -> doc.getVersion() == 2 && doc.isStale())).isTrue();
        assertThat(saved.stream().anyMatch(doc -> doc.getVersion() == 3 && !doc.isStale())).isTrue();
        assertThat(result.version()).isEqualTo(3);
    }

    @Test
    void updateRejectsRenamingToExistingActiveKey() {
        Map<String, Object> content = OpenSloTestSupport.sloDocument("new-name");
        OpenSloDocument current = document("openslo/v1/SLO/old-name", 1, false, content);

        when(repository.findByLogicalKeyAndStaleFalse("openslo/v1/SLO/old-name")).thenReturn(Optional.of(current));
        when(repository.existsByLogicalKeyAndStaleFalse("openslo/v1/SLO/new-name")).thenReturn(true);

        assertThatThrownBy(() -> service.update("openslo/v1/SLO/old-name", content))
            .isInstanceOf(DuplicateOpenSloException.class);
    }

    @Test
    void listActiveMapsSummariesWithDisplayName() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("checkout");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) content.get("metadata");
        metadata.put("displayName", "Checkout");

        OpenSloDocument doc = document("openslo/v1/Service/checkout", 1, false, content);
        doc.setId("id-1");
        when(repository.findByStaleFalseOrderByKindAscNameAsc()).thenReturn(List.of(doc));

        var summaries = service.listActive();

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).displayName()).isEqualTo("Checkout");
    }

    @Test
    void getActiveByLogicalKeyThrowsWhenMissing() {
        when(repository.findByLogicalKeyAndStaleFalse("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getActiveByLogicalKey("missing"))
            .isInstanceOf(OpenSloNotFoundException.class);
    }

    @Test
    void listVersionsThrowsWhenEmpty() {
        when(repository.findByLogicalKeyOrderByVersionDesc("missing")).thenReturn(List.of());
        assertThatThrownBy(() -> service.listVersions("missing"))
            .isInstanceOf(OpenSloNotFoundException.class);
    }

    @Test
    void validateRejectsInvalidKind() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("bad");
        content.put("kind", "InvalidKind");
        assertThatThrownBy(() -> service.validateOnly(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void buildLogicalKeyRequiresCompleteDocument() {
        assertThatThrownBy(() -> service.buildLogicalKey(Map.of("apiVersion", "openslo/v1")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesAnonymousUserWhenSecurityContextMissing() {
        SecurityContextHolder.clearContext();
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("anon");
        when(repository.existsByLogicalKeyAndStaleFalse(any())).thenReturn(false);
        when(repository.save(any(OpenSloDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(content);

        assertThat(result.createdBy()).isEqualTo("anonymous");
    }

    @Test
    void usesPreferredUsernameFromJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("subject-uuid")
            .claim("preferred_username", "alice")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        Map<String, Object> content = OpenSloTestSupport.serviceDocument("jwt-author");
        when(repository.existsByLogicalKeyAndStaleFalse(any())).thenReturn(false);
        when(repository.save(any(OpenSloDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(content);

        assertThat(result.createdBy()).isEqualTo("alice");
    }

    @Test
    void fallsBackToSubjectUserIdFromJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("subject-uuid")
            .claim("subject_user_id", "svc-slo")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        Map<String, Object> content = OpenSloTestSupport.serviceDocument("jwt-author-2");
        when(repository.existsByLogicalKeyAndStaleFalse(any())).thenReturn(false);
        when(repository.save(any(OpenSloDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(content);

        assertThat(result.createdBy()).isEqualTo("svc-slo");
    }

    @Test
    void getByIdReturnsDocument() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("by-id");
        OpenSloDocument doc = document("openslo/v1/Service/by-id", 1, false, content);
        doc.setId("mongo-id");
        when(repository.findById("mongo-id")).thenReturn(Optional.of(doc));

        var result = service.getById("mongo-id");

        assertThat(result.id()).isEqualTo("mongo-id");
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById("missing"))
            .isInstanceOf(OpenSloNotFoundException.class);
    }

    @Test
    void listVersionsReturnsHistory() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("versioned");
        OpenSloDocument v2 = document("openslo/v1/Service/versioned", 2, false, content);
        OpenSloDocument v1 = document("openslo/v1/Service/versioned", 1, true, content);
        when(repository.findByLogicalKeyOrderByVersionDesc("openslo/v1/Service/versioned"))
            .thenReturn(List.of(v2, v1));

        var versions = service.listVersions("openslo/v1/Service/versioned");

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).version()).isEqualTo(2);
    }

    @Test
    void updateThrowsWhenActiveMissing() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("missing");
        when(repository.findByLogicalKeyAndStaleFalse("openslo/v1/Service/missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update("openslo/v1/Service/missing", content))
            .isInstanceOf(OpenSloNotFoundException.class);
    }

    @Test
    void updateWithRenamedLogicalKeyMarksAllOldVersionsStale() {
        Map<String, Object> oldContent = OpenSloTestSupport.serviceDocument("old-name");
        Map<String, Object> newContent = OpenSloTestSupport.serviceDocument("new-name");

        OpenSloDocument current = document("openslo/v1/Service/old-name", 1, false, oldContent);
        OpenSloDocument staleCandidate = document("openslo/v1/Service/old-name", 1, false, oldContent);

        when(repository.findByLogicalKeyAndStaleFalse("openslo/v1/Service/old-name")).thenReturn(Optional.of(current));
        when(repository.existsByLogicalKeyAndStaleFalse("openslo/v1/Service/new-name")).thenReturn(false);
        when(repository.findByLogicalKeyOrderByVersionDesc("openslo/v1/Service/old-name")).thenReturn(List.of(staleCandidate));
        when(repository.save(any(OpenSloDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.update("openslo/v1/Service/old-name", newContent);

        assertThat(result.logicalKey()).isEqualTo("openslo/v1/Service/new-name");
        assertThat(staleCandidate.isStale()).isTrue();
    }

    @Test
    void existsActiveDelegatesToRepository() {
        when(repository.existsByLogicalKeyAndStaleFalse("key")).thenReturn(true);
        assertThat(service.existsActive("key")).isTrue();
    }

    private OpenSloDocument document(String logicalKey, int version, boolean stale, Map<String, Object> content) {
        OpenSloDocument doc = new OpenSloDocument();
        doc.setLogicalKey(logicalKey);
        doc.setVersion(version);
        doc.setStale(stale);
        doc.setContent(content);
        doc.setApiVersion("openslo/v1");
        doc.setKind((String) content.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) content.get("metadata");
        doc.setName((String) metadata.get("name"));
        doc.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        doc.setCreatedBy("tester");
        return doc;
    }
}
