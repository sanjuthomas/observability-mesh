package com.srecatalog.instruction.web;

import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.instruction.InstructionTestFixtures;
import com.srecatalog.instruction.model.InstructionStatus;
import com.srecatalog.instruction.model.VersionedInstruction;
import com.srecatalog.instruction.service.InstructionService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstructionControllerMoreLifecycleTest {

    @Mock InstructionService instructionService;
    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InstructionController controller = new InstructionController(
                instructionService, subjectResolver, InstructionTestFixtures.properties());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(subjectResolver.resolveActor(any())).thenReturn(InstructionTestFixtures.CREATOR);
        when(subjectResolver.bearerToken(any())).thenReturn("token");
        when(subjectResolver.sessionId(any())).thenReturn("sess");
    }

    @Test
    void approveSuspendReactivateEndpoints() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.APPROVED);
        when(instructionService.approve(eq("I-1"), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));
        when(instructionService.suspend(eq("I-1"), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 3, Instant.now(), null));
        when(instructionService.reactivate(eq("I-1"), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 4, Instant.now(), null));

        mockMvc.perform(post("/api/v1/instructions/I-1/approve")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/instructions/I-1/suspend")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/instructions/I-1/reactivate")).andExpect(status().isOk());
    }

    @Test
    void releaseUseEndpoint() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(instructionService.releaseUse(eq("I-1"), any(), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));

        mockMvc.perform(post("/api/v1/instructions/I-1/release-use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payment_reference\":\"P-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruction_id").value("I-1"));
    }
}
