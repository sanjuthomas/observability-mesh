package com.observabilitymesh.payment.service;

import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.config.PaymentProperties;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.common.web.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectAccessTest {

    private static final PaymentProperties PROPERTIES = new PaymentProperties(
            "payments", "ofac", "scan-requests", "security_events", "payment_service",
            "svc-payment", "Password1!",
            "COMPLIANCE_ANALYST,COMPLIANCE_OFFICER,PLATFORM_ADMIN",
            "", "admin-001", 200);

    @Test
    void platformAdminCanViewAnyPayment() {
        Subject admin = subject(List.of("PLATFORM_ADMIN"), List.of());
        Payment payment = payment("other-user", "EQUITIES");
        assertThat(SubjectAccess.canViewPayment(admin, payment)).isTrue();
    }

    @Test
    void creatorCanViewOwnPayment() {
        Subject creator = subject(List.of("PAYMENT_CREATOR"), List.of("FICC"));
        Payment payment = payment("user-001", "FICC");
        assertThat(SubjectAccess.canViewPayment(creator, payment)).isTrue();
    }

    @Test
    void fundingApproverNeedsLobCoverage() {
        Subject approver = subject(List.of("FUNDING_APPROVER"), List.of("FICC"));
        assertThat(SubjectAccess.canViewPayment(approver, payment("other", "FICC"))).isTrue();
        assertThat(SubjectAccess.canViewPayment(approver, payment("other", "EQUITIES"))).isFalse();
    }

    @Test
    void requireComplianceAllowsAnalyst() {
        Subject analyst = subject(List.of("COMPLIANCE_ANALYST"), List.of());
        SubjectAccess.requireCompliance(analyst, PROPERTIES);
    }

    @Test
    void requireComplianceRejectsCreator() {
        Subject creator = subject(List.of("PAYMENT_CREATOR"), List.of("FICC"));
        assertThatThrownBy(() -> SubjectAccess.requireCompliance(creator, PROPERTIES))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void requirePlatformAdminRejectsNonAdmin() {
        Subject creator = subject(List.of("PAYMENT_CREATOR"), List.of("FICC"));
        assertThatThrownBy(() -> SubjectAccess.requirePlatformAdmin(creator))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void creatorCanViewPaymentInOwnLobWithoutCoverage() {
        Subject creator = new Subject("user-001", "Jane", "Doe", "VP", "FICC", List.of("PAYMENT_CREATOR"), List.of(), null, List.of(), null, List.of());
        Payment payment = payment("other-user", "FICC");
        assertThat(SubjectAccess.canViewPayment(creator, payment)).isTrue();
    }

    @Test
    void complianceSubjectIncludesPlatformAdminRole() {
        Subject admin = subject(List.of("PLATFORM_ADMIN"), List.of());
        assertThat(SubjectAccess.isComplianceSubject(admin, PROPERTIES)).isTrue();
    }

    private static Subject subject(List<String> roles, List<String> coveringLobs) {
        return new Subject("user-001", "Jane", "Doe", "VP", "FICC", roles, List.of(), null, coveringLobs, null, List.of());
    }

    private static Payment payment(String creatorId, String lob) {
        Subject creator = new Subject(creatorId, null, null, "VP", lob, List.of("PAYMENT_CREATOR"), List.of(), null, List.of(lob), null, List.of());
        return Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", lob, "STANDING",
                PaymentAuthorization.userRef(creator), Payment.newEventId());
    }
}
