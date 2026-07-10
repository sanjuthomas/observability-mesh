package com.observabilitymesh.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.authzclient.AuthzClient;
import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.client.InstructionClient;
import com.observabilitymesh.payment.client.InstructionNotFoundException;
import com.observabilitymesh.payment.config.PaymentProperties;
import com.observabilitymesh.payment.config.ServiceIdentity;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.model.PaymentStatus;
import com.observabilitymesh.payment.model.VersionedPayment;
import com.observabilitymesh.payment.repo.PaymentRepository;
import com.observabilitymesh.payment.ofac.OfacScanRequestRepository;
import com.observabilitymesh.payment.security.SecurityEventRepository;
import com.observabilitymesh.sequenceclient.SequenceClient;
import com.observabilitymesh.sequenceclient.SequenceClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceValidationTest {

    @Mock PaymentRepository repository;
    @Mock SecurityEventRepository securityEventRepository;
    @Mock OfacScanRequestRepository ofacScanRequestRepository;
    @Mock AuthzClient authzClient;
    @Mock InstructionClient instructionClient;
    @Mock SequenceClient sequenceClient;
    @Mock ServiceIdentity serviceIdentity;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaymentService paymentService;
    private final Subject actor = new Subject(
            "user-001", "Jane", "Doe", "VP", "FICC",
            java.util.List.of("PAYMENT_CREATOR"), java.util.List.of(), null, java.util.List.of("FICC"), null, java.util.List.of());

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac", "scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "", "", "", 200);
        paymentService = PaymentServiceTestFixtures.paymentService(
                repository, securityEventRepository, ofacScanRequestRepository, authzClient, instructionClient,
                sequenceClient, serviceIdentity, properties);
        when(serviceIdentity.token()).thenReturn("svc-token");
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(java.util.List.of("ok")));
        when(securityEventRepository.allocateEventId(any())).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv -> new VersionedPayment(inv.getArgument(0), 2, Instant.now(), null));
    }

    @Test
    void approveCancelsWhenInstructionNotYetEffective() throws Exception {
        Payment payment = submitted();
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,
                 "end_date":"2099-12-31T00:00:00Z","effective_date":"2099-12-31T00:00:00Z"}
                """));
        assertThat(paymentService.approve("P-1", actor, "t", "s").payment().status())
                .isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void approveCancelsWhenInstructionStatusInvalid() throws Exception {
        Payment payment = submitted();
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"DRAFT","instruction_type":"STANDING","version_number":1,"end_date":""}
                """));
        assertThat(paymentService.approve("P-1", actor, "t", "s").payment().status())
                .isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void createFailsWhenSequenceUnavailable() throws Exception {
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,
                 "currency":"USD","owning_lob":"FICC","end_date":"2099-12-31T00:00:00Z"}
                """));
        when(sequenceClient.nextPaymentId(any(), eq("FICC"))).thenThrow(new SequenceClientException("down"));
        assertThatThrownBy(() -> paymentService.create("I-1", "2026-07-01", 1.0, actor, "t", "s"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void cancelFailsWhenInstructionMissing() {
        Payment payment = submitted();
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenThrow(new InstructionNotFoundException("I-1"));
        assertThatThrownBy(() -> paymentService.cancel("P-1", actor, null, "t", "s"))
                .isInstanceOf(InstructionNotFoundException.class);
    }

    @Test
    void createRejectsUnparseableEndDate() throws Exception {
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,
                 "currency":"USD","owning_lob":"FICC","end_date":"not-a-date"}
                """));
        assertThatThrownBy(() -> paymentService.create("I-1", "2026-07-01", 1.0, actor, "t", "s"))
                .isInstanceOf(IllegalStateException.class);
    }

    private Payment submitted() {
        Payment payment = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(actor), Payment.newEventId());
        payment.setStatus(PaymentStatus.SUBMITTED);
        payment.setSubmittedAt(Instant.now());
        return payment;
    }

    private VersionedPayment v(Payment payment) {
        return new VersionedPayment(payment, 1, Instant.now(), null);
    }
}
