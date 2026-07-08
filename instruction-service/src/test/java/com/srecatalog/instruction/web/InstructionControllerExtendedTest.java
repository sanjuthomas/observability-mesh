package com.srecatalog.instruction.web;

import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.instruction.InstructionTestFixtures;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstructionControllerExtendedTest {

    @Mock InstructionService instructionService;
    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InstructionController controller = new InstructionController(
                instructionService, subjectResolver, InstructionTestFixtures.properties());
        mockMvc = InstructionTestFixtures.standaloneMockMvc(controller);
        when(subjectResolver.resolveActor(any())).thenReturn(InstructionTestFixtures.CREATOR);
        when(subjectResolver.bearerToken(any())).thenReturn("token");
        when(subjectResolver.sessionId(any())).thenReturn("sess");
    }

    @Test
    void listInstructionsReturnsArray() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(instructionService.list(any(), any(), any(), anyInt(), eq("token"), eq("sess")))
                .thenReturn(List.of(new VersionedInstruction(instruction, 1, Instant.now(), null)));

        mockMvc.perform(get("/api/v1/instructions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instruction_id").value("I-1"));
    }

    @Test
    void listVersionsEndpointWorks() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(instructionService.listVersions(eq("I-1"), any(), eq("token"), eq("sess")))
                .thenReturn(List.of(new VersionedInstruction(instruction, 1, Instant.now(), null)));

        mockMvc.perform(get("/api/v1/instructions/I-1/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version_number").value(1));
    }

    @Test
    void rejectInstructionRequiresReason() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(instructionService.reject(eq("I-1"), any(), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));

        mockMvc.perform(post("/api/v1/instructions/I-1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"policy\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void cancelInstructionAcceptsOptionalBody() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(instructionService.cancel(eq("I-1"), any(), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));

        mockMvc.perform(post("/api/v1/instructions/I-1/cancel"))
                .andExpect(status().isOk());
    }

    @Test
    void createInstructionReturnsCreated() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-new");
        when(instructionService.create(any(), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));

        mockMvc.perform(post("/api/v1/instructions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InstructionTestFixtures.createRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instruction_id").value("I-new"));
    }

    @Test
    void updateInstructionReturnsPayload() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(instructionService.update(eq("I-1"), any(), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));

        mockMvc.perform(put("/api/v1/instructions/I-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InstructionTestFixtures.createRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void approveSuspendReactivateAndReleaseUseEndpointsWork() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(instructionService.approve(eq("I-1"), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));
        when(instructionService.suspend(eq("I-1"), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 3, Instant.now(), null));
        when(instructionService.reactivate(eq("I-1"), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 4, Instant.now(), null));
        when(instructionService.releaseUse(eq("I-1"), any(), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 5, Instant.now(), null));

        mockMvc.perform(post("/api/v1/instructions/I-1/approve")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/instructions/I-1/suspend")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/instructions/I-1/reactivate")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/instructions/I-1/release-use")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payment_reference\":\"P-1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void listInstructionsHonorsQueryParameters() throws Exception {
        when(instructionService.list(any(), eq("FICC"), eq("DRAFT"), anyInt(), eq("token"), eq("sess")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/instructions")
                        .param("owning_lob", "FICC")
                        .param("status", "DRAFT")
                        .param("limit", "50"))
                .andExpect(status().isOk());
    }
}
