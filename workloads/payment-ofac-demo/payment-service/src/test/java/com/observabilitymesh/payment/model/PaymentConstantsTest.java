package com.observabilitymesh.payment.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentConstantsTest {

    @Test
    void documentKeyRoundTrip() {
        assertThat(PaymentConstants.documentKey("P-100", 3)).isEqualTo("P-100|3");
        assertThat(PaymentConstants.paymentIdFromDocumentKey("P-100|3")).isEqualTo("P-100");
    }
}
