package com.observabilitymesh.payment.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.config.PaymentProperties;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.model.VersionedPayment;
import com.observabilitymesh.payment.service.PaymentAuthorization;
import com.observabilitymesh.payment.service.PaymentService;
import com.observabilitymesh.payment.web.dto.CancelPaymentRequest;
import com.observabilitymesh.payment.web.dto.RejectPaymentRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentControllerLifecycleTest {

    @Mock PaymentService paymentService;
    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;
    private final Subject subject = new Subject(
            "user-001", "Jane", "Doe", "VP", "FICC",
            List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "", "", "", 200);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentController(paymentService, subjectResolver, properties)).build();
        when(subjectResolver.resolveActor(any())).thenReturn(subject);
        when(subjectResolver.bearerToken(any())).thenReturn("token");
        when(subjectResolver.sessionId(any())).thenReturn("sess");
        Payment payment = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(subject), Payment.newEventId());
        VersionedPayment record = new VersionedPayment(payment, 1, Instant.now(), null);
        when(paymentService.submit(eq("P-1"), eq(subject), any(), any())).thenReturn(record);
        when(paymentService.approve(eq("P-1"), eq(subject), any(), any())).thenReturn(record);
        when(paymentService.reject(eq("P-1"), eq(subject), any(RejectPaymentRequest.class), any(), any())).thenReturn(record);
        when(paymentService.cancel(eq("P-1"), eq(subject), any(), any(), any())).thenReturn(record);
        when(paymentService.update(eq("P-1"), eq("I-1"), eq("2026-07-01"), eq(10.0), eq(subject), any(), any())).thenReturn(record);
    }

    @Test
    void submitEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/payments/P-1/submit")).andExpect(status().isOk());
    }

    @Test
    void approveEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/payments/P-1/approve")).andExpect(status().isOk());
    }

    @Test
    void rejectEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/payments/P-1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void cancelEndpointWithoutBody() throws Exception {
        mockMvc.perform(post("/api/v1/payments/P-1/cancel")).andExpect(status().isOk());
    }

    @Test
    void cancelEndpointWithReason() throws Exception {
        mockMvc.perform(post("/api/v1/payments/P-1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"withdraw\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateEndpoint() throws Exception {
        mockMvc.perform(put("/api/v1/payments/P-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction_id\":\"I-1\",\"value_date\":\"2026-07-01\",\"amount\":10.0}"))
                .andExpect(status().isOk());
    }
}
