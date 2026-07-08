package com.srecatalog.instruction.web;

import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.instruction.InstructionTestFixtures;
import com.srecatalog.instruction.model.VersionedInstruction;
import com.srecatalog.instruction.repo.InstructionNotFoundException;
import com.srecatalog.instruction.repo.InstructionRepository;
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
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UiControllerTest {

    @Mock InstructionRepository repository;
    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UiController controller = new UiController(repository, subjectResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(subjectResolver.resolveActor(any())).thenReturn(InstructionTestFixtures.ADMIN);
    }

    @Test
    void listInstructionsReturnsPayload() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(repository.listCurrent(eq("FICC"), eq("DRAFT"), anyInt()))
                .thenReturn(List.of(new VersionedInstruction(instruction, 1, Instant.now(), null)));

        mockMvc.perform(get("/api/ui/instructions").param("owning_lob", "FICC").param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.instructions[0].instruction_id").value("I-1"));
    }

    @Test
    void getInstructionReturns404WhenMissing() throws Exception {
        when(repository.getCurrent("missing")).thenThrow(new InstructionNotFoundException("missing"));

        mockMvc.perform(get("/api/ui/instructions/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInstructionReturnsPayloadWithClosedOut() throws Exception {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        Instant closed = Instant.parse("2026-06-01T00:00:00Z");
        when(repository.getCurrent("I-1")).thenReturn(new VersionedInstruction(
                instruction, 1, Instant.parse("2026-05-01T00:00:00Z"), closed));

        mockMvc.perform(get("/api/ui/instructions/I-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruction.out").value(closed.toString()));
    }

    @Test
    void servesIndexHtml() throws Exception {
        mockMvc.perform(get("/ui/"))
                .andExpect(status().isOk());
    }

    @Test
    void servesIndexWithoutTrailingSlash() throws Exception {
        mockMvc.perform(get("/ui"))
                .andExpect(status().isOk());
    }

    @Test
    void servesInstructionDetailHtml() throws Exception {
        mockMvc.perform(get("/ui/instructions/I-1"))
                .andExpect(status().isOk());
    }
}
