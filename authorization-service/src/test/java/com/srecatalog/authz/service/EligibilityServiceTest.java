package com.srecatalog.authz.service;

import com.srecatalog.authz.directory.UserDirectory;
import com.srecatalog.authz.opa.OpaClient;
import com.srecatalog.authz.opa.OpaClient.ApprovalResult;
import com.srecatalog.authz.web.dto.InstructionEligibleApproversResponse;
import com.srecatalog.authz.web.dto.PaymentEligibilityContext;
import com.srecatalog.authz.web.dto.PaymentEligibleApproversResponse;
import com.srecatalog.common.model.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EligibilityServiceTest {

    @Mock OpaClient opaClient;

    private EligibilityService service;

    @BeforeEach
    void setUp() throws Exception {
        Path path = Path.of(new ClassPathResource("users.yaml").getURI());
        service = new EligibilityService(UserDirectory.load(path), opaClient);
    }

    @Test
    void eligibleInstructionApproversUsesOpaCandidates() {
        when(opaClient.canApproveInstruction(any(Subject.class), anyMap(), anyMap()))
                .thenReturn(new ApprovalResult(true, List.of("TITLE_MATCH")));

        Map<String, Object> instruction = instruction("DRAFT");
        InstructionEligibleApproversResponse response = service.eligibleApproversForInstruction(instruction);

        assertThat(response.instructionStatus()).isEqualTo("DRAFT");
        assertThat(response.approvalBlockedReason()).contains("DRAFT");
        assertThat(response.candidatesEvaluated()).isPositive();
    }

    @Test
    void eligiblePaymentApproversReturnsBlockedReasonForDraftPayment() {
        when(opaClient.canApprovePayment(any(Subject.class), anyMap()))
                .thenReturn(new ApprovalResult(false, List.of()));

        PaymentEligibilityContext payment = new PaymentEligibilityContext(
                "P-1", "I-1", 1, "DRAFT", 100.0, "USD", "FICC", "STANDING", "mo-100", null);
        PaymentEligibleApproversResponse response = service.eligibleApproversForPayment(
                payment, "APPROVED", "2027-01-01");

        assertThat(response.approvalBlockedReason()).contains("DRAFT");
        assertThat(response.candidatesEvaluated()).isPositive();
    }

    @Test
    void eligibleInstructionApproversReturnsEligibleUsers() {
        when(opaClient.canApproveInstruction(any(Subject.class), anyMap(), anyMap()))
                .thenReturn(new ApprovalResult(true, List.of("TITLE_MATCH")));

        Map<String, Object> instruction = instruction("SUBMITTED");
        InstructionEligibleApproversResponse response = service.eligibleApproversForInstruction(instruction);

        assertThat(response.approvalBlockedReason()).isNull();
        assertThat(response.eligible()).isNotEmpty();
        assertThat(response.prospectiveEligible()).isEmpty();
    }

    @Test
    void eligiblePaymentApproversIncludesProspectiveForDraftPayment() {
        when(opaClient.canApprovePayment(any(Subject.class), anyMap()))
                .thenReturn(new ApprovalResult(true, List.of("TITLE_MATCH")));

        PaymentEligibilityContext payment = new PaymentEligibilityContext(
                "P-1", "I-1", 1, "DRAFT", 100.0, "USD", "FICC", "STANDING", "mo-100", null);
        PaymentEligibleApproversResponse response = service.eligibleApproversForPayment(
                payment, "APPROVED", "2027-01-01");

        assertThat(response.eligible()).isNotEmpty();
        assertThat(response.prospectiveEligible()).isNotEmpty();
    }

    private static Map<String, Object> instruction(String status) {
        Map<String, Object> instruction = new LinkedHashMap<>();
        instruction.put("instruction_id", "I-1");
        instruction.put("status", status);
        instruction.put("instruction_type", "SINGLE_USE");
        instruction.put("owning_lob", "FICC");
        instruction.put("effective_date", "2026-07-04T00:00:00Z");
        instruction.put("end_date", "2027-07-04T00:00:00Z");
        instruction.put("created_by", Map.of("user_id", "mo-100", "title", "Analyst"));
        instruction.put("funding_account", Map.of("owning_lob", "FICC"));
        return instruction;
    }
}
