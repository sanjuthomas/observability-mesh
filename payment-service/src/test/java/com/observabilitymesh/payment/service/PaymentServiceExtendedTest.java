package com.observabilitymesh.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.authzclient.AuthzClient;
import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.common.web.PermissionDeniedException;
import com.observabilitymesh.payment.client.InstructionClient;
import com.observabilitymesh.payment.config.PaymentProperties;
import com.observabilitymesh.payment.config.ServiceIdentity;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.model.PaymentStatus;
import com.observabilitymesh.payment.model.VersionedPayment;
import com.observabilitymesh.payment.repo.PaymentRepository;
import com.observabilitymesh.payment.ofac.OfacScanRequestRepository;
import com.observabilitymesh.payment.security.SecurityEventRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceExtendedTest {

    @Mock PaymentRepository repository;
    @Mock SecurityEventRepository securityEventRepository;
    @Mock OfacScanRequestRepository ofacScanRequestRepository;
    @Mock AuthzClient authzClient;
    @Mock InstructionClient instructionClient;
    @Mock SequenceClient sequenceClient;
    @Mock ServiceIdentity serviceIdentity;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaymentService paymentService;
    private final Subject creator = new Subject(
            "user-001", "Jane", "Doe", "VP", "FICC",
            List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "COMPLIANCE_ANALYST", "", "", 200);
        paymentService = PaymentServiceTestFixtures.paymentService(
                repository, securityEventRepository, ofacScanRequestRepository, authzClient, instructionClient,
                sequenceClient, serviceIdentity, properties);
        when(serviceIdentity.token()).thenReturn("svc-token");
        when(securityEventRepository.allocateEventId(any())).thenReturn("SE-1");
    }

    @Test
    void createRejectsExpiredInstruction() throws Exception {
        JsonNode instruction = objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,
                 "currency":"USD","owning_lob":"FICC","end_date":"2000-01-01T00:00:00Z"}
                """);
        when(instructionClient.getInstruction(eq("I-1"), any(), any())).thenReturn(instruction);
        assertThatThrownBy(() -> paymentService.create("I-1", "2026-07-01", 100.0, creator, "token", "sess"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createDeniedByPolicyRecordsSecurityEvent() throws Exception {
        JsonNode instruction = objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,
                 "currency":"USD","owning_lob":"FICC","end_date":"2099-12-31T00:00:00Z"}
                """);
        when(instructionClient.getInstruction(eq("I-1"), any(), any())).thenReturn(instruction);
        when(sequenceClient.nextPaymentId(any(), eq("FICC"))).thenReturn("P-9");
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.deny(List.of("SELF_APPROVAL"), true));

        assertThatThrownBy(() -> paymentService.create("I-1", "2026-07-01", 100.0, creator, "token", "sess"))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void submitSingleUseMarksInstructionUsed() throws Exception {
        Payment payment = singleUseDraft("P-1");
        when(repository.getCurrent("P-1")).thenReturn(versioned(payment));
        when(repository.listCurrent(eq("I-1"), any(), anyInt(), eq(false))).thenReturn(List.of(versioned(payment)));
        when(instructionClient.getInstruction(eq("I-1"), any(), any()))
                .thenReturn(objectMapper.readTree("""
                        {"status":"APPROVED","instruction_type":"SINGLE_USE","version_number":1,"end_date":"2099-12-31T00:00:00Z"}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"status":"USED","instruction_type":"SINGLE_USE","version_number":2,"end_date":"2099-12-31T00:00:00Z"}
                        """));
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("allowed")));
        when(instructionClient.markUsed(eq("I-1"), eq("P-1"), any(), any()))
                .thenReturn(objectMapper.readTree("{\"status\":\"USED\"}"));
        when(repository.appendVersion(any())).thenAnswer(inv -> new VersionedPayment(inv.getArgument(0), 2, Instant.now(), null));

        VersionedPayment saved = paymentService.submit("P-1", creator, "token", "sess");
        assertThat(saved.payment().status()).isEqualTo(PaymentStatus.SUBMITTED);
    }

    @Test
    void listFiltersByViewPermission() {
        Payment own = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(creator), Payment.newEventId());
        Subject other = new Subject("other", null, null, "VP", "EQUITIES",
                List.of("PAYMENT_CREATOR"), List.of(), null, List.of("EQUITIES"), null, List.of());
        Payment foreign = Payment.create("P-2", "I-2", 1, 10.0, "USD", "2026-07-01", "EQUITIES", "STANDING",
                PaymentAuthorization.userRef(other), Payment.newEventId());
        when(repository.listCurrent(null, null, 10, false))
                .thenReturn(List.of(versioned(own), versioned(foreign)));

        assertThat(paymentService.list(creator, null, null, 10)).hasSize(1);
    }

    @Test
    void approveAutoCancelsWhenInstructionVersionChanged() throws Exception {
        Payment payment = submittedPayment(3);
        when(repository.getCurrent("P-1")).thenReturn(versioned(payment));
        when(instructionClient.getInstruction(eq("I-1"), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":5,"end_date":"2099-12-31T00:00:00Z"}
                """));
        when(repository.appendVersion(any())).thenAnswer(inv -> new VersionedPayment(inv.getArgument(0), 2, Instant.now(), null));

        VersionedPayment saved = paymentService.approve("P-1", creator, "token", "sess");
        assertThat(saved.payment().status()).isEqualTo(PaymentStatus.CANCELLED);
    }

    private Payment singleUseDraft(String paymentId) {
        Payment payment = Payment.create(paymentId, "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "SINGLE_USE",
                PaymentAuthorization.userRef(creator), Payment.newEventId());
        return payment;
    }

    private Payment submittedPayment(int instructionVersion) {
        Payment payment = Payment.create("P-1", "I-1", instructionVersion, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(creator), Payment.newEventId());
        payment.setStatus(PaymentStatus.SUBMITTED);
        payment.setSubmittedAt(Instant.now());
        return payment;
    }

    private VersionedPayment versioned(Payment payment) {
        return new VersionedPayment(payment, 1, Instant.now(), null);
    }
}
