package com.observabilitymesh.sloprovisioner.compile;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSloContentMapsTest {

    @Test
    void handlesMissingParentFieldsAndAnnotations() {
        assertThat(OpenSloContentMaps.map(null, "spec")).isEmpty();
        assertThat(OpenSloContentMaps.text(null, "name")).isEmpty();
        assertThat(OpenSloContentMaps.metadataAnnotation(Map.of(), "missing")).isEmpty();
        assertThat(OpenSloContentMaps.metadataAnnotation(
                Map.of("metadata", Map.of("annotations", Map.of("team", "payments"))),
                "team")).isEqualTo("payments");
    }
}
