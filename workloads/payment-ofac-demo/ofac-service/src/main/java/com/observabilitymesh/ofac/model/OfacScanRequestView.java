package com.observabilitymesh.ofac.model;

import org.bson.Document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OfacScanRequestView(
        String paymentId,
        int paymentVersion,
        int versionNumber,
        String instructionId,
        String owningLob,
        Map<String, Object> debtorAccount,
        Map<String, Object> creditorAccount,
        String creditorName,
        List<Map<String, Object>> intermediaries,
        String lifecycleStatus,
        String result,
        String requestedAt,
        String validIn,
        String validOut) {

    @SuppressWarnings("unchecked")
    public static OfacScanRequestView fromDocument(Document document) {
        List<Document> intermediaryDocs = document.getList("intermediaries", Document.class);
        List<Map<String, Object>> intermediaries = intermediaryDocs == null
                ? List.of()
                : intermediaryDocs.stream()
                        .map(doc -> (Map<String, Object>) (Map<?, ?>) doc)
                        .toList();
        return new OfacScanRequestView(
                document.getString("payment_id"),
                document.getInteger("payment_version"),
                document.getInteger("version_number"),
                document.getString("instruction_id"),
                document.getString("owning_lob"),
                (Map<String, Object>) document.get("debtor_account"),
                (Map<String, Object>) document.get("creditor_account"),
                document.getString("creditor_name"),
                intermediaries,
                document.getString("lifecycle_status"),
                document.getString("result"),
                document.getString("requested_at"),
                document.getString("in"),
                document.getString("out"));
    }

    public Map<String, Object> toUiMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("payment_id", paymentId);
        map.put("payment_version", paymentVersion);
        map.put("version_number", versionNumber);
        map.put("instruction_id", instructionId);
        map.put("owning_lob", owningLob);
        map.put("debtor_account", debtorAccount);
        map.put("creditor_account", creditorAccount);
        map.put("creditor_name", creditorName);
        map.put("intermediaries", intermediaries);
        map.put("lifecycle_status", lifecycleStatus);
        map.put("result", result);
        map.put("requested_at", requestedAt);
        map.put("in", validIn);
        map.put("out", validOut == null ? OfacScanRequestConstants.CURRENT_OUT : validOut);
        return map;
    }
}
