package com.observabilitymesh.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.authzclient.AuthzClient;
import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.client.InstructionClient;
import com.observabilitymesh.payment.config.PaymentProperties;
import com.observabilitymesh.payment.config.ServiceIdentity;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.model.PaymentStatus;
import com.observabilitymesh.payment.model.VersionedPayment;
import com.observabilitymesh.payment.repo.PaymentRepository;
import com.observabilitymesh.payment.ofac.OfacScanRequestRepository;
import com.observabilitymesh.payment.security.SecurityEventRepository;
import com.observabilitymesh.payment.web.dto.CancelPaymentRequest;
import com.observabilitymesh.sequenceclient.SequenceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceLifecycleTest {

    @Mock PaymentRepository repository;
    @Mock SecurityEventRepository securityEventRepository;
    @Mock OfacScanRequestRepository ofacScanRequestRepository;
    @Mock AuthzClient authzClient;
    @Mock InstructionClient instructionClient;
    @Mock SequenceClient sequenceClient;
    @Mock ServiceIdentity serviceIdentity;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaymentService paymentService;
    private final Subject approver = new Subject(
            "approver-001", "Ann", "Approver", "MD", "FICC",
            List.of("FUNDING_APPROVER"), List.of("MIDDLE_OFFICE"), null, List.of("FICC"), null, List.of());

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "COMPLIANCE_ANALYST", "", "", 200);
        paymentService = PaymentServiceTestFixtures.paymentService(
                repository, securityEventRepository, ofacScanRequestRepository, authzClient, instructionClient,
                sequenceClient, serviceIdentity, properties);
        when(serviceIdentity.token()).thenReturn("svc-token");
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("allowed")));
        when(securityEventRepository.allocateEventId(any())).thenReturn("SE-1");
    }

    @Test
    void approveMovesSubmittedToApproved() throws Exception {
        Payment payment = submittedPayment();
        when(repository.getCurrent("P-1")).thenReturn(versioned(payment));
        JsonNode instruction = objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,"end_date":"2099-12-31T00:00:00Z"}
                """);
        when(instructionClient.getInstruction(eq("I-1"), any(), any())).thenReturn(instruction);
        when(repository.appendVersion(any())).thenAnswer(inv -> new VersionedPayment(inv.getArgument(0), 2, Instant.now(), null));

        VersionedPayment saved = paymentService.approve("P-1", approver, "token", "sess");
        assertThat(saved.payment().status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void cancelDraftPayment() throws Exception {
        Payment payment = draftPayment();
        when(repository.getCurrent("P-1")).thenReturn(versioned(payment));
        when(instructionClient.getInstruction(eq("I-1"), any(), any())).thenReturn(objectMapper.readTree(
                "{\"status\":\"APPROVED\",\"instruction_type\":\"STANDING\",\"version_number\":1,\"end_date\":\"\"}"));
        when(repository.appendVersion(any())).thenAnswer(inv -> new VersionedPayment(inv.getArgument(0), 2, Instant.now(), null));

        VersionedPayment saved = paymentService.cancel("P-1", approver, new CancelPaymentRequest("changed mind"), "token", "sess");
        assertThat(saved.payment().status()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void eligibleApproversDelegatesToAuthz() throws Exception {
        Payment payment = submittedPayment();
        when(repository.getCurrent("P-1")).thenReturn(versioned(payment));
        when(instructionClient.getInstructionAsService("I-1")).thenReturn(objectMapper.readTree(
                "{\"status\":\"APPROVED\",\"end_date\":\"2099-12-31T00:00:00Z\"}"));
        when(authzClient.eligiblePaymentApprovers(any(), any(), any(), any(), any()))
                .thenReturn(Map.of("payment_id", "P-1", "eligible", List.of()));

        Map<String, Object> result = paymentService.eligibleApprovers("P-1");
        assertThat(result.get("payment_id")).isEqualTo("P-1");
    }

    @Test
    void updateRejectsNonDraft() {
        Payment payment = submittedPayment();
        when(repository.getCurrent("P-1")).thenReturn(versioned(payment));
        assertThatThrownBy(() -> paymentService.update("P-1", "I-1", "2026-07-02", 50.0, approver, "t", "s"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    private Payment draftPayment() {
        Subject creator = new Subject("user-001", "Jane", "Doe", "VP", "FICC",
                List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());
        return Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(creator), Payment.newEventId());
    }

    private Payment submittedPayment() {
        Payment payment = draftPayment();
        payment.setStatus(PaymentStatus.SUBMITTED);
        payment.setSubmittedAt(Instant.now());
        return payment;
    }

    private VersionedPayment versioned(Payment payment) {
        return new VersionedPayment(payment, 1, Instant.now(), null);
    }
}
