package com.srecatalog.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srecatalog.authzclient.AuthzClient;
import com.srecatalog.common.model.PolicyDecision;
import com.srecatalog.common.model.Subject;
import com.srecatalog.common.web.PermissionDeniedException;
import com.srecatalog.payment.client.InstructionClient;
import com.srecatalog.payment.client.InstructionNotFoundException;
import com.srecatalog.payment.config.PaymentProperties;
import com.srecatalog.payment.config.ServiceIdentity;
import com.srecatalog.payment.model.Payment;
import com.srecatalog.payment.model.PaymentStatus;
import com.srecatalog.payment.model.VersionedPayment;
import com.srecatalog.payment.repo.PaymentNotFoundException;
import com.srecatalog.payment.repo.PaymentRepository;
import com.srecatalog.payment.ofac.OfacScanRequestRepository;
import com.srecatalog.payment.security.SecurityEventRepository;
import com.srecatalog.payment.web.dto.RejectPaymentRequest;
import com.srecatalog.sequenceclient.SequenceClient;
import com.srecatalog.sequenceclient.SequenceClientException;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceCoverageTest {

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
            List.of("PAYMENT_CREATOR", "FUNDING_APPROVER"), List.of("MIDDLE_OFFICE"), null, List.of("FICC"), null, List.of());
    private final Subject outsider = new Subject(
            "outsider", "Out", "Side", "VP", "EQUITIES",
            List.of("PAYMENT_CREATOR"), List.of(), null, List.of("EQUITIES"), null, List.of());

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
        when(repository.appendVersion(any())).thenAnswer(inv -> new VersionedPayment(inv.getArgument(0), 2, Instant.now(), null));
        when(repository.insertInitial(any())).thenAnswer(inv -> new VersionedPayment(inv.getArgument(0), 1, Instant.now(), null));
    }

    @Test
    void createFailsWhenSequenceUnavailable() throws Exception {
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(approvedInstruction());
        when(sequenceClient.nextPaymentId(any(), any())).thenThrow(new SequenceClientException("down"));
        assertThatThrownBy(() -> paymentService.create("I-1", "2026-07-01", 1.0, actor, "t", "s"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsExpiredInstruction() throws Exception {
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,"end_date":"2000-01-01T00:00:00Z","currency":"USD","owning_lob":"FICC"}
                """));
        assertThatThrownBy(() -> paymentService.create("I-1", "2026-07-01", 1.0, actor, "t", "s"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createRejectsUnparseableEndDate() throws Exception {
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,"end_date":"not-a-date","currency":"USD","owning_lob":"FICC"}
                """));
        assertThatThrownBy(() -> paymentService.create("I-1", "2026-07-01", 1.0, actor, "t", "s"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approveCancelsOnVersionMismatch() throws Exception {
        Payment payment = submitted("STANDING");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":9,"end_date":"2099-12-31T00:00:00Z"}
                """));
        assertThat(paymentService.approve("P-1", actor, "t", "s").payment().status())
                .isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void approveCancelsOnUnapprovableStatus() throws Exception {
        Payment payment = submitted("STANDING");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"DRAFT","instruction_type":"STANDING","version_number":1,"end_date":"2099-12-31T00:00:00Z"}
                """));
        assertThat(paymentService.approve("P-1", actor, "t", "s").payment().status())
                .isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void approveCancelsOnUnparseableEndDate() throws Exception {
        Payment payment = submitted("STANDING");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,"end_date":"bad-date"}
                """));
        assertThat(paymentService.approve("P-1", actor, "t", "s").payment().status())
                .isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void approveCancelsOnUnparseableEffectiveDate() throws Exception {
        Payment payment = submitted("STANDING");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,"end_date":"2099-12-31T00:00:00Z","effective_date":"bad"}
                """));
        assertThat(paymentService.approve("P-1", actor, "t", "s").payment().status())
                .isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void approveCancelsWhenInstructionMissing() {
        Payment payment = submitted("STANDING");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenThrow(new InstructionNotFoundException("I-1"));
        assertThat(paymentService.approve("P-1", actor, "t", "s").payment().status())
                .isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void getNotFoundMapsToResponseStatusException() {
        when(repository.getCurrent("missing")).thenThrow(new PaymentNotFoundException("missing"));
        assertThatThrownBy(() -> paymentService.get("missing", actor))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getDeniedWhenSubjectCannotView() {
        Payment payment = draft("STANDING");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        assertThatThrownBy(() -> paymentService.get("P-1", outsider))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void listFiltersByViewAccess() {
        Payment visible = draft("STANDING");
        Payment hidden = Payment.create("P-2", "I-2", 1, 10.0, "USD", "2026-07-01", "EQUITIES", "STANDING",
                PaymentAuthorization.userRef(outsider), Payment.newEventId());
        when(repository.listCurrent(null, null, 10, false))
                .thenReturn(List.of(v(visible), v(hidden)));
        assertThat(paymentService.list(actor, null, null, 10)).hasSize(1);
    }

    @Test
    void policyDenialRecordsSecurityEvent() throws Exception {
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.deny(List.of("ALERT_AMOUNT_EXCEEDS_SUBJECT_LIMIT"), false));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(approvedInstruction());
        when(sequenceClient.nextPaymentId(any(), eq("FICC"))).thenReturn("P-9");
        assertThatThrownBy(() -> paymentService.create("I-1", "2026-07-01", 1.0, actor, "t", "s"))
                .isInstanceOf(PermissionDeniedException.class);
        verify(securityEventRepository).insert(any(), eq("SE-1"));
        verify(repository, never()).insertInitial(any());
    }

    @Test
    void submitSingleUseDefaultsBlankPostStatus() throws Exception {
        Payment payment = draft("SINGLE_USE");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(repository.listCurrent(eq("I-1"), any(), anyInt(), eq(false))).thenReturn(List.of(v(payment)));
        when(instructionClient.getInstruction(any(), any(), any()))
                .thenReturn(objectMapper.readTree("""
                        {"status":"APPROVED","instruction_type":"SINGLE_USE","version_number":1,"end_date":"2099-12-31T00:00:00Z"}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"status":"","instruction_type":"SINGLE_USE","version_number":2,"end_date":"2099-12-31T00:00:00Z"}
                        """));
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        VersionedPayment saved = paymentService.submit("P-1", actor, "t", "s");
        assertThat(saved.payment().status()).isEqualTo(PaymentStatus.SUBMITTED);
        verify(instructionClient).markUsed("I-1", "P-1", "t", "s");
    }

    @Test
    void rejectSingleUseReleasesInstruction() throws Exception {
        Payment payment = submitted("SINGLE_USE");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"USED","instruction_type":"SINGLE_USE","version_number":2,"end_date":""}
                """));
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        paymentService.reject("P-1", actor, new RejectPaymentRequest("no"), "t", "s");
        verify(instructionClient).releaseUse("I-1", "P-1", "t", "s");
    }

    @Test
    void rejectSingleUseReleaseFailureIsSwallowed() throws Exception {
        Payment payment = submitted("SINGLE_USE");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(objectMapper.readTree("""
                {"status":"USED","instruction_type":"SINGLE_USE","version_number":2,"end_date":""}
                """));
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        when(instructionClient.releaseUse(any(), any(), any(), any())).thenThrow(new RuntimeException("release failed"));
        assertThat(paymentService.reject("P-1", actor, new RejectPaymentRequest("no"), "t", "s").payment().status())
                .isEqualTo(PaymentStatus.REJECTED);
    }

    @Test
    void cancelDraftWithoutRequestBody() throws Exception {
        Payment payment = draft("STANDING");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstruction(any(), any(), any())).thenReturn(approvedInstruction());
        when(authzClient.evaluatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ok")));
        assertThat(paymentService.cancel("P-1", actor, null, "t", "s").payment().status())
                .isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void updateRejectsNonDraft() {
        Payment payment = submitted("STANDING");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        assertThatThrownBy(() -> paymentService.update("P-1", "I-1", "2026-07-01", 1.0, actor, "t", "s"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void eligibleApproversDelegatesToAuthz() throws Exception {
        Payment payment = submitted("STANDING");
        when(repository.getCurrent("P-1")).thenReturn(v(payment));
        when(instructionClient.getInstructionAsService("I-1")).thenReturn(approvedInstruction());
        when(authzClient.eligiblePaymentApprovers(any(), any(), any(), any(), any()))
                .thenReturn(Map.of("payment_id", "P-1", "eligible", List.of()));
        Map<String, Object> result = paymentService.eligibleApprovers("P-1");
        assertThat(result).containsEntry("payment_id", "P-1");
        verify(serviceIdentity).ensureLoggedIn();
    }

    private com.fasterxml.jackson.databind.JsonNode approvedInstruction() throws Exception {
        return objectMapper.readTree("""
                {"status":"APPROVED","instruction_type":"STANDING","version_number":1,"currency":"USD","owning_lob":"FICC","end_date":"2099-12-31T00:00:00Z"}
                """);
    }

    private Payment draft(String type) {
        return Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", type,
                PaymentAuthorization.userRef(actor), Payment.newEventId());
    }

    private Payment submitted(String type) {
        Payment payment = draft(type);
        payment.setStatus(PaymentStatus.SUBMITTED);
        payment.setSubmittedAt(Instant.now());
        return payment;
    }

    private VersionedPayment v(Payment payment) {
        return new VersionedPayment(payment, 1, Instant.now(), null);
    }
}
