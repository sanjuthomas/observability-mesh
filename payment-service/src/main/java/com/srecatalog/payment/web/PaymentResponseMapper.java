package com.srecatalog.payment.web;

import com.srecatalog.payment.model.PaymentConstants;
import com.srecatalog.payment.model.VersionedPayment;
import com.srecatalog.payment.web.dto.PaymentResponse;

import java.time.Instant;

public final class PaymentResponseMapper {

    private PaymentResponseMapper() {
    }

    public static PaymentResponse toResponse(VersionedPayment record) {
        var payment = record.payment();
        return new PaymentResponse(
                payment.paymentId(),
                record.versionNumber(),
                formatInstant(record.validIn()),
                record.validOut() == null ? PaymentConstants.CURRENT_OUT : formatInstant(record.validOut()),
                payment.instructionId(),
                payment.instructionVersion(),
                payment.status().name(),
                payment.amount(),
                payment.currency(),
                payment.valueDate(),
                payment.owningLob(),
                payment.instructionType(),
                payment.createdBy(),
                payment.submittedBy(),
                payment.approvedBy(),
                payment.rejectedBy(),
                payment.cancelledBy(),
                payment.rejectionReason(),
                payment.cancellationReason(),
                formatInstant(payment.createdAt()),
                formatInstant(payment.updatedAt()),
                formatNullable(payment.submittedAt()),
                formatNullable(payment.approvedAt()),
                formatNullable(payment.rejectedAt()),
                formatNullable(payment.cancelledAt()),
                payment.lifecycleEvents());
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private static String formatNullable(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
