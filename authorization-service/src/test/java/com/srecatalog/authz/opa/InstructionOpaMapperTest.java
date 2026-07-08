package com.srecatalog.authz.opa;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionOpaMapperTest {

    @Test
    void buildsInstructionContextAndDraftBlockedReason() {
        Map<String, Object> instruction = new LinkedHashMap<>();
        instruction.put("status", "DRAFT");
        instruction.put("instruction_type", "SINGLE_USE");
        instruction.put("owning_lob", "FICC");
        instruction.put("effective_date", "2026-07-04T00:00:00");
        instruction.put("end_date", "2027-07-04T00:00:00");
        instruction.put("created_by", Map.of("user_id", "mo-100", "title", "Analyst"));
        instruction.put("funding_account", Map.of("owning_lob", "FICC"));

        var context = InstructionOpaMapper.buildInstructionOpaContext(instruction);
        assertThat(context.opaInstruction()).containsEntry("type", "SINGLE_USE");
        assertThat(context.opaInstruction().get("effective_date")).isEqualTo("2026-07-04T00:00:00Z");

        var approval = InstructionOpaMapper.instructionOpaContextForApprovalEligibility(instruction);
        assertThat(approval.approvalBlockedReason()).contains("DRAFT");
    }

    @Test
    void buildsProspectiveSubmittedContextForDraft() {
        Map<String, Object> instruction = Map.of(
                "status", "DRAFT",
                "instruction_type", "SINGLE_USE",
                "owning_lob", "FICC",
                "created_by", Map.of("user_id", "mo-100", "title", "Analyst"),
                "funding_account", Map.of());

        var prospective = InstructionOpaMapper.instructionOpaContextAfterSubmission(instruction);
        assertThat(prospective).isNotNull();
        assertThat(prospective.opaInstruction()).containsEntry("status", "SUBMITTED");
    }

    @Test
    void skipsProspectiveContextWhenNotDraft() {
        Map<String, Object> instruction = Map.of(
                "status", "SUBMITTED",
                "instruction_type", "SINGLE_USE",
                "owning_lob", "FICC",
                "created_by", Map.of("user_id", "mo-100", "title", "Analyst"),
                "funding_account", Map.of());
        assertThat(InstructionOpaMapper.instructionOpaContextAfterSubmission(instruction)).isNull();
    }

    @Test
    void includesSupervisorAndSuspendedByWhenPresent() {
        Map<String, Object> instruction = new LinkedHashMap<>();
        instruction.put("status", "SUBMITTED");
        instruction.put("instruction_type", "STANDING");
        instruction.put("owning_lob", "FICC");
        instruction.put("created_by", Map.of(
                "user_id", "mo-100",
                "title", "Analyst",
                "supervisor_id", "mo-050"));
        instruction.put("suspended_by", Map.of("user_id", "admin-001"));
        instruction.put("funding_account", Map.of());

        var context = InstructionOpaMapper.buildInstructionOpaContext(instruction);
        assertThat(context.opaInstruction().get("created_by"))
                .isEqualTo(Map.of("user_id", "mo-100", "title", "Analyst", "supervisor_id", "mo-050"));
        assertThat(context.opaInstruction()).containsKey("suspended_by");
        assertThat(context.opaAccount()).containsEntry("owning_lob", "FICC");
    }

    @Test
    void fallsBackToInstructionLobWhenFundingAccountMissing() {
        Map<String, Object> instruction = Map.of(
                "status", "SUBMITTED",
                "instruction_type", "STANDING",
                "owning_lob", "FX",
                "created_by", Map.of("user_id", "mo-100", "title", "Analyst"),
                "funding_account", Map.of("owning_lob", ""));

        var context = InstructionOpaMapper.buildInstructionOpaContext(instruction);
        assertThat(context.opaAccount()).containsEntry("owning_lob", "FX");
    }

    @Test
    void preservesTimezoneOffsetDates() {
        Map<String, Object> instruction = Map.of(
                "status", "SUBMITTED",
                "instruction_type", "STANDING",
                "owning_lob", "FICC",
                "effective_date", "2026-07-04T00:00:00+00:00",
                "end_date", "2027-07-04T00:00:00Z",
                "created_by", Map.of("user_id", "mo-100", "title", "Analyst"),
                "funding_account", Map.of());

        var context = InstructionOpaMapper.buildInstructionOpaContext(instruction);
        assertThat(context.opaInstruction().get("effective_date")).isEqualTo("2026-07-04T00:00:00+00:00");
        assertThat(context.opaInstruction().get("end_date")).isEqualTo("2027-07-04T00:00:00Z");
    }

    @Test
    void submittedInstructionHasNoBlockedReason() {
        Map<String, Object> instruction = Map.of(
                "status", "SUBMITTED",
                "instruction_type", "STANDING",
                "owning_lob", "FICC",
                "created_by", Map.of("user_id", "mo-100", "title", "Analyst"),
                "funding_account", Map.of());

        var approval = InstructionOpaMapper.instructionOpaContextForApprovalEligibility(instruction);
        assertThat(approval.approvalBlockedReason()).isNull();
    }
}
