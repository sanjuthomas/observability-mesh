package com.observabilitymesh.sloprovisioner.compile;

import com.observabilitymesh.sloprovisioner.model.OpenSloDocumentView;

import java.util.Map;
import java.util.Set;

/**
 * Compiles OpenSLO AlertPolicy + AlertCondition + thresholdMetric SLI documents into
 * Prometheus alert rule groups. Metric-threshold alerts are indicated by annotation
 * {@value #METRIC_THRESHOLD_ANNOTATION} on the AlertCondition metadata.
 */
public final class OpenSloMetricAlertCompiler {

    public static final String METRIC_THRESHOLD_ANNOTATION = "observability-mesh.alert-type";
    public static final String METRIC_THRESHOLD_VALUE = "metric-threshold";
    public static final String SLI_REF_ANNOTATION = "observability-mesh.sli-ref";

    private OpenSloMetricAlertCompiler() {
    }

    public static String metadataAnnotation(Map<String, Object> content, String key) {
        return OpenSloContentMaps.metadataAnnotation(content, key);
    }

    public static String compile(
            OpenSloDocumentView alertPolicy,
            OpenSloDocumentView alertCondition,
            OpenSloDocumentView sli,
            Set<String> knownDatasources) {
        if (!"AlertPolicy".equals(alertPolicy.kind())) {
            throw new OpenSloCompilationException("expected AlertPolicy document, got kind=" + alertPolicy.kind());
        }
        if (!"AlertCondition".equals(alertCondition.kind())) {
            throw new OpenSloCompilationException("expected AlertCondition document, got kind=" + alertCondition.kind());
        }
        if (!"SLI".equals(sli.kind())) {
            throw new OpenSloCompilationException("expected SLI document for metric query, got kind=" + sli.kind());
        }
        if (!METRIC_THRESHOLD_VALUE.equals(
                OpenSloContentMaps.metadataAnnotation(alertCondition.content(), METRIC_THRESHOLD_ANNOTATION))) {
            throw new OpenSloCompilationException(
                    "AlertCondition must set metadata.annotations."
                            + METRIC_THRESHOLD_ANNOTATION
                            + "="
                            + METRIC_THRESHOLD_VALUE);
        }

        String metricQuery = promqlFromThresholdMetric(map(sli.content(), "spec"), knownDatasources);
        Map<String, Object> condition = map(map(alertCondition.content(), "spec"), "condition");
        String operator = mapOperator(text(condition, "op"));
        double threshold = number(condition, "threshold");
        String lookbackWindow = text(condition, "lookbackWindow");
        String alertAfter = text(condition, "alertAfter");
        if (alertAfter.isBlank()) {
            alertAfter = "0m";
        }

        String severity = text(map(alertCondition.content(), "spec"), "severity");
        if (severity.isBlank()) {
            severity = "page";
        }

        String alertName = toAlertName(alertPolicy.name());
        String groupName = "openslo-alert-" + safeName(alertPolicy.name());
        String summary = text(map(alertPolicy.content(), "spec"), "description");
        if (summary.isBlank()) {
            summary = "OpenSLO alert policy " + alertPolicy.name() + " is firing";
        }

        String expr = "%s %s %s".formatted(metricQuery, operator, formatThreshold(threshold));

        return """
                groups:
                  - name: %s
                    rules:
                      - alert: %s
                        expr: |
                          %s
                        for: %s
                        labels:
                          severity: %s
                          openslo_alert_policy: %s
                          openslo_alert_condition: %s
                        annotations:
                          summary: %s
                          description: Metric threshold alert compiled from OpenSLO AlertPolicy %s and SLI %s (lookback %s).
                """.formatted(
                yamlScalar(groupName),
                yamlScalar(alertName),
                expr,
                yamlScalar(alertAfter),
                yamlScalar(severity),
                yamlScalar(alertPolicy.name()),
                yamlScalar(alertCondition.name()),
                yamlScalar(summary),
                yamlScalar(alertPolicy.name()),
                yamlScalar(sli.name()),
                yamlScalar(lookbackWindow.isBlank() ? "inline" : lookbackWindow));
    }

    private static String promqlFromThresholdMetric(Map<String, Object> sliSpec, Set<String> knownDatasources) {
        Map<String, Object> thresholdMetric = map(sliSpec, "thresholdMetric");
        Map<String, Object> metricSource = map(thresholdMetric, "metricSource");
        String ref = text(metricSource, "metricSourceRef");
        if (!ref.isBlank() && !knownDatasources.isEmpty() && !knownDatasources.contains(ref)) {
            throw new OpenSloCompilationException("unknown metricSourceRef '" + ref + "'");
        }
        String query = text(map(metricSource, "spec"), "query");
        if (query.isBlank()) {
            throw new OpenSloCompilationException("missing PromQL query on SLI thresholdMetric");
        }
        return query.trim();
    }

    private static String mapOperator(String op) {
        return switch (op) {
            case "gt" -> ">";
            case "gte" -> ">=";
            case "lt" -> "<";
            case "lte" -> "<=";
            default -> throw new OpenSloCompilationException("unsupported alert condition op: " + op);
        };
    }

    private static double number(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new OpenSloCompilationException("missing numeric field: " + field);
    }

    private static String toAlertName(String policyName) {
        String[] parts = policyName.split("[-_]");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? "OpenSloMetricAlert" : builder.toString();
    }

    private static String safeName(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private static String formatThreshold(double threshold) {
        if (threshold == Math.rint(threshold)) {
            return String.valueOf((long) threshold);
        }
        return String.valueOf(threshold);
    }

    private static String yamlScalar(String raw) {
        if (raw == null || raw.isBlank()) {
            return "\"\"";
        }
        String escaped = raw.replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String field) {
        return OpenSloContentMaps.map(parent, field);
    }

    private static String text(Map<String, Object> map, String field) {
        return OpenSloContentMaps.text(map, field);
    }
}
