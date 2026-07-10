package com.observabilitymesh.harness.web;

import com.observabilitymesh.harness.model.HarnessActionResult;
import com.observabilitymesh.harness.model.SessionCredentials;
import com.observabilitymesh.harness.service.HarnessActions;
import com.observabilitymesh.harness.service.HarnessAdminAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HarnessActionControllerTest {

    @Mock HarnessActions harnessActions;
    @Mock HarnessAdminAccess adminAccess;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HarnessActionController(harnessActions, adminAccess))
                .build();
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void createInstructionsReturnsActionResult() throws Exception {
        when(adminAccess.requireAdminSession(any())).thenReturn(new SessionCredentials("sess", "token"));
        when(harnessActions.createInstructions(anyInt(), any())).thenReturn(actionResult());

        mockMvc.perform(post("/api/actions/create-instructions")
                        .header("X-Session-Id", "sess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("create_instructions"))
                .andExpect(jsonPath("$.succeeded").value(2));
    }

    @Test
    void runPolicyScenarioReturnsLogs() throws Exception {
        when(adminAccess.requireAdminSession(any())).thenReturn(new SessionCredentials("sess", "token"));
        when(harnessActions.runPolicyScenario(any())).thenReturn(actionResult());

        mockMvc.perform(post("/api/actions/run-policy-scenario")
                        .header("X-Session-Id", "sess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void runPaymentPolicyScenarioReturnsLogs() throws Exception {
        when(adminAccess.requireAdminSession(any())).thenReturn(new SessionCredentials("sess", "token"));
        when(harnessActions.runPaymentPolicyScenario(any())).thenReturn(actionResult());

        mockMvc.perform(post("/api/actions/run-payment-policy-scenario")
                        .header("X-Session-Id", "sess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void allCountActionsDelegateToHarnessActions() throws Exception {
        when(adminAccess.requireAdminSession(any())).thenReturn(new SessionCredentials("sess", "token"));
        when(harnessActions.submitInstructions(anyInt(), any())).thenReturn(actionResult());
        when(harnessActions.approveInstructions(anyInt(), any())).thenReturn(actionResult());
        when(harnessActions.rejectInstructions(anyInt(), any())).thenReturn(actionResult());
        when(harnessActions.suspendInstructions(anyInt(), any())).thenReturn(actionResult());
        when(harnessActions.reactivateInstructions(anyInt(), any())).thenReturn(actionResult());
        when(harnessActions.createPayments(anyInt(), any())).thenReturn(actionResult());
        when(harnessActions.updatePayments(anyInt(), any(), any())).thenReturn(actionResult());
        when(harnessActions.submitPayments(anyInt(), any())).thenReturn(actionResult());
        when(harnessActions.approvePayments(anyInt(), any())).thenReturn(actionResult());
        when(harnessActions.rejectPayments(anyInt(), any())).thenReturn(actionResult());

        for (String path : List.of(
                "submit-instructions", "approve-instructions", "reject-instructions",
                "suspend-instructions", "reactivate-instructions", "create-payments",
                "submit-payments", "approve-payments", "reject-payments")) {
            mockMvc.perform(post("/api/actions/" + path)
                            .header("X-Session-Id", "sess")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"count\":1}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/actions/update-payments")
                        .header("X-Session-Id", "sess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":1,\"amount\":1000000}"))
                .andExpect(status().isOk());
    }

    private HarnessActionResult actionResult() {
        var result = new HarnessActionResult("create_instructions", 2);
        result.recordSuccess();
        result.recordSuccess();
        result.log("done");
        result.setOk(true);
        return result;
    }
}
