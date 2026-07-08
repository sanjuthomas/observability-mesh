package com.srecatalog.authz.opa;

import java.util.LinkedHashMap;
import java.util.Map;

public final class InstructionOpaMapper {

    private InstructionOpaMapper() {
    }

    public static InstructionOpaContext buildInstructionOpaContext(Map<String, Object> instruction) {
        Map<String, Object> createdBy = asMap(instruction.get("created_by"));
        Map<String, Object> fundingAccount = asMap(instruction.get("funding_account"));
        String owningLob = stringValue(instruction.get("owning_lob"));

        Map<String, Object> opaInstruction = new LinkedHashMap<>();
        opaInstruction.put("status", instruction.get("status"));
        opaInstruction.put("type", instruction.get("instruction_type"));
        opaInstruction.put("owning_lob", owningLob);
        opaInstruction.put("effective_date", ensureRfc3339Z(stringValue(instruction.get("effective_date"))));
        opaInstruction.put("end_date", ensureRfc3339Z(stringValue(instruction.get("end_date"))));
        Map<String, Object> opaCreatedBy = new LinkedHashMap<>();
        opaCreatedBy.put("user_id", stringValue(createdBy.get("user_id")));
        opaCreatedBy.put("title", stringValue(createdBy.get("title")));
        if (createdBy.get("supervisor_id") != null) {
            opaCreatedBy.put("supervisor_id", createdBy.get("supervisor_id"));
        }
        opaInstruction.put("created_by", opaCreatedBy);
        if (instruction.get("suspended_by") != null) {
            opaInstruction.put("suspended_by", instruction.get("suspended_by"));
        }

        Map<String, Object> opaAccount = new LinkedHashMap<>();
        Object accountLob = fundingAccount.get("owning_lob");
        opaAccount.put("owning_lob", accountLob == null || String.valueOf(accountLob).isBlank() ? owningLob : accountLob);

        return new InstructionOpaContext(opaInstruction, opaAccount);
    }

    public static InstructionApprovalContext instructionOpaContextForApprovalEligibility(Map<String, Object> instruction) {
        InstructionOpaContext context = buildInstructionOpaContext(instruction);
        String status = stringValue(instruction.get("status"));
        String blockedReason = null;
        if ("DRAFT".equals(status)) {
            blockedReason = "Approval is not permitted while status is DRAFT. Submit the instruction first.";
        }
        return new InstructionApprovalContext(context.opaInstruction(), context.opaAccount(), blockedReason);
    }

    public static InstructionOpaContext instructionOpaContextAfterSubmission(Map<String, Object> instruction) {
        String status = stringValue(instruction.get("status"));
        if (!"DRAFT".equals(status)) {
            return null;
        }
        InstructionOpaContext context = buildInstructionOpaContext(instruction);
        Map<String, Object> prospective = new LinkedHashMap<>(context.opaInstruction());
        prospective.put("status", "SUBMITTED");
        return new InstructionOpaContext(prospective, context.opaAccount());
    }

    private static String ensureRfc3339Z(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.endsWith("Z")) {
            return value;
        }
        if (value.contains("+") || (value.contains("T") && value.length() > 6 && "+-".indexOf(value.charAt(value.length() - 6)) >= 0)) {
            return value;
        }
        return value + "Z";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record InstructionOpaContext(Map<String, Object> opaInstruction, Map<String, Object> opaAccount) {
    }

    public record InstructionApprovalContext(
            Map<String, Object> opaInstruction,
            Map<String, Object> opaAccount,
            String approvalBlockedReason
    ) {
    }
}
