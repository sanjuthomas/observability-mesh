package com.srecatalog.payment.web;

import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.common.model.Subject;
import com.srecatalog.payment.config.PaymentProperties;
import com.srecatalog.payment.model.Payment;
import com.srecatalog.payment.model.VersionedPayment;
import com.srecatalog.payment.service.PaymentAuthorization;
import com.srecatalog.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentControllerStandaloneTest {

    @Mock PaymentService paymentService;
    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;
    private final Subject subject = new Subject(
            "user-001", "Jane", "Doe", "VP", "FICC",
            List.of("PAYMENT_CREATOR", "COMPLIANCE_ANALYST"), List.of(), null, List.of("FICC"), null, List.of());

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "security_events", "payment_service",
                "svc-payment", "Password1!", "COMPLIANCE_ANALYST", "", "", 200);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentController(paymentService, subjectResolver, properties)).build();
        when(subjectResolver.resolveActor(any())).thenReturn(subject);
        when(subjectResolver.bearerToken(any())).thenReturn("token");
        when(subjectResolver.sessionId(any())).thenReturn("sess");
    }

    @Test
    void getPaymentById() throws Exception {
        Payment payment = Payment.create("P-1", "I-1", 1, 100.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        when(paymentService.get("P-1", subject)).thenReturn(new VersionedPayment(payment, 1, Instant.now(), null));

        mockMvc.perform(get("/api/v1/payments/P-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_id").value("P-1"));
    }

    @Test
    void eligibleApproversReturnsPayload() throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("payment_id", "P-1");
        payload.put("instruction_id", "I-1");
        payload.put("payment_status", "SUBMITTED");
        payload.put("amount", 100.0);
        payload.put("currency", "USD");
        payload.put("owning_lob", "FICC");
        payload.put("instruction_status", "APPROVED");
        payload.put("evaluated_at", "2026-07-07T00:00:00Z");
        payload.put("eligible", List.of());
        payload.put("prospective_eligible", List.of());
        payload.put("candidates_evaluated", 3);
        payload.put("approval_blocked_reason", null);
        when(paymentService.eligibleApprovers("P-1")).thenReturn(payload);

        mockMvc.perform(post("/api/v1/payments/P-1/eligible-approvers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_id").value("P-1"))
                .andExpect(jsonPath("$.candidates_evaluated").value(3));
    }

    @Test
    void createRequiresJsonBody() throws Exception {
        Payment payment = Payment.create("P-1", "I-1", 1, 100.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        when(paymentService.create(eq("I-1"), eq("2026-07-01"), eq(100.0), eq(subject), eq("token"), eq("sess")))
                .thenReturn(new VersionedPayment(payment, 1, Instant.now(), null));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction_id\":\"I-1\",\"value_date\":\"2026-07-01\",\"amount\":100.0}"))
                .andExpect(status().isCreated());
    }
}
