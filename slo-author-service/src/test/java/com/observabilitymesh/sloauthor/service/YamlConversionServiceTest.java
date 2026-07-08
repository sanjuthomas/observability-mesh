package com.observabilitymesh.sloauthor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.observabilitymesh.sloauthor.exception.OpenSloValidationException;
import com.observabilitymesh.sloauthor.support.OpenSloTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlConversionServiceTest {

    private YamlConversionService yamlConversionService;

    @BeforeEach
    void setUp() {
        ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory());
        yamlConversionService = new YamlConversionService(yamlObjectMapper);
    }

    @Test
    void parsesAndSerializesYaml() {
        String yaml = """
            apiVersion: openslo/v1
            kind: Service
            metadata:
              name: checkout
            spec:
              description: Checkout service
            """;

        Map<String, Object> parsed = yamlConversionService.parseYaml(yaml);
        assertThat(parsed.get("kind")).isEqualTo("Service");

        String roundTrip = yamlConversionService.toYaml(parsed);
        assertThat(roundTrip).contains("Service");
    }

    @Test
    void rejectsBlankYaml() {
        assertThatThrownBy(() -> yamlConversionService.parseYaml("  "))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsInvalidYaml() {
        assertThatThrownBy(() -> yamlConversionService.parseYaml("kind: [unclosed"))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("Invalid YAML");
    }

    @Test
    void serializesDocumentFromSupport() {
        String yaml = yamlConversionService.toYaml(OpenSloTestSupport.sloDocument("latency"));
        assertThat(yaml).contains("SLO");
    }
}
