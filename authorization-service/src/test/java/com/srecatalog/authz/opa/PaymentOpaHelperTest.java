package com.srecatalog.authz.opa;

import com.srecatalog.authz.web.dto.PaymentEligibilityContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentOpaHelperTest {

    @Test
    void explainsDraftPaymentBlockedReason() {
        String reason = PaymentOpaHelper.paymentApprovalBlockedReason(
                "DRAFT", "APPROVED", "I-1", "STANDING", "STANDING");
        assertThat(reason).contains("DRAFT");
    }

    @Test
    void explainsTerminalPaymentStatus() {
        assertThat(PaymentOpaHelper.paymentApprovalBlockedReason(
                "APPROVED", "APPROVED", "I-1", "STANDING", "STANDING"))
                .contains("already APPROVED");
    }

    @Test
    void explainsRejectedPaymentStatus() {
        assertThat(PaymentOpaHelper.paymentApprovalBlockedReason(
                "REJECTED", "APPROVED", "I-1", "STANDING", "STANDING"))
                .contains("REJECTED");
    }

    @Test
    void explainsCancelledPaymentStatus() {
        assertThat(PaymentOpaHelper.paymentApprovalBlockedReason(
                "CANCELLED", "APPROVED", "I-1", "STANDING", "STANDING"))
                .contains("CANCELLED");
    }

    @Test
    void explainsTerminalInstructionStatus() {
        assertThat(PaymentOpaHelper.paymentApprovalBlockedReason(
                "SUBMITTED", "REJECTED", "I-9", "STANDING", "STANDING"))
                .contains("I-9")
                .contains("REJECTED");
    }

    @Test
    void allowsSingleUseConsumedInstructionForSubmittedPayment() {
        assertThat(PaymentOpaHelper.paymentApprovalBlockedReason(
                "SUBMITTED", "USED", "I-1", "SINGLE_USE", "SINGLE_USE"))
                .isNull();
    }

    @Test
    void explainsNonApprovedInstructionStatus() {
        assertThat(PaymentOpaHelper.paymentApprovalBlockedReason(
                "SUBMITTED", "SUBMITTED", "I-1", "STANDING", "STANDING"))
                .contains("must be APPROVED");
    }

    @Test
    void usesGenericInstructionLabelWhenIdMissing() {
        assertThat(PaymentOpaHelper.paymentApprovalBlockedReason(
                "SUBMITTED", "REJECTED", "", "STANDING", "STANDING"))
                .startsWith("The backing instruction is");
    }

    @Test
    void handlesNullInputsGracefully() {
        assertThat(PaymentOpaHelper.paymentApprovalBlockedReason(null, null, null, null, null))
                .isNull();
    }

    @Test
    void prospectiveInstructionStatusForApprovedBacking() {
        assertThat(PaymentOpaHelper.paymentProspectiveInstructionStatus("APPROVED", "", ""))
                .isEqualTo("APPROVED");
    }

    @Test
    void prospectiveInstructionStatusForUsedSingleUse() {
        assertThat(PaymentOpaHelper.paymentProspectiveInstructionStatus(
                "USED", "SINGLE_USE", "SINGLE_USE"))
                .isEqualTo("USED");
    }

    @Test
    void prospectiveInstructionStatusReturnsNullWhenNotApplicable() {
        assertThat(PaymentOpaHelper.paymentProspectiveInstructionStatus("DRAFT", "STANDING", "STANDING"))
                .isNull();
    }

    @Test
    void buildsOpaPaymentPayload() {
        PaymentEligibilityContext payment = new PaymentEligibilityContext(
                "P-1", "I-1", 1, "SUBMITTED", 100.0, "USD", "FICC", "STANDING", "mo-100", "mo-050");
        Map<String, Object> opaPayment = PaymentOpaHelper.toOpaPayment(payment, "2027-01-01", "APPROVED");
        assertThat(opaPayment).containsEntry("payment_id", "P-1");
        assertThat(opaPayment).containsEntry("instruction_status", "APPROVED");
        assertThat(opaPayment.get("created_by")).isEqualTo(Map.of(
                "user_id", "mo-100",
                "supervisor_id", "mo-050"));
    }

    @Test
    void omitsSupervisorWhenMissing() {
        PaymentEligibilityContext payment = new PaymentEligibilityContext(
                "P-1", "I-1", 1, "SUBMITTED", 100.0, "USD", "FICC", "STANDING", "mo-100", null);
        Map<String, Object> opaPayment = PaymentOpaHelper.toOpaPayment(payment, null, "APPROVED");
        assertThat(opaPayment.get("created_by")).isEqualTo(Map.of("user_id", "mo-100"));
    }

    @Test
    void fromPaymentMapBuildsContext() {
        PaymentEligibilityContext payment = PaymentOpaHelper.fromPaymentMap(Map.of(
                "payment_id", "P-1",
                "instruction_id", "I-1",
                "instruction_version", 2,
                "status", "SUBMITTED",
                "amount", 50.5,
                "currency", "USD",
                "owning_lob", "FICC",
                "instruction_type", "STANDING",
                "created_by", Map.of("user_id", "mo-100", "supervisor_id", "mo-050")));
        assertThat(payment.paymentId()).isEqualTo("P-1");
        assertThat(payment.createdBySupervisorId()).isEqualTo("mo-050");
    }

    @Test
    void fromPaymentMapHandlesMissingCreatedByAndNumericStrings() {
        PaymentEligibilityContext payment = PaymentOpaHelper.fromPaymentMap(Map.of(
                "payment_id", "P-2",
                "instruction_version", "3",
                "amount", "42.5"));
        assertThat(payment.instructionVersion()).isEqualTo(3);
        assertThat(payment.amount()).isEqualTo(42.5);
        assertThat(payment.createdByUserId()).isEmpty();
    }
}
