package com.observabilitymesh.payment.ofac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.service.PaymentAuthorization;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfacScanRequestFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Subject subject = new Subject(
            "user-001", "Jane", "Doe", "VP", "FICC",
            List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());

    @Test
    void buildsRequestFromPaymentAndInstruction() throws Exception {
        Payment payment = Payment.create(
                "P-100", "I-200", 2, 50.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        var instruction = objectMapper.readTree("""
                {
                  "debtor_account": {"account_id": "D-1", "name": "Debtor Bank"},
                  "creditor_account": {"account_id": "C-1"},
                  "creditor": {"name": "Acme Corp"},
                  "intermediary_agents": [
                    {"bic": "INTMUS33", "name": "Intermediary One"}
                  ]
                }
                """);

        OfacScanRequest request = OfacScanRequestFactory.from(payment, instruction, 3, objectMapper);

        assertThat(request.paymentId()).isEqualTo("P-100");
        assertThat(request.paymentVersion()).isEqualTo(3);
        assertThat(request.versionNumber()).isEqualTo(1);
        assertThat(request.instructionId()).isEqualTo("I-200");
        assertThat(request.owningLob()).isEqualTo("FICC");
        assertThat(request.debtorAccount()).containsEntry("account_id", "D-1");
        assertThat(request.creditorAccount()).containsEntry("account_id", "C-1");
        assertThat(request.creditorName()).isEqualTo("Acme Corp");
        assertThat(request.intermediaries()).hasSize(1);
        assertThat(request.intermediaries().getFirst()).containsEntry("bic", "INTMUS33");
        assertThat(request.requestedAt()).isNotNull();
        assertThat(request.in()).isEqualTo(request.requestedAt());
        assertThat(request.out()).isEqualTo(OfacScanRequestConstants.CURRENT_OUT);
        assertThat(request.lifecycleStatus()).isEqualTo(OfacScanLifecycleStatus.OPEN);
        assertThat(request.result()).isNull();
    }

    @Test
    void handlesMissingInstructionParties() throws Exception {
        Payment payment = Payment.create(
                "P-101", "I-201", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        var instruction = objectMapper.readTree("{}");

        OfacScanRequest request = OfacScanRequestFactory.from(payment, instruction, 2, objectMapper);

        assertThat(request.debtorAccount()).isEmpty();
        assertThat(request.creditorAccount()).isEmpty();
        assertThat(request.creditorName()).isEmpty();
        assertThat(request.intermediaries()).isEmpty();
        assertThat(request.lifecycleStatus()).isEqualTo(OfacScanLifecycleStatus.OPEN);
        assertThat(request.result()).isNull();
    }
}
