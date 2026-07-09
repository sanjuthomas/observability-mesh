package com.observabilitymesh.payment.security;

import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.model.PaymentAction;
import com.observabilitymesh.payment.service.PaymentAuthorization;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSecurityEventTest {

    @Test
    void authorizedActionIncludesSummary() {
        Subject subject = new Subject("u1", "A", "B", "VP", "FICC", List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());
        Payment payment = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        Map<String, Object> details = Map.of("authorization", Map.of("summary", "allowed"));
        PaymentSecurityEvent event = PaymentSecurityEvent.authorizedAction(
                PaymentAction.CREATE, subject, payment, 1, details);
        assertThat(event.severity().name()).isEqualTo("INFO");
        assertThat(event.event().reason()).isEqualTo("allowed");
    }

    @Test
    void policyDenialMarksAlertSeverity() {
        Subject subject = new Subject("u1", null, null, "VP", "FICC", List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());
        Payment payment = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        PaymentSecurityEvent event = PaymentSecurityEvent.policyDenial(
                PaymentAction.SUBMIT, subject, payment, "denied", Map.of(), null);
        assertThat(event.severity().name()).isEqualTo("ALERT");
        assertThat(event.details()).containsEntry("policy_engine", "opa");
    }
}
