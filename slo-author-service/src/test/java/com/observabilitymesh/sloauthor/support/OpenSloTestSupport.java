package com.observabilitymesh.sloauthor.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OpenSloTestSupport {

    private OpenSloTestSupport() {
    }

    public static Map<String, Object> serviceDocument(String name) {
        return document("Service", name, Map.of("description", "Test service"));
    }

    public static Map<String, Object> sloDocument(String name) {
        Map<String, Object> indicator = Map.of(
            "metadata", Map.of("name", name + "-sli"),
            "spec", Map.of(
                "ratioMetric", Map.of(
                    "good", Map.of("metricSource", Map.of("type", "Prometheus", "spec", Map.of("query", "good"))),
                    "total", Map.of("metricSource", Map.of("type", "Prometheus", "spec", Map.of("query", "total")))
                )
            )
        );
        Map<String, Object> spec = new HashMap<>();
        spec.put("service", "payment-platform");
        spec.put("indicator", indicator);
        spec.put("budgetingMethod", "Occurrences");
        spec.put("objectives", List.of(Map.of("target", 0.999)));
        spec.put("timeWindow", List.of(Map.of("duration", "30d", "isRolling", true)));
        return document("SLO", name, spec);
    }

    public static Map<String, Object> sliDocument(String name) {
        Map<String, Object> spec = Map.of(
            "description", "Test SLI",
            "ratioMetric", Map.of(
                "good", Map.of("metricSource", Map.of("type", "Prometheus", "spec", Map.of("query", "good"))),
                "total", Map.of("metricSource", Map.of("type", "Prometheus", "spec", Map.of("query", "total")))
            )
        );
        return document("SLI", name, spec);
    }

    public static Map<String, Object> dataSourceDocument(String name) {
        Map<String, Object> spec = Map.of(
            "type", "Prometheus",
            "connectionDetails", Map.of("url", "http://localhost:9090")
        );
        return document("DataSource", name, spec);
    }

    public static Map<String, Object> alertPolicyDocument(String name) {
        Map<String, Object> spec = Map.of(
            "alertWhenBreaching", true,
            "conditions", List.of(Map.of("conditionRef", "cond-1")),
            "notificationTargets", List.of(Map.of("targetRef", "target-1"))
        );
        return document("AlertPolicy", name, spec);
    }

    public static Map<String, Object> alertConditionDocument(String name) {
        Map<String, Object> spec = Map.of(
            "severity", "page",
            "condition", Map.of("kind", "burnrate", "op", "gte", "threshold", 2, "lookbackWindow", "1h")
        );
        return document("AlertCondition", name, spec);
    }

    public static Map<String, Object> alertNotificationTargetDocument(String name) {
        Map<String, Object> spec = Map.of("target", "email");
        return document("AlertNotificationTarget", name, spec);
    }

    public static Map<String, Object> document(String kind, String name, Map<String, Object> spec) {
        Map<String, Object> content = new HashMap<>();
        content.put("apiVersion", "openslo/v1");
        content.put("kind", kind);
        content.put("metadata", new HashMap<>(Map.of("name", name)));
        content.put("spec", deepCopy(spec));
        return content;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapValue) {
                copy.put(entry.getKey(), deepCopy((Map<String, Object>) mapValue));
            } else if (value instanceof List<?> listValue) {
                copy.put(entry.getKey(), listValue.stream().map(item -> {
                    if (item instanceof Map<?, ?> mapItem) {
                        return deepCopy((Map<String, Object>) mapItem);
                    }
                    return item;
                }).toList());
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }
}
