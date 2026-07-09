package com.observabilitymesh.sloprovisioner.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSloContentReaderTest {

    @Test
    void readsSloAndSliFields() {
        Map<String, Object> sloContent = Map.of(
                "metadata", Map.of("displayName", "Latency SLO"),
                "spec", Map.of(
                        "service", "payment-platform",
                        "description", "99.9% under one minute",
                        "indicatorRef", "latency-sli",
                        "timeWindow", List.of(Map.of("duration", "30d")),
                        "objectives", List.of(Map.of("target", 0.999))));

        Map<String, Object> sliContent = Map.of(
                "spec", Map.of(
                        "ratioMetric", Map.of(
                                "good", Map.of(
                                        "metricSource", Map.of(
                                                "metricSourceRef", "payment-prometheus",
                                                "spec", Map.of("query", "good_query"))),
                                "total", Map.of(
                                        "metricSource", Map.of(
                                                "metricSourceRef", "payment-prometheus",
                                                "spec", Map.of("query", "total_query"))))));

        assertThat(OpenSloContentReader.displayName(sloContent, "fallback")).isEqualTo("Latency SLO");
        assertThat(OpenSloContentReader.service(sloContent)).isEqualTo("payment-platform");
        assertThat(OpenSloContentReader.indicatorRef(sloContent)).isEqualTo("latency-sli");
        assertThat(OpenSloContentReader.objectiveTarget(sloContent)).isEqualTo(0.999);
        assertThat(OpenSloContentReader.timeWindowLabel(sloContent)).isEqualTo("30d");
        assertThat(OpenSloContentReader.datasourceRef(sliContent)).isEqualTo("payment-prometheus");
        assertThat(OpenSloContentReader.goodQuery(sliContent)).isEqualTo("good_query");
        assertThat(OpenSloContentReader.totalQuery(sliContent)).isEqualTo("total_query");
    }

    @Test
    void handlesMissingNestedFields() {
        assertThat(OpenSloContentReader.displayName(Map.of(), "fallback")).isEqualTo("fallback");
        assertThat(OpenSloContentReader.service(Map.of())).isEmpty();
        assertThat(OpenSloContentReader.indicatorRef(Map.of())).isEmpty();
        assertThat(OpenSloContentReader.objectiveTarget(Map.of("spec", Map.of()))).isNull();
        assertThat(OpenSloContentReader.timeWindowLabel(Map.of("spec", Map.of()))).isEmpty();
        assertThat(OpenSloContentReader.datasourceRef(Map.of())).isEmpty();
        assertThat(OpenSloContentReader.goodQuery(Map.of())).isEmpty();
        assertThat(OpenSloContentReader.totalQuery(Map.of())).isEmpty();
    }

    @Test
    void readsObjectiveTargetFromIterable() {
        Map<String, Object> content = Map.of(
                "spec", Map.of("objectives", List.of(Map.of("label", "no-target"), Map.of("target", 0.95))));
        assertThat(OpenSloContentReader.objectiveTarget(content)).isEqualTo(0.95);
    }
}
