package com.observabilitymesh.payment.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.config.PaymentProperties;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.model.VersionedPayment;
import com.observabilitymesh.payment.service.PaymentAuthorization;
import com.observabilitymesh.payment.service.PaymentService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentControllerExtendedTest {

    @Mock PaymentService paymentService;
    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;
    private final Subject subject = new Subject(
            "user-001", "Jane", "Doe", "VP", "FICC",
            List.of("PAYMENT_CREATOR", "COMPLIANCE_ANALYST"), List.of(), null, List.of("FICC"), null, List.of());

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "COMPLIANCE_ANALYST", "", "", 200);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentController(paymentService, subjectResolver, properties)).build();
        when(subjectResolver.resolveActor(any())).thenReturn(subject);
        when(subjectResolver.bearerToken(any())).thenReturn("token");
        when(subjectResolver.sessionId(any())).thenReturn("sess");
    }

    @Test
    void listPaymentsCapsLimit() throws Exception {
        Payment payment = Payment.create("P-1", "I-1", 1, 100.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        when(paymentService.list(subject, null, null, 500))
                .thenReturn(List.of(new VersionedPayment(payment, 1, Instant.now(), null)));

        mockMvc.perform(get("/api/v1/payments").param("limit", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].payment_id").value("P-1"));
    }

    @Test
    void updatePayment() throws Exception {
        Payment payment = Payment.create("P-1", "I-1", 1, 200.0, "USD", "2026-07-02", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        when(paymentService.update(eq("P-1"), eq("I-1"), eq("2026-07-02"), eq(200.0), eq(subject), eq("token"), eq("sess")))
                .thenReturn(new VersionedPayment(payment, 2, Instant.now(), null));

        mockMvc.perform(put("/api/v1/payments/P-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction_id\":\"I-1\",\"value_date\":\"2026-07-02\",\"amount\":200.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(200.0));
    }

    @Test
    void cancelWithoutBody() throws Exception {
        Payment payment = Payment.create("P-1", "I-1", 1, 100.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        payment.setStatus(com.observabilitymesh.payment.model.PaymentStatus.CANCELLED);
        when(paymentService.cancel(eq("P-1"), eq(subject), eq(null), eq("token"), eq("sess")))
                .thenReturn(new VersionedPayment(payment, 2, Instant.now(), null));

        mockMvc.perform(post("/api/v1/payments/P-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void eligibleApproversHandlesNullMapValues() throws Exception {
        when(paymentService.eligibleApprovers("P-1")).thenReturn(java.util.Map.of());
        mockMvc.perform(post("/api/v1/payments/P-1/eligible-approvers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_id").value(""))
                .andExpect(jsonPath("$.candidates_evaluated").value(0));
    }
}
