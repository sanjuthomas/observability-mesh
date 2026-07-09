package com.observabilitymesh.payment.service;

import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.model.PaymentAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAuthorizationExtendedTest {

    private static final Subject SUBJECT = new Subject(
            "user-001", "Jane", "Doe", "VP", "FICC",
            List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());

    @Test
    void denyWithEmptyViolationsUsesPolicyDenied() {
        Map<String, Object> block = PaymentAuthorization.buildAuthorizationBlock(
                PolicyDecision.deny(List.of(), false),
                SUBJECT,
                PaymentAction.CREATE,
                null);
        assertThat(block.get("summary")).asString().contains("policy denied");
    }

    @Test
    void denyWithAlertPrimaryUsesAlertLabel() {
        Map<String, Object> block = PaymentAuthorization.buildAuthorizationBlock(
                PolicyDecision.deny(List.of("ALERT_LOB_COVERAGE_VIOLATION"), true),
                SUBJECT,
                PaymentAction.SUBMIT,
                Map.of());
        assertThat(block.get("summary")).asString().contains("LOB");
        assertThat(block.get("is_alert")).isEqualTo(true);
    }

    @Test
    void denyWithUnknownViolationCodeFallsBackToLowercase() {
        Map<String, Object> block = PaymentAuthorization.buildAuthorizationBlock(
                PolicyDecision.deny(List.of("CUSTOM_RULE"), false),
                SUBJECT,
                PaymentAction.APPROVE,
                Map.of());
        assertThat(block.get("summary")).asString().contains("custom rule");
    }

    @Test
    void paymentResourceContextDefaultsBlankInstructionStatus() {
        Payment payment = Payment.create(
                "P-1", "I-1", 1, 100.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(SUBJECT), Payment.newEventId());
        Map<String, Object> context = PaymentAuthorization.paymentResourceContext(payment, "  ", null);
        assertThat(context.get("instruction_status")).isEqualTo("APPROVED");
        assertThat(context.get("instruction_end_date")).isEqualTo("");
    }
}
