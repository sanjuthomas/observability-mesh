package com.observabilitymesh.instruction.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.config.InstructionProperties;
import com.observabilitymesh.instruction.model.InstructionStatus;
import com.observabilitymesh.instruction.model.VersionedInstruction;
import com.observabilitymesh.instruction.service.InstructionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstructionControllerLifecycleTest {

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
    void getInstructionReturnsPayload() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(instructionService.get(eq("I-1"), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));

        mockMvc.perform(get("/api/v1/instructions/I-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruction_id").value("I-1"));
    }

    @Test
    void submitInstructionReturnsSubmittedStatus() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.SUBMITTED);
        when(instructionService.submit(eq("I-1"), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));

        mockMvc.perform(post("/api/v1/instructions/I-1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void useInstructionAcceptsBody() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setStatus(InstructionStatus.USED);
        when(instructionService.use(eq("I-1"), any(), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));

        mockMvc.perform(post("/api/v1/instructions/I-1/use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payment_reference\":\"P-1\",\"end_to_end_identification\":\"P-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USED"));
    }
}
