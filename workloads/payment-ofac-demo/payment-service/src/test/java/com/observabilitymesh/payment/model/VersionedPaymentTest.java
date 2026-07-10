package com.observabilitymesh.payment.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VersionedPaymentTest {

    @Test
    void isCurrentWhenValidOutNull() {
        VersionedPayment current = new VersionedPayment(null, 1, Instant.now(), null);
        assertThat(current.isCurrent()).isTrue();
    }

    @Test
    void isNotCurrentWhenClosed() {
        VersionedPayment closed = new VersionedPayment(null, 1, Instant.now(), Instant.now());
        assertThat(closed.isCurrent()).isFalse();
    }
}
