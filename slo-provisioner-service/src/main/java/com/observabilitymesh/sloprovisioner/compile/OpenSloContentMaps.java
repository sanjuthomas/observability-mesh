package com.observabilitymesh.sloprovisioner.compile;

import java.util.Map;

final class OpenSloContentMaps {

    private OpenSloContentMaps() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Map<String, Object> parent, String field) {
        if (parent == null) {
            return Map.of();
        }
        Object value = parent.get(field);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    static String text(Map<String, Object> map, String field) {
        if (map == null) {
            return "";
        }
        Object value = map.get(field);
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> metadata(Map<String, Object> content) {
        return map(content, "metadata");
    }

    public static String metadataAnnotation(Map<String, Object> content, String key) {
        Map<String, Object> annotations = map(metadata(content), "annotations");
        return text(annotations, key);
    }
}
