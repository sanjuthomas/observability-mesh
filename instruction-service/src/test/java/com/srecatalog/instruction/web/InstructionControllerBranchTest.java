package com.srecatalog.instruction.web;

import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.instruction.InstructionTestFixtures;
import com.srecatalog.instruction.model.VersionedInstruction;
import com.srecatalog.instruction.service.InstructionService;
import com.srecatalog.instruction.web.dto.CreateInstructionRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstructionControllerBranchTest {

    @Mock InstructionService instructionService;
    @Mock RequestSubjectResolver subjectResolver;
    @Mock HttpServletRequest httpRequest;

    private InstructionController controller;

    @BeforeEach
    void setUp() {
        controller = new InstructionController(
                instructionService, subjectResolver, InstructionTestFixtures.properties());
        when(subjectResolver.resolveActor(httpRequest)).thenReturn(InstructionTestFixtures.CREATOR);
        when(subjectResolver.bearerToken(httpRequest)).thenReturn("token");
        when(subjectResolver.sessionId(httpRequest)).thenReturn("sess");
    }

    @Test
    void createReturnsMappedResponse() {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(instructionService.create(any(CreateInstructionRequest.class), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 1, Instant.now(), null));

        var response = controller.create(InstructionTestFixtures.sampleCreateRequest(), httpRequest);
        assertThat(response.instructionId()).isEqualTo("I-1");
    }

    @Test
    void updateReturnsMappedResponse() {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(instructionService.update(eq("I-1"), any(CreateInstructionRequest.class), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));

        var response = controller.update("I-1", InstructionTestFixtures.sampleCreateRequest(), httpRequest);
        assertThat(response.versionNumber()).isEqualTo(2);
    }

    @Test
    void lifecycleEndpointsDelegateToService() {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        when(instructionService.approve(eq("I-1"), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 2, Instant.now(), null));
        when(instructionService.suspend(eq("I-1"), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 3, Instant.now(), null));
        when(instructionService.reactivate(eq("I-1"), any(), eq("token"), eq("sess")))
                .thenReturn(new VersionedInstruction(instruction, 4, Instant.now(), null));

        assertThat(controller.approve("I-1", httpRequest).versionNumber()).isEqualTo(2);
        assertThat(controller.suspend("I-1", httpRequest).versionNumber()).isEqualTo(3);
        assertThat(controller.reactivate("I-1", httpRequest).versionNumber()).isEqualTo(4);
    }

    @Test
    void eligibleApproversHandlesSparseMap() {
        when(subjectResolver.resolveActor(httpRequest)).thenReturn(InstructionTestFixtures.ADMIN);
        Map<String, Object> sparse = new LinkedHashMap<>();
        sparse.put("instruction_id", "I-1");
        when(instructionService.eligibleApprovers("I-1")).thenReturn(sparse);

        var response = controller.eligibleApprovers("I-1", httpRequest);
        assertThat(response.instructionId()).isEqualTo("I-1");
        assertThat(response.candidatesEvaluated()).isZero();
    }
}
