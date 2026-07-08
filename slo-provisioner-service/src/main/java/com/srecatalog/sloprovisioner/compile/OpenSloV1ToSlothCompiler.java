package com.srecatalog.sloprovisioner.compile;

import com.srecatalog.sloprovisioner.model.OpenSloDocumentView;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class OpenSloV1ToSlothCompiler {

    private static final Pattern FIXED_WINDOW = Pattern.compile("\\[\\d+[smhdwy]\\]");

    private OpenSloV1ToSlothCompiler() {
    }

    public static String compile(OpenSloDocumentView slo, OpenSloDocumentView sli, Set<String> knownDatasources) {
        if (!"SLO".equals(slo.kind())) {
            throw new OpenSloCompilationException("expected SLO document, got kind=" + slo.kind());
        }
        if (!"SLI".equals(sli.kind())) {
            throw new OpenSloCompilationException("expected SLI document for indicator, got kind=" + sli.kind());
        }

        Map<String, Object> sloSpec = map(slo.content(), "spec");
        Map<String, Object> sliSpec = map(sli.content(), "spec");
        Map<String, Object> ratioMetric = map(sliSpec, "ratioMetric");

        String goodQuery = promqlFromRatioSide(map(ratioMetric, "good"), knownDatasources);
        String totalQuery = promqlFromRatioSide(map(ratioMetric, "total"), knownDatasources);

        String service = text(sloSpec, "service");
        String sloName = slo.name();
        String displayName = metadataText(slo.content(), "displayName", sloName);
        String description = text(sloSpec, "description");
        double target = objectiveTarget(sloSpec);
        int windowDays = timeWindowDays(sloSpec);

        return """
                apiVersion: openslo/v1alpha
                kind: SLO
                metadata:
                  name: %s
                  displayName: %s
                spec:
                  service: %s
                  description: %s
                  budgetingMethod: Occurrences
                  objectives:
                    - target: %s
                      ratioMetrics:
                        good:
                          source: prometheus
                          queryType: promql
                          query: %s
                        total:
                          source: prometheus
                          queryType: promql
                          query: %s
                  timeWindows:
                    - count: %d
                      unit: Day
                """.formatted(
                yamlScalar(sloName),
                yamlScalar(displayName),
                yamlScalar(service),
                yamlScalar(description),
                formatTarget(target),
                yamlScalar(normalizeWindowPlaceholder(goodQuery)),
                yamlScalar(normalizeWindowPlaceholder(totalQuery)),
                windowDays);
    }

    static String normalizeWindowPlaceholder(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        return FIXED_WINDOW.matcher(query).replaceAll("[{{.window}}]");
    }

    private static String promqlFromRatioSide(Map<String, Object> side, Set<String> knownDatasources) {
        Map<String, Object> metricSource = map(side, "metricSource");
        String ref = text(metricSource, "metricSourceRef");
        if (!ref.isBlank() && !knownDatasources.isEmpty() && !knownDatasources.contains(ref)) {
            throw new OpenSloCompilationException("unknown metricSourceRef '" + ref + "'");
        }
        Map<String, Object> spec = map(metricSource, "spec");
        String query = text(spec, "query");
        if (query.isBlank()) {
            throw new OpenSloCompilationException("missing PromQL query on SLI ratio metric");
        }
        return query.trim();
    }

    private static double objectiveTarget(Map<String, Object> sloSpec) {
        Object objectives = sloSpec.get("objectives");
        if (!(objectives instanceof Iterable<?> iterable)) {
            throw new OpenSloCompilationException("SLO spec.objectives is required");
        }
        for (Object entry : iterable) {
            if (entry instanceof Map<?, ?> objective) {
                Object target = objective.get("target");
                if (target instanceof Number number) {
                    return number.doubleValue();
                }
            }
        }
        throw new OpenSloCompilationException("SLO objectives[0].target is required");
    }

    private static int timeWindowDays(Map<String, Object> sloSpec) {
        Object windows = sloSpec.get("timeWindow");
        if (!(windows instanceof Iterable<?> iterable)) {
            return 30;
        }
        for (Object entry : iterable) {
            if (entry instanceof Map<?, ?> window) {
                String duration = String.valueOf(window.get("duration"));
                if (duration != null && duration.endsWith("d")) {
                    return Integer.parseInt(duration.substring(0, duration.length() - 1));
                }
            }
        }
        return 30;
    }

    private static String metadataText(Map<String, Object> content, String field, String fallback) {
        Map<String, Object> metadata = map(content, "metadata");
        String value = text(metadata, field);
        return value.isBlank() ? fallback : value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String field) {
        if (parent == null) {
            return Map.of();
        }
        Object value = parent.get(field);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String text(Map<String, Object> map, String field) {
        if (map == null) {
            return "";
        }
        Object value = map.get(field);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String yamlScalar(String raw) {
        if (raw == null || raw.isBlank()) {
            return "\"\"";
        }
        String escaped = raw.replace("\"", "\\\"");
        if (raw.contains("\n") || raw.contains(":")) {
            return "\"" + escaped + "\"";
        }
        return "\"" + escaped + "\"";
    }

    private static String formatTarget(double target) {
        return String.valueOf(target);
    }
}
