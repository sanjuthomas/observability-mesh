package com.srecatalog.harness.web;

import com.srecatalog.harness.config.HarnessProperties;
import com.srecatalog.harness.model.SessionCredentials;
import com.srecatalog.harness.service.HarnessAdminAccess;
import com.srecatalog.harness.service.HarnessHelpers;
import com.srecatalog.harness.service.SecurityEventCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StatusControllerTest {

    @Mock HarnessProperties properties;
    @Mock HarnessAdminAccess adminAccess;
    @Mock HarnessHelpers helpers;
    @Mock SecurityEventCounter securityEventCounter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new StatusController(properties, adminAccess, helpers, securityEventCounter))
                .build();
    }

    @Test
    void statusReturnsCounts() throws Exception {
        when(adminAccess.requireAdmin(any())).thenReturn(null);
        when(adminAccess.requireAdminSession(any())).thenReturn(new SessionCredentials("sess", "token"));
        when(properties.instructionServiceUrl()).thenReturn("http://localhost:9000");
        when(properties.paymentServiceUrl()).thenReturn("http://localhost:9093");
        when(properties.keycloakConfigured()).thenReturn(true);
        when(helpers.fetchInstructions(any(), any())).thenReturn(List.of());
        when(helpers.fetchPayments(any(), any())).thenReturn(List.of());
        when(securityEventCounter.countInstructionEvents()).thenReturn(5L);
        when(securityEventCounter.countPaymentEvents()).thenReturn(2L);

        mockMvc.perform(get("/api/status").header("X-Session-Id", "sess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruction_total").value(0))
                .andExpect(jsonPath("$.zitadel_configured").value(true))
                .andExpect(jsonPath("$.security_event_count").value(5));
    }

    @Test
    void statusAggregatesInstructionAndPaymentStatuses() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var instructions = List.of(
                mapper.readTree("{\"status\":\"DRAFT\"}"),
                mapper.readTree("{\"status\":\"APPROVED\"}"),
                mapper.readTree("{\"status\":\"APPROVED\"}"));
        var payments = List.of(mapper.readTree("{\"status\":\"SUBMITTED\"}"));

        when(adminAccess.requireAdmin(any())).thenReturn(null);
        when(adminAccess.requireAdminSession(any())).thenReturn(new SessionCredentials("sess", "token"));
        when(properties.instructionServiceUrl()).thenReturn("http://localhost:9000");
        when(properties.paymentServiceUrl()).thenReturn("http://localhost:9093");
        when(properties.keycloakConfigured()).thenReturn(false);
        when(helpers.fetchInstructions(any(), any())).thenReturn(instructions);
        when(helpers.fetchPayments(any(), any())).thenReturn(payments);
        when(securityEventCounter.countInstructionEvents()).thenReturn(0L);
        when(securityEventCounter.countPaymentEvents()).thenReturn(0L);

        mockMvc.perform(get("/api/status").header("X-Session-Id", "sess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruction_total").value(3))
                .andExpect(jsonPath("$.instruction_counts.DRAFT").value(1))
                .andExpect(jsonPath("$.instruction_counts.APPROVED").value(2))
                .andExpect(jsonPath("$.payment_total").value(1))
                .andExpect(jsonPath("$.payment_counts.SUBMITTED").value(1))
                .andExpect(jsonPath("$.keycloak_configured").value(false));
    }

    @Test
    void statusToleratesFetchFailures() throws Exception {
        when(adminAccess.requireAdmin(any())).thenReturn(null);
        when(adminAccess.requireAdminSession(any())).thenReturn(new SessionCredentials("sess", "token"));
        when(properties.instructionServiceUrl()).thenReturn("http://localhost:9000");
        when(properties.paymentServiceUrl()).thenReturn("http://localhost:9093");
        when(properties.keycloakConfigured()).thenReturn(true);
        when(helpers.fetchInstructions(any(), any())).thenThrow(new RuntimeException("down"));
        when(helpers.fetchPayments(any(), any())).thenThrow(new RuntimeException("down"));
        when(securityEventCounter.countInstructionEvents()).thenReturn(1L);
        when(securityEventCounter.countPaymentEvents()).thenReturn(1L);

        mockMvc.perform(get("/api/status").header("X-Session-Id", "sess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruction_total").value(0))
                .andExpect(jsonPath("$.payment_total").value(0));
    }
}
