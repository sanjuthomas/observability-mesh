package com.observabilitymesh.ofac.model;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OfacScanRequestViewTest {

    @Test
    void fromDocumentMapsFields() {
        Document document = new Document("payment_id", "P-1")
                .append("payment_version", 2)
                .append("version_number", 1)
                .append("instruction_id", "I-1")
                .append("owning_lob", "FICC")
                .append("debtor_account", Map.of("account_id", "D-1"))
                .append("creditor_account", Map.of("account_id", "C-1"))
                .append("creditor_name", "Acme")
                .append("intermediaries", List.of())
                .append("lifecycle_status", "OPEN")
                .append("requested_at", "2026-07-08T12:00:00Z")
                .append("in", "2026-07-08T12:00:00Z")
                .append("out", OfacScanRequestConstants.CURRENT_OUT);

        OfacScanRequestView view = OfacScanRequestView.fromDocument(document);

        assertThat(view.paymentId()).isEqualTo("P-1");
        assertThat(view.toUiMap().get("payment_version")).isEqualTo(2);
        assertThat(view.toUiMap().get("lifecycle_status")).isEqualTo("OPEN");
    }
}
