package com.observabilitymesh.payment.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.model.VersionedPayment;
import com.observabilitymesh.payment.repo.PaymentRepository;
import com.observabilitymesh.payment.service.PaymentAuthorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UiControllerTest {

    @Mock PaymentRepository repository;
    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;
    private final Subject admin = new Subject(
            "admin-001", "Admin", "User", "Platform Admin", "FICC",
            List.of("PLATFORM_ADMIN"), List.of(), null, List.of("FICC"), null, List.of());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UiController(repository, subjectResolver)).build();
        when(subjectResolver.resolveActor(any())).thenReturn(admin);
    }

    @Test
    void uiIndexServesHtml() throws Exception {
        mockMvc.perform(get("/ui/")).andExpect(status().isOk());
    }

    @Test
    void uiPaymentDetailServesHtml() throws Exception {
        mockMvc.perform(get("/ui/payments/P-1")).andExpect(status().isOk());
    }

    @Test
    void apiUiPaymentsListsRecords() throws Exception {
        Payment payment = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(admin), Payment.newEventId());
        when(repository.listCurrent(null, null, 200, true))
                .thenReturn(List.of(new VersionedPayment(payment, 1, Instant.now(), null)));

        mockMvc.perform(get("/api/ui/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.payments[0].payment_id").value("P-1"));
    }

    @Test
    void apiUiPaymentsFiltersOwningLob() throws Exception {
        Payment payment = Payment.create("P-1", "I-1", 1, 10.0, "USD", "2026-07-01", "FICC", "STANDING",
                PaymentAuthorization.userRef(admin), Payment.newEventId());
        Payment other = Payment.create("P-2", "I-2", 1, 10.0, "USD", "2026-07-01", "EQUITIES", "STANDING",
                PaymentAuthorization.userRef(admin), Payment.newEventId());
        when(repository.listCurrent(null, null, 200, true))
                .thenReturn(List.of(new VersionedPayment(payment, 1, Instant.now(), null),
                        new VersionedPayment(other, 1, Instant.now(), null)));

        mockMvc.perform(get("/api/ui/payments").param("owning_lob", "FICC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void apiUiPaymentNotFound() throws Exception {
        when(repository.getCurrent("missing")).thenThrow(new com.observabilitymesh.payment.repo.PaymentNotFoundException("missing"));
        mockMvc.perform(get("/api/ui/payments/missing")).andExpect(status().isNotFound());
    }
}
