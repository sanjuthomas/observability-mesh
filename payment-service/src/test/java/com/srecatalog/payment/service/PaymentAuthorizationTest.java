package com.srecatalog.payment.service;

import com.srecatalog.common.model.PolicyDecision;
import com.srecatalog.common.model.Subject;
import com.srecatalog.payment.config.PaymentProperties;
import com.srecatalog.payment.model.Payment;
import com.srecatalog.payment.model.PaymentAction;
import com.srecatalog.payment.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentAuthorizationTest {

    private static final Subject SUBJECT = new Subject(
            "user-001", "Jane", "Doe", "VP", "FICC",
            List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());

    @Test
    void buildAuthorizationBlockAllow() {
        Map<String, Object> block = PaymentAuthorization.buildAuthorizationBlock(
                PolicyDecision.allow(List.of("creator may draft")),
                SUBJECT,
                PaymentAction.CREATE,
                Map.of("payment_id", "P-1"));
        assertThat(block.get("decision")).isEqualTo("allow");
        assertThat(block.get("summary")).asString().contains("allowed");
    }

    @Test
    void buildAuthorizationBlockDenyWithNonAlertPrimary() {
        Map<String, Object> block = PaymentAuthorization.buildAuthorizationBlock(
                PolicyDecision.deny(List.of("SELF_APPROVAL", "NO_LIMIT_GROUP_ASSIGNED"), false),
                SUBJECT,
                PaymentAction.APPROVE,
                Map.of());
        assertThat(block.get("summary")).asString().contains("also:");
    }

    @Test
    void buildAuthorizationBlockAllowWithoutBasis() {
        Map<String, Object> block = PaymentAuthorization.buildAuthorizationBlock(
                PolicyDecision.allow(List.of()),
                SUBJECT,
                PaymentAction.UPDATE,
                Map.of());
        assertThat(block.get("summary")).asString().contains("allowed");
    }

    @Test
    void detailsWithAuthorizationMerges() {
        Map<String, Object> merged = PaymentAuthorization.detailsWithAuthorization(
                Map.of("reason", "test"),
                Map.of("summary", "allowed"));
        assertThat(merged).containsKey("authorization");
        assertThat(merged).containsEntry("reason", "test");
    }

    @Test
    void requireAllowedThrowsWhenDenied() {
        Map<String, Object> block = PaymentAuthorization.buildAuthorizationBlock(
                PolicyDecision.deny(List.of("ALERT_EXPIRED_INSTRUCTION"), true),
                SUBJECT,
                PaymentAction.SUBMIT,
                Map.of());
        assertThatThrownBy(() -> PaymentAuthorization.requireAllowed(
                PolicyDecision.deny(List.of("ALERT_EXPIRED_INSTRUCTION"), true), block))
                .isInstanceOf(com.srecatalog.common.web.PermissionDeniedException.class);
    }

    @Test
    void paymentResourceContextIncludesStatus() {
        Payment payment = Payment.create(
                "P-1", "I-1", 1, 100.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(SUBJECT), Payment.newEventId());
        Map<String, Object> context = PaymentAuthorization.paymentResourceContext(payment, "APPROVED", "2026-12-31");
        assertThat(context.get("payment_status")).isEqualTo(PaymentStatus.DRAFT.name());
        assertThat(context.get("instruction_status")).isEqualTo("APPROVED");
    }
}
