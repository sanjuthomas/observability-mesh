package com.observabilitymesh.payment.web;

import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.model.PaymentConstants;
import com.observabilitymesh.payment.model.VersionedPayment;
import com.observabilitymesh.payment.service.PaymentAuthorization;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentResponseMapperTest {

    @Test
    void mapsCurrentVersionOutSentinel() {
        Subject subject = new Subject("u1", null, null, "VP", "FICC", List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());
        Payment payment = Payment.create("P-1", "I-1", 2, 50.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        Instant now = Instant.parse("2026-07-07T12:00:00Z");
        VersionedPayment record = new VersionedPayment(payment, 1, now, null);
        var response = PaymentResponseMapper.toResponse(record);
        assertThat(response.paymentId()).isEqualTo("P-1");
        assertThat(response.recordOut()).isEqualTo(PaymentConstants.CURRENT_OUT);
        assertThat(response.versionNumber()).isEqualTo(1);
    }

    @Test
    void mapsClosedVersionOutTimestamp() {
        Subject subject = new Subject("u1", null, null, "VP", "FICC", List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());
        Payment payment = Payment.create("P-1", "I-1", 2, 50.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        Instant now = Instant.parse("2026-07-07T12:00:00Z");
        Instant closed = Instant.parse("2026-07-08T12:00:00Z");
        VersionedPayment record = new VersionedPayment(payment, 1, now, closed);
        var response = PaymentResponseMapper.toResponse(record);
        assertThat(response.recordOut()).isEqualTo(closed.toString());
    }
}
