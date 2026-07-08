package com.observabilitymesh.payment.ofac;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfacScanRequestConstantsTest {

    @Test
    void documentKeyIncludesPaymentAndVersionNumbers() {
        assertThat(OfacScanRequestConstants.documentKey("P-1", 2, 3)).isEqualTo("P-1|2|3");
    }
}
