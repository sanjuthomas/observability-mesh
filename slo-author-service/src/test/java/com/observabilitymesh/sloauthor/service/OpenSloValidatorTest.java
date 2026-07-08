package com.observabilitymesh.sloauthor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.observabilitymesh.sloauthor.exception.OpenSloValidationException;
import com.observabilitymesh.sloauthor.support.OpenSloTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenSloValidatorTest {

    private OpenSloValidator validator;

    @BeforeEach
    void setUp() {
        ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory());
        validator = new OpenSloValidator(new YamlConversionService(yamlObjectMapper));
    }

    @Test
    void acceptsValidDocumentsForAllKinds() {
        assertThatCode(() -> validator.validate(OpenSloTestSupport.serviceDocument("svc"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OpenSloTestSupport.sloDocument("slo"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OpenSloTestSupport.sliDocument("sli"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OpenSloTestSupport.dataSourceDocument("ds"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OpenSloTestSupport.alertPolicyDocument("ap"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OpenSloTestSupport.alertConditionDocument("ac"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OpenSloTestSupport.alertNotificationTargetDocument("ant"))).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullOrEmptyContent() {
        assertThatThrownBy(() -> validator.validate(null))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("required");
        assertThatThrownBy(() -> validator.validate(Map.of()))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsInvalidApiVersion() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("svc");
        content.put("apiVersion", "openslo/v2");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("openslo/v2");
    }

    @Test
    void rejectsInvalidKindAndMetadata() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("svc");
        content.put("kind", "Unknown");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);

        content.put("kind", "Service");
        content.remove("metadata");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);

        content.put("metadata", Map.of("name", "INVALID_NAME"));
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsSloWithoutIndicator() {
        Map<String, Object> withoutIndicator = OpenSloTestSupport.sloDocument("slo");
        @SuppressWarnings("unchecked")
        Map<String, Object> specWithoutIndicator = (Map<String, Object>) withoutIndicator.get("spec");
        specWithoutIndicator.remove("indicator");
        assertThatThrownBy(() -> validator.validate(withoutIndicator))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("indicator");
    }

    @Test
    void rejectsInvalidBudgetingMethodAndTimeWindow() {
        Map<String, Object> invalidBudget = OpenSloTestSupport.sloDocument("slo");
        @SuppressWarnings("unchecked")
        Map<String, Object> budgetSpec = (Map<String, Object>) invalidBudget.get("spec");
        budgetSpec.put("budgetingMethod", "Invalid");
        assertThatThrownBy(() -> validator.validate(invalidBudget))
            .isInstanceOf(OpenSloValidationException.class);

        Map<String, Object> invalidWindow = OpenSloTestSupport.sloDocument("slo2");
        @SuppressWarnings("unchecked")
        Map<String, Object> windowSpec = (Map<String, Object>) invalidWindow.get("spec");
        windowSpec.put("timeWindow", List.of(Map.of("duration", "bad", "isRolling", true)));
        assertThatThrownBy(() -> validator.validate(invalidWindow))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("duration");
    }

    @Test
    void rejectsSliWithoutExactlyOneMetricType() {
        Map<String, Object> content = OpenSloTestSupport.sliDocument("sli");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.put("thresholdMetric", Map.of("metricSource", Map.of("type", "Prometheus", "spec", Map.of())));
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsDataSourceWithoutConnectionDetails() {
        Map<String, Object> content = OpenSloTestSupport.dataSourceDocument("ds");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.remove("connectionDetails");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsInvalidTimeWindowShape() {
        Map<String, Object> content = OpenSloTestSupport.sloDocument("slo");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.put("timeWindow", List.of(Map.of("duration", "1d"), Map.of("duration", "2d")));
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("timeWindow");

        spec.put("timeWindow", List.of("not-a-map"));
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsLongDescription() {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("svc");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.put("description", "x".repeat(1051));
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("1050");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1d", "4w", "30d", "1h", "1M", "1Q", "1Y"})
    void acceptsValidDurationShorthand(String duration) {
        Map<String, Object> content = OpenSloTestSupport.sloDocument("slo");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.put("timeWindow", List.of(Map.of("duration", duration, "isRolling", true)));
        assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
    }

    @Test
    void acceptsSloWithIndicatorRef() {
        Map<String, Object> content = OpenSloTestSupport.sloDocument("slo");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.remove("indicator");
        spec.put("indicatorRef", "external-sli");
        assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAlertConditionWithoutConditionBody() {
        Map<String, Object> content = OpenSloTestSupport.alertConditionDocument("ac");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.remove("condition");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsAlertNotificationTargetWithoutTarget() {
        Map<String, Object> content = OpenSloTestSupport.alertNotificationTargetDocument("ant");
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) content.get("spec");
        spec.remove("target");
        assertThatThrownBy(() -> validator.validate(content))
            .isInstanceOf(OpenSloValidationException.class);
    }

    @Test
    void rejectsMultipleDocuments() {
        YamlConversionService yamlService = mock(YamlConversionService.class);
        when(yamlService.toYaml(any())).thenReturn("""
            apiVersion: openslo/v1
            kind: Service
            metadata:
              name: a
            spec:
              description: one
            ---
            apiVersion: openslo/v1
            kind: Service
            metadata:
              name: b
            spec:
              description: two
            """);
        OpenSloValidator multiDocumentValidator = new OpenSloValidator(yamlService);

        assertThatThrownBy(() -> multiDocumentValidator.validate(Map.of("ignored", "map")))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("Exactly one OpenSLO document is required");
    }

    @Test
    void rejectsDecodedEmptyDocument() {
        YamlConversionService yamlService = mock(YamlConversionService.class);
        when(yamlService.toYaml(any())).thenReturn("");
        OpenSloValidator emptyDocumentValidator = new OpenSloValidator(yamlService);

        assertThatThrownBy(() -> emptyDocumentValidator.validate(Map.of("foo", "bar")))
            .isInstanceOf(OpenSloValidationException.class)
            .hasMessageContaining("OpenSLO document content is required");
    }
}
