package com.observabilitymesh.authz.opa;

import com.observabilitymesh.authz.web.dto.PaymentEligibilityContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class PaymentOpaHelper {

    private static final Set<String> TERMINAL_INSTRUCTION_STATUSES =
            Set.of("USED", "REJECTED", "EXPIRED", "CANCELLED");
    private static final Set<String> TERMINAL_PAYMENT_STATUSES =
            Set.of("APPROVED", "REJECTED", "CANCELLED");

    private PaymentOpaHelper() {
    }

    public static String paymentApprovalBlockedReason(
            String paymentStatus,
            String instructionStatus,
            String instructionId,
            String instructionType,
            String paymentInstructionType) {
        paymentStatus = paymentStatus == null ? "" : paymentStatus;
        instructionStatus = instructionStatus == null ? "" : instructionStatus;
        instructionType = instructionType == null ? "" : instructionType;
        paymentInstructionType = paymentInstructionType == null ? "" : paymentInstructionType;
        String instructionLabel = backingInstructionLabel(instructionId);

        if (TERMINAL_PAYMENT_STATUSES.contains(paymentStatus)) {
            if ("APPROVED".equals(paymentStatus)) {
                return "This payment is already APPROVED.";
            }
            return "This payment is " + paymentStatus + " and cannot be approved.";
        }

        boolean singleUseConsumed = "USED".equals(instructionStatus)
                && "SINGLE_USE".equals(instructionType)
                && "SINGLE_USE".equals(paymentInstructionType);

        if (TERMINAL_INSTRUCTION_STATUSES.contains(instructionStatus)) {
            if (!(singleUseConsumed && "SUBMITTED".equals(paymentStatus))) {
                return instructionLabel + " is " + instructionStatus + " and cannot support payment approval.";
            }
        }

        if (!instructionStatus.isBlank()
                && !"APPROVED".equals(instructionStatus)
                && !singleUseConsumed) {
            return instructionLabel + " is " + instructionStatus
                    + "; it must be APPROVED before a payment can be approved.";
        }

        if ("DRAFT".equals(paymentStatus)) {
            return "Payment approval is not permitted while status is DRAFT. Submit the payment first.";
        }

        return null;
    }

    public static String paymentProspectiveInstructionStatus(
            String instructionStatus,
            String instructionType,
            String paymentInstructionType) {
        instructionStatus = instructionStatus == null ? "" : instructionStatus;
        if ("APPROVED".equals(instructionStatus)) {
            return "APPROVED";
        }
        if ("USED".equals(instructionStatus)
                && "SINGLE_USE".equals(instructionType)
                && "SINGLE_USE".equals(paymentInstructionType)) {
            return "USED";
        }
        return null;
    }

    public static Map<String, Object> toOpaPayment(
            PaymentEligibilityContext payment,
            String instructionEndDate,
            String instructionStatus) {
        Map<String, Object> createdBy = new LinkedHashMap<>();
        createdBy.put("user_id", payment.createdByUserId());
        if (payment.createdBySupervisorId() != null) {
            createdBy.put("supervisor_id", payment.createdBySupervisorId());
        }
        Map<String, Object> opaPayment = new LinkedHashMap<>();
        opaPayment.put("payment_id", payment.paymentId());
        opaPayment.put("instruction_id", payment.instructionId());
        opaPayment.put("instruction_version", payment.instructionVersion());
        opaPayment.put("amount", payment.amount());
        opaPayment.put("currency", payment.currency());
        opaPayment.put("instruction_status", instructionStatus);
        opaPayment.put("instruction_end_date", instructionEndDate);
        opaPayment.put("instruction_type", payment.instructionType());
        opaPayment.put("instruction_owning_lob", payment.owningLob());
        opaPayment.put("created_by", createdBy);
        return opaPayment;
    }

    public static PaymentEligibilityContext fromPaymentMap(Map<String, Object> payment) {
        Map<String, Object> createdBy = asMap(payment.get("created_by"));
        return new PaymentEligibilityContext(
                stringValue(payment.get("payment_id")),
                stringValue(payment.get("instruction_id")),
                intValue(payment.get("instruction_version")),
                stringValue(payment.get("status")),
                doubleValue(payment.get("amount")),
                stringValue(payment.get("currency")),
                stringValue(payment.get("owning_lob")),
                stringValue(payment.get("instruction_type")),
                stringValue(createdBy.get("user_id")),
                createdBy.get("supervisor_id") == null ? null : String.valueOf(createdBy.get("supervisor_id")));
    }

    private static String backingInstructionLabel(String instructionId) {
        if (instructionId != null && !instructionId.isBlank()) {
            return "The backing instruction " + instructionId.strip();
        }
        return "The backing instruction";
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

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
