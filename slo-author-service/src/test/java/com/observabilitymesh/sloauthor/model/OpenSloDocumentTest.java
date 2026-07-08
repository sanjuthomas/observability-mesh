package com.observabilitymesh.sloauthor.model;

import com.observabilitymesh.sloauthor.support.OpenSloTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSloDocumentTest {

    @Test
    void exposesPersistedMetadata() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("checkout");
        OpenSloDocument document = new OpenSloDocument();
        document.setId("id-1");
        document.setLogicalKey("openslo/v1/Service/checkout");
        document.setVersion(3);
        document.setStale(true);
        document.setApiVersion("openslo/v1");
        document.setKind("Service");
        document.setName("checkout");
        document.setContent(content);
        document.setCreatedBy("alice");
        document.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(document.getId()).isEqualTo("id-1");
        assertThat(document.getLogicalKey()).isEqualTo("openslo/v1/Service/checkout");
        assertThat(document.getVersion()).isEqualTo(3);
        assertThat(document.isStale()).isTrue();
        assertThat(document.getApiVersion()).isEqualTo("openslo/v1");
        assertThat(document.getKind()).isEqualTo("Service");
        assertThat(document.getName()).isEqualTo("checkout");
        assertThat(document.getContent()).isEqualTo(content);
        assertThat(document.getCreatedBy()).isEqualTo("alice");
        assertThat(document.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
