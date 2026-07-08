package com.observabilitymesh.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.authzclient.AuthzClient;
import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.client.InstructionClient;
import com.observabilitymesh.payment.client.InstructionNotFoundException;
import com.observabilitymesh.payment.client.InstructionStateException;
import com.observabilitymesh.payment.config.PaymentProperties;
import com.observabilitymesh.payment.config.ServiceIdentity;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.model.PaymentStatus;
import com.observabilitymesh.payment.model.VersionedPayment;
import com.observabilitymesh.payment.repo.PaymentRepository;
import com.observabilitymesh.payment.ofac.OfacScanLifecycleStatus;
import com.observabilitymesh.payment.ofac.OfacScanRequestRepository;
import com.observabilitymesh.payment.security.SecurityEventRepository;
import com.observabilitymesh.payment.web.dto.RejectPaymentRequest;
import com.observabilitymesh.sequenceclient.SequenceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

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
class PaymentServiceApprovalTest {

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
                "svc-payment", "Password1!", "", "", "", 200);
        paymentService = PaymentServiceTestFixtures.paymentService(
                repository, securityEventRepository, ofacScanRequestRepository, authzClient, instructionClient,
                sequenceClient, serviceIdentity, properties);
        when(serviceIdentity.token()).thenReturn("svc-token");
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        when(securityEventRepository.allocateEventId(any())).thenReturn("SE-1");
        when(repository.appendVersion(any())).thenAnswer(inv -> new VersionedPayment(inv.getArgument(0), 2, Instant.now(), null));
    }

    @Test
    void approveAllowsUsedSingleUseInstruction() throws Exception {
        Payment payment = submittedSingleUse();
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"USED","instruction_type":"SINGLE_USE","version_number":1,"end_date":"2099-12-31T00:00:00Z",
                 "debtor_account":{"account_id":"D-1"},"creditor_account":{"account_id":"C-1"},
                 "creditor":{"name":"Acme"},"intermediary_agents":[{"bic":"INTMUS33"}]}
                """));
        VersionedPayment saved = paymentService.approve("P-1", approver, "t", "s");
        assertThat(saved.payment().status()).isEqualTo(PaymentStatus.APPROVED);
        org.mockito.Mockito.verify(ofacScanRequestRepository).insert(org.mockito.ArgumentMatchers.argThat(request ->
                request.paymentId().equals("P-1")
                        && request.paymentVersion() == 2
                        && request.versionNumber() == 1
                        && request.creditorName().equals("Acme")
                        && request.lifecycleStatus() == OfacScanLifecycleStatus.OPEN
                        && request.result() == null));
    }

    @Test
    void approveCancelsWhenInstructionExpired() throws Exception {
        Payment payment = submittedStanding();
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,"end_date":"2000-01-01T00:00:00Z"}
                """));
        VersionedPayment saved = paymentService.approve("P-1", approver, "t", "s");
        assertThat(saved.payment().status()).isEqualTo(PaymentStatus.CANCELLED);
        org.mockito.Mockito.verify(ofacScanRequestRepository, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void approveCancelsWhenInstructionTypeChanged() throws Exception {
        Payment payment = submittedStanding();
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"SINGLE_USE","version_number":1,"end_date":"2099-12-31T00:00:00Z"}
                """));
        VersionedPayment saved = paymentService.approve("P-1", approver, "t", "s");
        assertThat(saved.payment().status()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void submitStandingInstructionWithoutUseCall() throws Exception {
        Payment payment = draftStanding();
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,"end_date":"2099-12-31T00:00:00Z"}
                """));
        VersionedPayment saved = paymentService.submit("P-1", approver, "t", "s");
        assertThat(saved.payment().status()).isEqualTo(PaymentStatus.SUBMITTED);
    }

    @Test
    void submitSingleUseSagaFailureThrows() throws Exception {
        Payment payment = draftSingleUse();
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(repository.listCurrent(eq("I-1"), any(), anyInt(), eq(false))).thenReturn(List.of(v(payment)));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"SINGLE_USE","version_number":1,"end_date":"2099-12-31T00:00:00Z"}
                """));
        when(instructionClient.markUsed(any(), any(), any(), any()))
                .thenThrow(new InstructionStateException("already used"));
        assertThatThrownBy(() -> paymentService.submit("P-1", approver, "t", "s"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void submitSingleUseRemoteFailureThrowsBadGateway() throws Exception {
        Payment payment = draftSingleUse();
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(repository.listCurrent(eq("I-1"), any(), anyInt(), eq(false))).thenReturn(List.of(v(payment)));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"SINGLE_USE","version_number":1,"end_date":"2099-12-31T00:00:00Z"}
                """));
        when(instructionClient.markUsed(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("connection reset"));
        assertThatThrownBy(() -> paymentService.submit("P-1", approver, "t", "s"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectStandingDoesNotReleaseInstruction() throws Exception {
        Payment payment = submittedStanding();
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,"end_date":""}
                """));
        paymentService.reject("P-1", approver, new RejectPaymentRequest("no"), "t", "s");
        org.mockito.Mockito.verify(instructionClient, org.mockito.Mockito.never())
                .releaseUse(any(), any(), any(), any());
    }

    @Test
    void updateFailsWhenInstructionMissing() throws Exception {
        Payment payment = draftStanding();
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenThrow(new InstructionNotFoundException("I-1"));
        assertThatThrownBy(() -> paymentService.update("P-1", "I-1", "2026-07-01", 1.0, approver, "t", "s"))
                .isInstanceOf(IllegalStateException.class);
    }

    private Payment draftStanding() {
        return Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(approver), Payment.newEventId());
    }

    private Payment draftSingleUse() {
        return Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "SINGLE_USE",
                PaymentAuthorization.userRef(approver), Payment.newEventId());
    }

    private Payment submittedStanding() {
        Payment payment = draftStanding();
        payment.setStatus(PaymentStatus.SUBMITTED);
        payment.setSubmittedAt(Instant.now());
        return payment;
    }

    private Payment submittedSingleUse() {
        Payment payment = draftSingleUse();
        payment.setStatus(PaymentStatus.SUBMITTED);
        payment.setSubmittedAt(Instant.now());
        return payment;
    }

    private VersionedPayment v(Payment payment) {
        return new VersionedPayment(payment, 1, Instant.now(), null);
    }
}
