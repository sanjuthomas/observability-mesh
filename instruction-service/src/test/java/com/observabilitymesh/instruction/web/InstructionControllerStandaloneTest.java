package com.observabilitymesh.instruction.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.config.InstructionProperties;
import com.observabilitymesh.instruction.service.InstructionService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstructionControllerStandaloneTest {

    @Mock InstructionService instructionService;
    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InstructionProperties properties = InstructionTestFixtures.properties();
        InstructionController controller = new InstructionController(instructionService, subjectResolver, properties);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(subjectResolver.resolveActor(any())).thenReturn(InstructionTestFixtures.ADMIN);
    }

    @Test
    void eligibleApproversDefaultsMissingFields() throws Exception {
        when(instructionService.eligibleApprovers("I-2")).thenReturn(Map.of());

        mockMvc.perform(post("/api/v1/instructions/I-2/eligible-approvers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruction_id").value(""))
                .andExpect(jsonPath("$.candidates_evaluated").value(0));
    }

    @Test
    void eligibleApproversReturnsPayload() throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("instruction_id", "I-1");
        payload.put("instruction_status", "SUBMITTED");
        payload.put("instruction_type", "STANDING");
        payload.put("owning_lob", "FICC");
        payload.put("created_by_user_id", "user-001");
        payload.put("created_by_title", "VP");
        payload.put("evaluated_at", "now");
        payload.put("eligible", List.of(Map.of("user_id", "user-002")));
        payload.put("prospective_eligible", List.of());
        payload.put("candidates_evaluated", 3);
        payload.put("approval_blocked_reason", "");
        when(instructionService.eligibleApprovers("I-1")).thenReturn(payload);

        mockMvc.perform(post("/api/v1/instructions/I-1/eligible-approvers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruction_id").value("I-1"))
                .andExpect(jsonPath("$.candidates_evaluated").value(3))
                .andExpect(jsonPath("$.eligible[0].user_id").value("user-002"));
    }

    @Test
    void eligibleApproversHandlesNonNumericCandidatesEvaluated() throws Exception {
        when(instructionService.eligibleApprovers("I-3")).thenReturn(Map.of("candidates_evaluated", "n/a"));

        mockMvc.perform(post("/api/v1/instructions/I-3/eligible-approvers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates_evaluated").value(0));
    }
}
