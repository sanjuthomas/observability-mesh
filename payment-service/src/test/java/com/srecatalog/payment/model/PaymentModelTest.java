package com.srecatalog.payment.model;

import com.srecatalog.common.model.Subject;
import com.srecatalog.payment.service.PaymentAuthorization;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentModelTest {

    @Test
    void toOpaPaymentIncludesInstructionContext() {
        Subject subject = new Subject("u1", "A", "B", "VP", "FICC", List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());
        Payment payment = Payment.create("P-1", "I-1", 2, 100.0, "USD", "2026-07-01", "FICC", "SINGLE_USE",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        Map<String, Object> opa = payment.toOpaPayment("2099-12-31", "APPROVED");
        assertThat(opa.get("payment_id")).isEqualTo("P-1");
        assertThat(opa.get("instruction_status")).isEqualTo("APPROVED");
        assertThat(opa.get("instruction_type")).isEqualTo("SINGLE_USE");
    }

    @Test
    void copyPreservesLifecycleEvents() {
        Subject subject = new Subject("u1", null, null, "VP", "FICC", List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());
        Payment payment = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        Payment copy = payment.copy();
        assertThat(copy.lifecycleEvents()).hasSize(1);
        assertThat(copy.paymentId()).isEqualTo("P-1");
    }
}
