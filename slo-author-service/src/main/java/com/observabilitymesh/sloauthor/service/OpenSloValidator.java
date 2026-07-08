package com.observabilitymesh.sloauthor.service;

import com.observabilitymesh.sloauthor.exception.OpenSloValidationException;
import com.sanjuthomas.openslo.OpenSloObject;
import com.sanjuthomas.openslo.validation.ValidationException;
import com.sanjuthomas.openslo.validation.ValidatorError;
import com.sanjuthomas.openslosdk.ObjectFormat;
import com.sanjuthomas.openslosdk.OpenSloSdk;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OpenSloValidator {

    private final YamlConversionService yamlConversionService;

    public OpenSloValidator(YamlConversionService yamlConversionService) {
        this.yamlConversionService = yamlConversionService;
    }

    public void validate(Map<String, Object> content) {
        if (content == null || content.isEmpty()) {
            throw new OpenSloValidationException("OpenSLO document content is required");
        }

        try {
            String yaml = yamlConversionService.toYaml(content);
            List<OpenSloObject> objects = OpenSloSdk.decode(yaml, ObjectFormat.YAML);
            if (objects.isEmpty()) {
                throw new OpenSloValidationException("OpenSLO document content is required");
            }
            if (objects.size() > 1) {
                throw new OpenSloValidationException("Exactly one OpenSLO document is required");
            }
            OpenSloSdk.validate(objects.getFirst());
        } catch (ValidationException ex) {
            throw new OpenSloValidationException(formatValidationErrors(ex));
        } catch (IllegalArgumentException ex) {
            throw new OpenSloValidationException(ex.getMessage());
        } catch (IOException ex) {
            throw new OpenSloValidationException("Invalid OpenSLO document: " + ex.getMessage());
        }
    }

    private String formatValidationErrors(ValidationException ex) {
        return ex.getErrors().stream()
            .map(ValidatorError::getMessage)
            .collect(Collectors.joining("; "));
    }
}
