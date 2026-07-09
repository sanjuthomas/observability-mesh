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
import com.observabilitymesh.payment.web.dto.RejectPaymentRequest;
import com.observabilitymesh.sequenceclient.SequenceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository repository;
    @Mock SecurityEventRepository securityEventRepository;
    @Mock OfacScanRequestRepository ofacScanRequestRepository;
    @Mock AuthzClient authzClient;
    @Mock InstructionClient instructionClient;
    @Mock SequenceClient sequenceClient;
    @Mock ServiceIdentity serviceIdentity;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaymentService paymentService;
    private final Subject subject = new Subject(
            "user-001", "Jane", "Doe", "VP", "FICC",
            List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac", "scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "COMPLIANCE_ANALYST", "", "", 200);
        paymentService = PaymentServiceTestFixtures.paymentService(
                repository, securityEventRepository, ofacScanRequestRepository, authzClient, instructionClient,
                sequenceClient, serviceIdentity, properties);
    }

    @Test
    void createAllocatesPaymentIdAndPersistsDraft() throws Exception {
        JsonNode instruction = objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,
                 "currency":"USD","owning_lob":"FICC","end_date":"2099-12-31T00:00:00Z"}
                """);
        when(instructionClient.getInstruction(eq("I-1"), any(), any())).thenReturn(instruction);
        when(sequenceClient.nextPaymentId(anyString(), eq("FICC"))).thenReturn("P-100");
        when(serviceIdentity.token()).thenReturn("svc-token");
        when(serviceIdentity.sessionId()).thenReturn("svc-session");
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("creator may draft")));
        when(securityEventRepository.allocateEventId("P-100")).thenReturn("SE-1");
        when(repository.insertInitial(any())).thenAnswer(inv -> {
            Payment payment = inv.getArgument(0);
            return new VersionedPayment(payment, 1, Instant.now(), null);
        });

        VersionedPayment saved = paymentService.create("I-1", "2026-07-01", 100.0, subject, "user-token", "sess");
        assertThat(saved.payment().paymentId()).isEqualTo("P-100");
        assertThat(saved.payment().status()).isEqualTo(PaymentStatus.DRAFT);
        verify(repository).insertInitial(any());
    }

    @Test
    void submitRejectsNonDraft() {
        Payment payment = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        payment.setStatus(PaymentStatus.SUBMITTED);
        when(repository.getCurrent("P-1")).thenReturn(new VersionedPayment(payment, 1, Instant.now(), null));

        assertThatThrownBy(() -> paymentService.submit("P-1", subject, "token", "sess"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void rejectReleasesSingleUseInstruction() throws Exception {
        Payment payment = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "SINGLE_USE",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        payment.setStatus(PaymentStatus.SUBMITTED);
        payment.setSubmittedAt(Instant.now());
        VersionedPayment current = new VersionedPayment(payment, 1, Instant.now(), null);
        when(repository.getCurrent("P-1")).thenReturn(current);
        JsonNode instruction = objectMapper.readTree("""
                {"status":"USED","instruction_type":"SINGLE_USE","version_number":2,"end_date":""}
                """);
        when(instructionClient.getInstruction(eq("I-1"), any(), any())).thenReturn(instruction);
        when(serviceIdentity.token()).thenReturn("svc-token");
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("approver may reject")));
        when(securityEventRepository.allocateEventId("P-1")).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv -> {
            Payment updated = inv.getArgument(0);
            return new VersionedPayment(updated, 2, Instant.now(), null);
        });
        when(instructionClient.releaseUse(eq("I-1"), eq("P-1"), any(), any()))
                .thenReturn(objectMapper.readTree("{\"status\":\"APPROVED\"}"));

        VersionedPayment saved = paymentService.reject("P-1", subject, new RejectPaymentRequest("no"), "token", "sess");
        assertThat(saved.payment().status()).isEqualTo(PaymentStatus.REJECTED);
        verify(instructionClient).releaseUse("I-1", "P-1", "token", "sess");
    }

    @Test
    void getDeniesUnauthorizedViewer() {
        Payment payment = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "EQUITIES", "STANDING",
                PaymentAuthorization.userRef(new Subject("other", null, null, "VP", "EQUITIES",
                        List.of("PAYMENT_CREATOR"), List.of(), null, List.of("EQUITIES"), null, List.of())),
                Payment.newEventId());
        when(repository.getCurrent("P-1")).thenReturn(new VersionedPayment(payment, 1, Instant.now(), null));

        assertThatThrownBy(() -> paymentService.get("P-1", subject))
                .isInstanceOf(com.observabilitymesh.common.web.PermissionDeniedException.class);
    }
}
