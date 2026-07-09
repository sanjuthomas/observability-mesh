package com.observabilitymesh.sloprovisioner.model;

import java.util.List;
import java.util.Map;

public final class OpenSloContentReader {

    private OpenSloContentReader() {
    }

    public static String displayName(Map<String, Object> content, String fallback) {
        String value = text(map(content, "metadata"), "displayName");
        return value.isBlank() ? fallback : value;
    }

    public static String service(Map<String, Object> content) {
        return text(map(content, "spec"), "service");
    }

    public static String description(Map<String, Object> content) {
        return text(map(content, "spec"), "description");
    }

    public static String indicatorRef(Map<String, Object> content) {
        return text(map(content, "spec"), "indicatorRef");
    }

    public static Double objectiveTarget(Map<String, Object> content) {
        Map<String, Object> spec = map(content, "spec");
        Object objectives = spec.get("objectives");
        if (!(objectives instanceof Iterable<?> iterable)) {
            return null;
        }
        for (Object entry : iterable) {
            if (entry instanceof Map<?, ?> objective) {
                Object target = objective.get("target");
                if (target instanceof Number number) {
                    return number.doubleValue();
                }
            }
        }
        return null;
    }

    public static String timeWindowLabel(Map<String, Object> content) {
        Map<String, Object> spec = map(content, "spec");
        Object windows = spec.get("timeWindow");
        if (!(windows instanceof Iterable<?> iterable)) {
            return "";
        }
        for (Object entry : iterable) {
            if (entry instanceof Map<?, ?> window) {
                Object duration = window.get("duration");
                if (duration != null) {
                    return String.valueOf(duration);
                }
            }
        }
        return "";
    }

    public static String datasourceRef(Map<String, Object> content) {
        Map<String, Object> ratioMetric = map(map(content, "spec"), "ratioMetric");
        String good = metricSourceRef(map(ratioMetric, "good"));
        if (!good.isBlank()) {
            return good;
        }
        return metricSourceRef(map(ratioMetric, "total"));
    }

    public static String goodQuery(Map<String, Object> content) {
        Map<String, Object> ratioMetric = map(map(content, "spec"), "ratioMetric");
        return promqlQuery(map(ratioMetric, "good"));
    }

    public static String totalQuery(Map<String, Object> content) {
        Map<String, Object> ratioMetric = map(map(content, "spec"), "ratioMetric");
        return promqlQuery(map(ratioMetric, "total"));
    }

    private static String metricSourceRef(Map<String, Object> side) {
        return text(map(side, "metricSource"), "metricSourceRef");
    }

    private static String promqlQuery(Map<String, Object> side) {
        return text(map(map(side, "metricSource"), "spec"), "query");
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
}
