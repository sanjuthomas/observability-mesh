package com.observabilitymesh.sloprovisioner.compile;

import com.observabilitymesh.sloprovisioner.model.OpenSloDocumentView;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenSloV1ToSlothCompilerTest {

    @Test
    void compilesSloAndSliIntoSlothOpenSlo() {
        OpenSloDocumentView slo = sloDocument();
        OpenSloDocumentView sli = sliDocument();

        String yaml = OpenSloV1ToSlothCompiler.compile(slo, sli, Set.of("payment-prometheus"));

        assertThat(yaml).contains("apiVersion: openslo/v1alpha");
        assertThat(yaml).contains("name: \"sanction-scan-latency-30d\"");
        assertThat(yaml).contains("service: \"payment-platform\"");
        assertThat(yaml).contains("target: 0.999");
        assertThat(yaml).contains("sanction_scan_completed_total");
        assertThat(yaml).contains("[{{.window}}]");
        assertThat(yaml).contains("count: 30");
    }

    @Test
    void rejectsUnknownDatasourceRef() {
        assertThatThrownBy(() -> OpenSloV1ToSlothCompiler.compile(sloDocument(), sliDocument(), Set.of("other")))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("payment-prometheus");
    }

    @Test
    void rejectsWrongSloKind() {
        OpenSloDocumentView slo = new OpenSloDocumentView(
                "1", "key", 1, false, "SLI", "demo", Map.of());
        assertThatThrownBy(() -> OpenSloV1ToSlothCompiler.compile(slo, sliDocument(), Set.of()))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("expected SLO");
    }

    @Test
    void rejectsWrongSliKind() {
        assertThatThrownBy(() -> OpenSloV1ToSlothCompiler.compile(sloDocument(), sloDocument(), Set.of()))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("expected SLI");
    }

    @Test
    void rejectsMissingObjectives() {
        OpenSloDocumentView slo = new OpenSloDocumentView(
                "1", "key", 1, false, "SLO", "demo",
                Map.of("spec", Map.of("service", "svc", "indicatorRef", "x")));
        assertThatThrownBy(() -> OpenSloV1ToSlothCompiler.compile(slo, sliDocument(), Set.of()))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("objectives");
    }

    @Test
    void rejectsMissingPromqlQuery() {
        OpenSloDocumentView sli = new OpenSloDocumentView(
                "2", "key", 1, false, "SLI", "demo",
                Map.of("spec", Map.of("ratioMetric", Map.of(
                        "good", Map.of("metricSource", Map.of("metricSourceRef", "payment-prometheus", "spec", Map.of())),
                        "total", Map.of("metricSource", Map.of("metricSourceRef", "payment-prometheus", "spec", Map.of("query", "1")))))));
        assertThatThrownBy(() -> OpenSloV1ToSlothCompiler.compile(sloDocument(), sli, Set.of("payment-prometheus")))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("PromQL");
    }

    @Test
    void usesDefaultTimeWindowWhenMissing() {
        OpenSloDocumentView slo = new OpenSloDocumentView(
                "1", "key", 1, false, "SLO", "sanction-scan-latency-30d",
                Map.of(
                        "metadata", Map.of("displayName", ""),
                        "spec", Map.of(
                                "service", "payment-platform",
                                "description", "desc",
                                "indicatorRef", "sanction-scan-under-one-minute",
                                "objectives", List.of(Map.of("target", 0.999)))));
        String yaml = OpenSloV1ToSlothCompiler.compile(slo, sliDocument(), Set.of());
        assertThat(yaml).contains("count: 30");
        assertThat(yaml).contains("displayName: \"sanction-scan-latency-30d\"");
    }

    @Test
    void rejectsMissingObjectiveTarget() {
        OpenSloDocumentView slo = new OpenSloDocumentView(
                "1", "key", 1, false, "SLO", "demo",
                Map.of("spec", Map.of(
                        "service", "svc",
                        "objectives", List.of(Map.of("label", "no-target")))));
        assertThatThrownBy(() -> OpenSloV1ToSlothCompiler.compile(slo, sliDocument(), Set.of()))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("target");
    }

    @Test
    void usesConfiguredRollingWindowDays() {
        OpenSloDocumentView slo = new OpenSloDocumentView(
                "1", "key", 1, false, "SLO", "demo",
                Map.of("spec", Map.of(
                        "service", "svc",
                        "timeWindow", List.of(Map.of("duration", "7d")),
                        "objectives", List.of(Map.of("target", 0.99)))));
        String yaml = OpenSloV1ToSlothCompiler.compile(slo, sliDocument(), Set.of());
        assertThat(yaml).contains("count: 7");
    }

    @Test
    void defaultsToThirtyDayWindowForNonDayDurations() {
        OpenSloDocumentView slo = new OpenSloDocumentView(
                "1", "key", 1, false, "SLO", "demo",
                Map.of("spec", Map.of(
                        "service", "svc",
                        "timeWindow", List.of(Map.of("duration", "30h")),
                        "objectives", List.of(Map.of("target", 0.99)))));
        String yaml = OpenSloV1ToSlothCompiler.compile(slo, sliDocument(), Set.of());
        assertThat(yaml).contains("count: 30");
    }

    @Test
    void allowsUnknownDatasourceWhenAllowlistEmpty() {
        String yaml = OpenSloV1ToSlothCompiler.compile(sloDocument(), sliDocument(), Set.of());
        assertThat(yaml).contains("sanction_scan_completed_total");
    }

    @Test
    void normalizesFixedWindowToSlothPlaceholder() {
        assertThat(OpenSloV1ToSlothCompiler.normalizeWindowPlaceholder("sum(rate(x[5m]))"))
                .isEqualTo("sum(rate(x[{{.window}}]))");
        assertThat(OpenSloV1ToSlothCompiler.normalizeWindowPlaceholder(null)).isNull();
        assertThat(OpenSloV1ToSlothCompiler.normalizeWindowPlaceholder("  ")).isEqualTo("  ");
    }

    private static OpenSloDocumentView sloDocument() {
        return new OpenSloDocumentView(
                "1",
                "openslo/v1/SLO/sanction-scan-latency-30d",
                1,
                false,
                "SLO",
                "sanction-scan-latency-30d",
                Map.of(
                        "metadata", Map.of(
                                "name", "sanction-scan-latency-30d",
                                "displayName", "Sanction scan latency (30-day rolling)"),
                        "spec", Map.of(
                                "service", "payment-platform",
                                "description", "99.9% under one minute",
                                "indicatorRef", "sanction-scan-under-one-minute",
                                "timeWindow", java.util.List.of(Map.of("duration", "30d", "isRolling", true)),
                                "budgetingMethod", "Occurrences",
                                "objectives", java.util.List.of(Map.of("target", 0.999)))));
    }

    private static OpenSloDocumentView sliDocument() {
        return new OpenSloDocumentView(
                "2",
                "openslo/v1/SLI/sanction-scan-under-one-minute",
                1,
                false,
                "SLI",
                "sanction-scan-under-one-minute",
                Map.of(
                        "spec", Map.of(
                                "ratioMetric", Map.of(
                                        "good", Map.of(
                                                "metricSource", Map.of(
                                                        "metricSourceRef", "payment-prometheus",
                                                        "spec", Map.of(
                                                                "query",
                                                                "sum(increase(sanction_scan_completed_total{duration_le=\"60s\",status=\"success\"}[5m]))"))),
                                        "total", Map.of(
                                                "metricSource", Map.of(
                                                        "metricSourceRef", "payment-prometheus",
                                                        "spec", Map.of(
                                                                "query",
                                                                "sum(increase(sanction_scan_completed_total{status=\"success\"}[5m]))")))))));
    }
}
