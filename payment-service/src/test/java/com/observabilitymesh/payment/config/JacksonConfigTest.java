package com.observabilitymesh.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.service.PaymentAuthorization;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    @Test
    void paymentObjectMapperUsesSnakeCase() {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.paymentObjectMapper(new ObjectMapper());
        Subject subject = new Subject("u1", "A", "B", "VP", "FICC", List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());
        Payment payment = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        var tree = mapper.valueToTree(payment);
        assertThat(tree.has("instruction_id")).isTrue();
        assertThat(tree.has("payment_id")).isTrue();
    }
}
