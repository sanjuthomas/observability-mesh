package com.observabilitymesh.authz.service;

import com.observabilitymesh.authz.directory.UserDirectory;
import com.observabilitymesh.authz.opa.InstructionOpaMapper;
import com.observabilitymesh.authz.opa.InstructionOpaMapper.InstructionApprovalContext;
import com.observabilitymesh.authz.opa.InstructionOpaMapper.InstructionOpaContext;
import com.observabilitymesh.authz.opa.OpaClient;
import com.observabilitymesh.authz.opa.OpaClient.ApprovalResult;
import com.observabilitymesh.authz.opa.PaymentOpaHelper;
import com.observabilitymesh.authz.web.dto.EligibleApprover;
import com.observabilitymesh.authz.web.dto.InstructionEligibleApproversResponse;
import com.observabilitymesh.authz.web.dto.PaymentEligibilityContext;
import com.observabilitymesh.authz.web.dto.PaymentEligibleApproversResponse;
import com.observabilitymesh.common.model.Subject;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class EligibilityService {

    private final UserDirectory userDirectory;
    private final OpaClient opaClient;

    public EligibilityService(UserDirectory userDirectory, OpaClient opaClient) {
        this.userDirectory = userDirectory;
        this.opaClient = opaClient;
    }

    public InstructionEligibleApproversResponse eligibleApproversForInstruction(Map<String, Object> instruction) {
        String instructionStatus = stringValue(instruction.get("status"));
        String instructionType = stringValue(instruction.get("instruction_type"));
        String owningLob = stringValue(instruction.get("owning_lob"));
        Map<String, Object> createdBy = asMap(instruction.get("created_by"));
        String instructionId = stringValue(instruction.get("instruction_id"));

        InstructionApprovalContext context = InstructionOpaMapper.instructionOpaContextForApprovalEligibility(instruction);
        List<Subject> candidates = userDirectory.instructionApproverCandidates(owningLob);
        List<EligibleApprover> eligible = eligibleInstructionApprovers(
                candidates, context.opaInstruction(), context.opaAccount());

        List<EligibleApprover> prospectiveEligible = List.of();
        InstructionOpaContext prospective = InstructionOpaMapper.instructionOpaContextAfterSubmission(instruction);
        if (prospective != null) {
            prospectiveEligible = eligibleInstructionApprovers(
                    candidates, prospective.opaInstruction(), prospective.opaAccount());
        }

        return new InstructionEligibleApproversResponse(
                instructionId,
                instructionStatus,
                instructionType,
                owningLob,
                stringValue(createdBy.get("user_id")),
                stringValue(createdBy.get("title")),
                Instant.now().toString(),
                eligible,
                prospectiveEligible,
                candidates.size(),
                context.approvalBlockedReason());
    }

    public PaymentEligibleApproversResponse eligibleApproversForPayment(
            PaymentEligibilityContext payment,
            String instructionStatus,
            String instructionEndDate) {
        String approvalBlockedReason = PaymentOpaHelper.paymentApprovalBlockedReason(
                payment.status(),
                instructionStatus,
                payment.instructionId(),
                payment.instructionType(),
                payment.instructionType());

        List<Subject> candidates = userDirectory.fundingApproverCandidates(payment.owningLob());
        Map<String, Object> opaPayment = PaymentOpaHelper.toOpaPayment(payment, instructionEndDate, instructionStatus);
        List<EligibleApprover> eligible = eligiblePaymentApprovers(candidates, opaPayment);

        List<EligibleApprover> prospectiveEligible = List.of();
        String prospectiveStatus = PaymentOpaHelper.paymentProspectiveInstructionStatus(
                instructionStatus, payment.instructionType(), payment.instructionType());
        if (prospectiveStatus != null && "DRAFT".equals(payment.status())) {
            Map<String, Object> prospectivePayment = PaymentOpaHelper.toOpaPayment(
                    payment, instructionEndDate, prospectiveStatus);
            prospectiveEligible = eligiblePaymentApprovers(candidates, prospectivePayment);
        }

        return new PaymentEligibleApproversResponse(
                payment.paymentId(),
                payment.instructionId(),
                payment.status(),
                payment.amount(),
                payment.currency(),
                payment.owningLob(),
                instructionStatus,
                Instant.now().toString(),
                eligible,
                prospectiveEligible,
                candidates.size(),
                approvalBlockedReason);
    }

    private List<EligibleApprover> eligibleInstructionApprovers(
            List<Subject> candidates,
            Map<String, Object> opaInstruction,
            Map<String, Object> opaAccount) {
        List<EligibleApprover> eligible = new ArrayList<>();
        for (Subject candidate : candidates) {
            ApprovalResult result = opaClient.canApproveInstruction(candidate, opaInstruction, opaAccount);
            if (result.allowed()) {
                eligible.add(new EligibleApprover(
                        candidate.userId(),
                        candidate.displayName(),
                        candidate.title(),
                        result.allowBasis()));
            }
        }
        eligible.sort(Comparator.comparing(EligibleApprover::displayName));
        return eligible;
    }

    private List<EligibleApprover> eligiblePaymentApprovers(
            List<Subject> candidates,
            Map<String, Object> opaPayment) {
        List<EligibleApprover> eligible = new ArrayList<>();
        for (Subject candidate : candidates) {
            ApprovalResult result = opaClient.canApprovePayment(candidate, opaPayment);
            if (result.allowed()) {
                eligible.add(new EligibleApprover(
                        candidate.userId(),
                        candidate.displayName(),
                        candidate.title(),
                        result.allowBasis()));
            }
        }
        eligible.sort(Comparator.comparing(EligibleApprover::displayName));
        return eligible;
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
}
