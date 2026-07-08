package com.observabilitymesh.payment.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.config.PaymentProperties;
import com.observabilitymesh.payment.security.SecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityEventUiControllerTest {

    @Mock SecurityEventRepository securityEventRepository;
    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", "", "", "", 200);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new SecurityEventUiController(securityEventRepository, subjectResolver, properties)).build();
        when(subjectResolver.resolveActor(any())).thenReturn(new Subject(
                "admin-001", null, null, "Platform Admin", "FICC",
                List.of("PLATFORM_ADMIN"), List.of(), null, List.of("FICC"), null, List.of()));
    }

    @Test
    void securityEventsIndexServesHtml() throws Exception {
        mockMvc.perform(get("/ui/security-events/")).andExpect(status().isOk());
    }

    @Test
    void apiListsSecurityEvents() throws Exception {
        when(securityEventRepository.listRecent(200))
                .thenReturn(List.of(Map.of("event_id", "SE-1", "severity", "INFO")));
        mockMvc.perform(get("/api/ui/security-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void apiGetSecurityEventReturns404WhenMissing() throws Exception {
        when(securityEventRepository.findByEventId("missing")).thenReturn(null);
        mockMvc.perform(get("/api/ui/security-events/missing"))
                .andExpect(status().isNotFound());
    }
}
