package com.srecatalog.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srecatalog.authzclient.AuthzClient;
import com.srecatalog.common.model.PolicyDecision;
import com.srecatalog.common.model.Subject;
import com.srecatalog.payment.client.InstructionClient;
import com.srecatalog.payment.config.PaymentProperties;
import com.srecatalog.payment.config.ServiceIdentity;
import com.srecatalog.payment.model.Payment;
import com.srecatalog.payment.model.PaymentStatus;
import com.srecatalog.payment.model.VersionedPayment;
import com.srecatalog.payment.repo.PaymentRepository;
import com.srecatalog.payment.ofac.OfacScanRequestRepository;
import com.srecatalog.payment.security.SecurityEventRepository;
import com.srecatalog.payment.web.dto.RejectPaymentRequest;
import com.srecatalog.sequenceclient.SequenceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceBranchesTest {

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
                "svc-payment", "Password1!", "COMPLIANCE_ANALYST", "admin-001", "", 200);
        paymentService = PaymentServiceTestFixtures.paymentService(
                repository, securityEventRepository, ofacScanRequestRepository, authzClient, instructionClient,
                sequenceClient, serviceIdentity, properties);
        when(serviceIdentity.token()).thenReturn("svc-token");
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        when(securityEventRepository.allocateEventId(any())).thenReturn("SE-1");
    }

    @Test
    void createRejectsDraftInstructionStatus() throws Exception {
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree(
                "{\"status\":\"USED\",\"instruction_type\":\"STANDING\",\"version_number\":1,\"currency\":\"USD\",\"owning_lob\":\"FICC\"}"));
        assertThatThrownBy(() -> paymentService.create("I-1", "2026-07-01", 1.0, creator, "t", "s"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void submitRejectsUnapprovedInstruction() throws Exception {
        Payment payment = draft("STANDING");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree(
                "{\"status\":\"DRAFT\",\"instruction_type\":\"STANDING\",\"version_number\":1,\"end_date\":\"2099-12-31T00:00:00Z\"}"));
        assertThatThrownBy(() -> paymentService.submit("P-1", creator, "t", "s"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void submitRejectsConflictingSingleUsePayments() throws Exception {
        Payment payment = draft("SINGLE_USE");
        Payment other = draft("SINGLE_USE");
        other.setStatus(PaymentStatus.SUBMITTED);
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(repository.listCurrent(eq("I-1"), any(), anyInt(), eq(false)))
                .thenReturn(List.of(v(payment), v(other)));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree(
                "{\"status\":\"APPROVED\",\"instruction_type\":\"SINGLE_USE\",\"version_number\":1,\"end_date\":\"2099-12-31T00:00:00Z\"}"));
        assertThatThrownBy(() -> paymentService.submit("P-1", creator, "t", "s"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectRequiresSubmittedStatus() {
        Payment payment = draft("STANDING");
        payment.setStatus(PaymentStatus.DRAFT);
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        assertThatThrownBy(() -> paymentService.reject("P-1", creator, new RejectPaymentRequest("no"), "t", "s"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancelRejectsApprovedPayment() {
        Payment payment = draft("STANDING");
        payment.setStatus(PaymentStatus.APPROVED);
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        assertThatThrownBy(() -> paymentService.cancel("P-1", creator, null, "t", "s"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancelRejectsAlreadyCancelled() {
        Payment payment = draft("STANDING");
        payment.setStatus(PaymentStatus.CANCELLED);
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        assertThatThrownBy(() -> paymentService.cancel("P-1", creator, null, "t", "s"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void updateRejectsInstructionIdChange() {
        Payment payment = draft("STANDING");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        assertThatThrownBy(() -> paymentService.update("P-1", "I-OTHER", "2026-07-01", 1.0, creator, "t", "s"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void securityEventsSkippedForExcludedUser() throws Exception {
        Subject excluded = new Subject(
                "admin-001", "Admin", "User", "Admin", "FICC",
                List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree(
                "{\"status\":\"APPROVED\",\"instruction_type\":\"STANDING\",\"version_number\":1,\"currency\":\"USD\",\"owning_lob\":\"FICC\",\"end_date\":\"2099-12-31T00:00:00Z\"}"));
        when(sequenceClient.nextPaymentId(any(), eq("FICC"))).thenReturn("P-2");
        when(repository.insertInitial(any())).thenAnswer(inv -> new VersionedPayment(inv.getArgument(0), 1, Instant.now(), null));
        paymentService.create("I-1", "2026-07-01", 1.0, excluded, "t", "s");
        org.mockito.Mockito.verifyNoInteractions(securityEventRepository);
    }

    private Payment draft(String type) {
        return Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", type,
                PaymentAuthorization.userRef(creator), Payment.newEventId());
    }

    private VersionedPayment v(Payment payment) {
        return new VersionedPayment(payment, 1, Instant.now(), null);
    }
}
