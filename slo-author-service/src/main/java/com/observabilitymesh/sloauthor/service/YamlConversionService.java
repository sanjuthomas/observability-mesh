package com.observabilitymesh.sloauthor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.sloauthor.exception.OpenSloValidationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class YamlConversionService {

    private final ObjectMapper yamlObjectMapper;

    public YamlConversionService(@Qualifier("yamlObjectMapper") ObjectMapper yamlObjectMapper) {
        this.yamlObjectMapper = yamlObjectMapper;
    }

    public Map<String, Object> parseYaml(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            throw new OpenSloValidationException("YAML content is required");
        }
        try {
            return yamlObjectMapper.readValue(yaml, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new OpenSloValidationException("Invalid YAML: " + ex.getMessage());
        }
    }

    public String toYaml(Map<String, Object> content) {
        try {
            return yamlObjectMapper.writeValueAsString(content);
        } catch (Exception ex) {
            throw new OpenSloValidationException("Failed to serialize document to YAML");
        }
    }
}
