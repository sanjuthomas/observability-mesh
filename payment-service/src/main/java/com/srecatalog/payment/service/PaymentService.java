package com.srecatalog.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srecatalog.authzclient.AuthzClient;
import com.srecatalog.common.model.PolicyDecision;
import com.srecatalog.common.model.Subject;
import com.srecatalog.common.web.PermissionDeniedException;
import com.srecatalog.payment.client.InstructionClient;
import com.srecatalog.payment.client.InstructionNotFoundException;
import com.srecatalog.payment.client.InstructionStateException;
import com.srecatalog.payment.config.PaymentProperties;
import com.srecatalog.payment.config.ServiceIdentity;
import com.srecatalog.payment.ofac.OfacScanRequest;
import com.srecatalog.payment.ofac.OfacScanRequestFactory;
import com.srecatalog.payment.ofac.OfacScanRequestRepository;
import com.srecatalog.payment.model.LifecycleEvent;
import com.srecatalog.payment.model.Payment;
import com.srecatalog.payment.model.PaymentAction;
import com.srecatalog.payment.model.PaymentStatus;
import com.srecatalog.payment.model.VersionedPayment;
import com.srecatalog.payment.repo.ConcurrentModificationException;
import com.srecatalog.payment.repo.PaymentNotFoundException;
import com.srecatalog.payment.repo.PaymentRepository;
import com.srecatalog.payment.security.PaymentSecurityEvent;
import com.srecatalog.payment.security.SecurityEventRepository;
import com.srecatalog.payment.web.dto.CancelPaymentRequest;
import com.srecatalog.payment.web.dto.RejectPaymentRequest;
import com.srecatalog.sequenceclient.SequenceClient;
import com.srecatalog.sequenceclient.SequenceClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Set<String> APPROVED_STATUSES = Set.of("APPROVED");
    private static final Set<String> DRAFT_PAYMENT_INSTRUCTION_STATUSES = Set.of("DRAFT", "SUBMITTED", "APPROVED");

    private final PaymentRepository repository;
    private final SecurityEventRepository securityEventRepository;
    private final OfacScanRequestRepository ofacScanRequestRepository;
    private final AuthzClient authzClient;
    private final InstructionClient instructionClient;
    private final SequenceClient sequenceClient;
    private final ServiceIdentity serviceIdentity;
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(
            PaymentRepository repository,
            SecurityEventRepository securityEventRepository,
            OfacScanRequestRepository ofacScanRequestRepository,
            AuthzClient authzClient,
            InstructionClient instructionClient,
            SequenceClient sequenceClient,
            ServiceIdentity serviceIdentity,
            PaymentProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("paymentTransactionTemplate") TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.securityEventRepository = securityEventRepository;
        this.ofacScanRequestRepository = ofacScanRequestRepository;
        this.authzClient = authzClient;
        this.instructionClient = instructionClient;
        this.sequenceClient = sequenceClient;
        this.serviceIdentity = serviceIdentity;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public VersionedPayment create(
            String instructionId,
            String valueDate,
            double amount,
            Subject subject,
            String bearerToken,
            String sessionId) {
        JsonNode instruction = instructionClient.getInstruction(instructionId, bearerToken, sessionId);
        validateInstructionAtCreate(instruction);

        String instructionStatus = text(instruction, "status");
        String instructionType = text(instruction, "instruction_type");
        int instructionVersion = intValue(instruction, "version_number", 1);
        String endDate = textOrEmpty(instruction, "end_date");

        String paymentId;
        try {
            String businessDate = LocalDate.now(ZoneOffset.UTC).toString().replace("-", "");
            paymentId = sequenceClient.nextPaymentId(businessDate, text(instruction, "owning_lob"));
        } catch (SequenceClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "sequence allocation failed: " + ex.getMessage());
        }

        Payment payment = Payment.create(
                paymentId,
                instructionId,
                instructionVersion,
                amount,
                text(instruction, "currency"),
                valueDate,
                text(instruction, "owning_lob"),
                instructionType,
                PaymentAuthorization.userRef(subject),
                Payment.newEventId());

        Map<String, Object> authorization = authorize(
                PaymentAction.CREATE, subject, payment, endDate, instructionStatus, bearerToken, sessionId);
        Map<String, Object> details = PaymentAuthorization.detailsWithAuthorization(null, authorization);
        recordEvent(payment, PaymentAction.CREATE, subject, details);
        return saveWithSecurityEvent(payment, PaymentAction.CREATE, subject, details, true);
    }

    public VersionedPayment update(
            String paymentId,
            String instructionId,
            String valueDate,
            double amount,
            Subject subject,
            String bearerToken,
            String sessionId) {
        VersionedPayment current = getCurrentOr404(paymentId);
        Payment payment = current.payment().copy();

        if (payment.status() != PaymentStatus.DRAFT) {
            throw new InvalidStateTransitionException("only DRAFT payments can be updated");
        }
        if (!payment.instructionId().equals(instructionId)) {
            throw new IllegalStateException(
                    "instruction_id cannot be changed (expected " + payment.instructionId() + ", got " + instructionId + ")");
        }

        JsonNode instruction;
        try {
            instruction = instructionClient.getInstruction(payment.instructionId(), bearerToken, sessionId);
        } catch (InstructionNotFoundException ex) {
            throw new IllegalStateException("backing instruction not found: " + payment.instructionId());
        }
        validateInstructionForDraftPayment(instruction);

        payment.setAmount(amount);
        payment.setValueDate(valueDate);
        payment.setInstructionVersion(intValue(instruction, "version_number", 1));
        payment.setUpdatedAt(Instant.now());

        return persistNewVersion(
                payment,
                PaymentAction.UPDATE,
                subject,
                null,
                textOrEmpty(instruction, "end_date"),
                text(instruction, "status"),
                bearerToken,
                sessionId,
                false);
    }

    public VersionedPayment submit(String paymentId, Subject subject, String bearerToken, String sessionId) {
        VersionedPayment current = getCurrentOr404(paymentId);
        Payment payment = current.payment().copy();
        if (payment.status() != PaymentStatus.DRAFT) {
            throw new InvalidStateTransitionException("only DRAFT payments can be submitted");
        }

        JsonNode instruction = instructionClient.getInstruction(payment.instructionId(), bearerToken, sessionId);
        validateInstructionApprovedForSubmit(instruction);

        if ("SINGLE_USE".equals(payment.instructionType())) {
            List<VersionedPayment> associated = repository.listCurrent(payment.instructionId(), null, 100, false);
            validateSingleUseSubmitExclusivity(payment, associated);
        }

        String instructionEndDate = textOrEmpty(instruction, "end_date");
        String instructionStatus = text(instruction, "status");
        payment.setInstructionVersion(intValue(instruction, "version_number", 1));

        Map<String, Object> authorization = authorize(
                PaymentAction.SUBMIT, subject, payment, instructionEndDate, instructionStatus, bearerToken, sessionId);

        String postSubmitInstructionStatus = instructionStatus;
        if ("SINGLE_USE".equals(payment.instructionType())) {
            try {
                instructionClient.markUsed(payment.instructionId(), payment.paymentId(), bearerToken, sessionId);
            } catch (InstructionStateException ex) {
                recordPolicyDenial(PaymentAction.SUBMIT, subject, payment,
                        "Saga step failed — instruction cannot be marked USED: " + ex.getReason(),
                        Map.of("saga_step", "mark_used", "saga_error", ex.getReason()));
                throw new IllegalStateException(ex.getReason());
            } catch (Exception ex) {
                recordPolicyDenial(PaymentAction.SUBMIT, subject, payment,
                        "Saga step failed — instruction-service unreachable: " + ex.getMessage(),
                        Map.of("saga_step", "mark_used", "saga_error", ex.getMessage()));
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Could not mark instruction as USED before submitting payment: " + ex.getMessage());
            }
            instruction = instructionClient.getInstruction(payment.instructionId(), bearerToken, sessionId);
            payment.setInstructionVersion(intValue(instruction, "version_number", 1));
            postSubmitInstructionStatus = textOrEmpty(instruction, "status");
            if (postSubmitInstructionStatus.isBlank()) {
                postSubmitInstructionStatus = "USED";
            }
        }

        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.SUBMITTED);
        payment.setSubmittedBy(PaymentAuthorization.userRef(subject));
        payment.setSubmittedAt(now);
        payment.setUpdatedAt(now);

        Map<String, Object> details = PaymentAuthorization.detailsWithAuthorization(null, authorization);
        return persistNewVersion(
                payment,
                PaymentAction.SUBMIT,
                subject,
                details,
                instructionEndDate,
                postSubmitInstructionStatus,
                bearerToken,
                sessionId,
                true);
    }

    public VersionedPayment approve(String paymentId, Subject subject, String bearerToken, String sessionId) {
        VersionedPayment current = getCurrentOr404(paymentId);
        Payment payment = current.payment().copy();
        if (payment.status() != PaymentStatus.SUBMITTED) {
            throw new InvalidStateTransitionException("only SUBMITTED payments can be approved");
        }

        JsonNode instruction;
        try {
            instruction = instructionClient.getInstruction(payment.instructionId(), bearerToken, sessionId);
        } catch (InstructionNotFoundException ex) {
            return cancelInternal(payment, subject,
                    "backing instruction " + payment.instructionId() + " could not be found at approval time");
        }

        String invalidReason = checkInstructionValidityForApproval(payment, instruction);
        if (invalidReason != null) {
            return cancelInternal(payment, subject, invalidReason);
        }

        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setApprovedBy(PaymentAuthorization.userRef(subject));
        payment.setApprovedAt(now);
        payment.setUpdatedAt(now);

        return persistNewVersion(
                payment,
                PaymentAction.APPROVE,
                subject,
                null,
                textOrEmpty(instruction, "end_date"),
                text(instruction, "status"),
                bearerToken,
                sessionId,
                false,
                instruction);
    }

    public VersionedPayment reject(
            String paymentId,
            Subject subject,
            RejectPaymentRequest request,
            String bearerToken,
            String sessionId) {
        VersionedPayment current = getCurrentOr404(paymentId);
        Payment payment = current.payment().copy();
        if (payment.status() != PaymentStatus.SUBMITTED) {
            throw new InvalidStateTransitionException("only SUBMITTED payments can be rejected");
        }

        JsonNode instruction = instructionClient.getInstruction(payment.instructionId(), bearerToken, sessionId);
        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.REJECTED);
        payment.setRejectedBy(PaymentAuthorization.userRef(subject));
        payment.setRejectionReason(request.reason());
        payment.setRejectedAt(now);
        payment.setUpdatedAt(now);

        VersionedPayment saved = persistNewVersion(
                payment,
                PaymentAction.REJECT,
                subject,
                Map.of("reason", request.reason()),
                textOrEmpty(instruction, "end_date"),
                text(instruction, "status"),
                bearerToken,
                sessionId,
                false);
        tryReleaseSingleUseInstruction(saved.payment(), bearerToken, sessionId);
        return saved;
    }

    public VersionedPayment cancel(
            String paymentId,
            Subject subject,
            CancelPaymentRequest request,
            String bearerToken,
            String sessionId) {
        VersionedPayment current = getCurrentOr404(paymentId);
        Payment payment = current.payment().copy();

        if (payment.status() == PaymentStatus.CANCELLED) {
            throw new InvalidStateTransitionException("payment is already cancelled");
        }
        if (payment.status() != PaymentStatus.DRAFT && payment.status() != PaymentStatus.SUBMITTED) {
            throw new InvalidStateTransitionException("only DRAFT or SUBMITTED payments can be cancelled");
        }

        JsonNode instruction = instructionClient.getInstruction(payment.instructionId(), bearerToken, sessionId);
        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCancelledBy(PaymentAuthorization.userRef(subject));
        String reason = request == null ? null : request.reason();
        payment.setCancellationReason(reason);
        payment.setCancelledAt(now);
        payment.setUpdatedAt(now);
        Map<String, Object> details = reason == null ? Map.of() : Map.of("reason", reason);

        VersionedPayment saved = persistNewVersion(
                payment,
                PaymentAction.CANCEL,
                subject,
                details,
                textOrEmpty(instruction, "end_date"),
                text(instruction, "status"),
                bearerToken,
                sessionId,
                false);
        tryReleaseSingleUseInstruction(saved.payment(), bearerToken, sessionId);
        return saved;
    }

    public VersionedPayment get(String paymentId, Subject subject) {
        VersionedPayment record = getCurrentOr404(paymentId);
        if (!SubjectAccess.canViewPayment(subject, record.payment())) {
            throw new PermissionDeniedException("not authorized to view payment");
        }
        return record;
    }

    public List<VersionedPayment> list(Subject subject, String instructionId, String status, int limit) {
        return repository.listCurrent(instructionId, status, limit, false).stream()
                .filter(record -> SubjectAccess.canViewPayment(subject, record.payment()))
                .toList();
    }

    public Map<String, Object> eligibleApprovers(String paymentId) {
        VersionedPayment record = getCurrentOr404(paymentId);
        Payment payment = record.payment();
        JsonNode instruction = instructionClient.getInstructionAsService(payment.instructionId());
        serviceIdentity.ensureLoggedIn();

        Map<String, Object> paymentPayload = new LinkedHashMap<>();
        paymentPayload.put("payment_id", payment.paymentId());
        paymentPayload.put("instruction_id", payment.instructionId());
        paymentPayload.put("instruction_version", payment.instructionVersion());
        paymentPayload.put("status", payment.status().name());
        paymentPayload.put("amount", payment.amount());
        paymentPayload.put("currency", payment.currency());
        paymentPayload.put("owning_lob", payment.owningLob());
        paymentPayload.put("created_by_user_id", payment.createdBy().userId());
        paymentPayload.put("created_by_supervisor_id", payment.createdBy().supervisorId());

        return authzClient.eligiblePaymentApprovers(
                paymentPayload,
                textOrEmpty(instruction, "status"),
                textOrEmpty(instruction, "end_date"),
                serviceIdentity.token(),
                serviceIdentity.sessionId());
    }

    private VersionedPayment persistNewVersion(
            Payment payment,
            PaymentAction action,
            Subject subject,
            Map<String, Object> details,
            String instructionEndDate,
            String instructionStatus,
            String bearerToken,
            String sessionId,
            boolean skipAuthorize) {
        return persistNewVersion(
                payment, action, subject, details, instructionEndDate, instructionStatus,
                bearerToken, sessionId, skipAuthorize, null);
    }

    private VersionedPayment persistNewVersion(
            Payment payment,
            PaymentAction action,
            Subject subject,
            Map<String, Object> details,
            String instructionEndDate,
            String instructionStatus,
            String bearerToken,
            String sessionId,
            boolean skipAuthorize,
            JsonNode instructionForOfac) {
        if (!skipAuthorize) {
            Map<String, Object> authorization = authorize(
                    action, subject, payment, instructionEndDate, instructionStatus, bearerToken, sessionId);
            details = PaymentAuthorization.detailsWithAuthorization(details, authorization);
        }
        recordEvent(payment, action, subject, details);
        if (action == PaymentAction.APPROVE && instructionForOfac != null) {
            return saveApprovalWithSecurityEventAndOfacScan(payment, action, subject, details, instructionForOfac);
        }
        return saveWithSecurityEvent(payment, action, subject, details, false);
    }

    private Map<String, Object> authorize(
            PaymentAction action,
            Subject subject,
            Payment payment,
            String instructionEndDate,
            String instructionStatus,
            String bearerToken,
            String sessionId) {
        serviceIdentity.ensureLoggedIn();
        PolicyDecision decision = authzClient.evaluatePayment(
                action.name(),
                payment.toOpaPayment(instructionEndDate, instructionStatus),
                instructionEndDate,
                instructionStatus,
                serviceIdentity.token(),
                serviceIdentity.sessionId(),
                bearerToken,
                sessionId,
                subject);
        Map<String, Object> resourceContext = PaymentAuthorization.paymentResourceContext(
                payment, instructionStatus, instructionEndDate);
        Map<String, Object> authorization = PaymentAuthorization.buildAuthorizationBlock(
                decision, subject, action, resourceContext);
        if (!decision.allowed()) {
            if (shouldRecordSecurityEvent(subject)) {
                recordPolicyDenial(action, subject, payment, String.valueOf(authorization.get("summary")),
                        PaymentAuthorization.detailsWithAuthorization(null, authorization));
            }
            throw new PermissionDeniedException(String.valueOf(authorization.get("summary")));
        }
        return authorization;
    }

    private VersionedPayment saveWithSecurityEvent(
            Payment payment,
            PaymentAction action,
            Subject subject,
            Map<String, Object> details,
            boolean initial) {
        VersionedPayment saved = initial ? repository.insertInitial(payment) : repository.appendVersion(payment);
        if (shouldRecordSecurityEvent(subject)) {
            String eventId = securityEventRepository.allocateEventId(payment.paymentId());
            PaymentSecurityEvent event = PaymentSecurityEvent.authorizedAction(
                    action, subject, saved.payment(), saved.versionNumber(), details);
            securityEventRepository.insert(event, eventId);
        }
        return saved;
    }

    private VersionedPayment saveApprovalWithSecurityEventAndOfacScan(
            Payment payment,
            PaymentAction action,
            Subject subject,
            Map<String, Object> details,
            JsonNode instruction) {
        return transactionTemplate.execute(status -> {
            VersionedPayment saved = repository.appendVersion(payment);
            if (shouldRecordSecurityEvent(subject)) {
                String eventId = securityEventRepository.allocateEventId(payment.paymentId());
                PaymentSecurityEvent event = PaymentSecurityEvent.authorizedAction(
                        action, subject, saved.payment(), saved.versionNumber(), details);
                securityEventRepository.insert(event, eventId);
            }
            OfacScanRequest ofacRequest = OfacScanRequestFactory.from(
                    saved.payment(), instruction, saved.versionNumber(), objectMapper);
            ofacScanRequestRepository.insert(ofacRequest);
            return saved;
        });
    }

    private void recordEvent(Payment payment, PaymentAction action, Subject subject, Map<String, Object> details) {
        payment.addLifecycleEvent(new LifecycleEvent(
                Payment.newEventId(),
                action.name(),
                subject.userId(),
                Instant.now().toString(),
                details));
    }

    private void recordPolicyDenial(
            PaymentAction action,
            Subject subject,
            Payment payment,
            String reason,
            Map<String, Object> details) {
        if (!shouldRecordSecurityEvent(subject)) {
            return;
        }
        String eventId = securityEventRepository.allocateEventId(payment.paymentId());
        PaymentSecurityEvent event = PaymentSecurityEvent.policyDenial(
                action, subject, payment, reason, details, null);
        securityEventRepository.insert(event, eventId);
    }

    private VersionedPayment cancelInternal(Payment payment, Subject subject, String reason) {
        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCancelledBy(PaymentAuthorization.userRef(subject));
        payment.setCancellationReason(reason);
        payment.setCancelledAt(now);
        payment.setUpdatedAt(now);
        recordEvent(payment, PaymentAction.CANCEL, subject, Map.of("reason", reason));
        VersionedPayment saved = saveWithSecurityEvent(payment, PaymentAction.CANCEL, subject, Map.of("reason", reason), false);
        tryReleaseSingleUseInstruction(saved.payment(), null, null);
        log.warn("payment cancelled payment_id={} by={} reason={}", payment.paymentId(), subject.userId(), reason);
        return saved;
    }

    private void tryReleaseSingleUseInstruction(Payment payment, String bearerToken, String sessionId) {
        if (!"SINGLE_USE".equals(payment.instructionType())) {
            return;
        }
        if (payment.submittedAt() == null) {
            return;
        }
        if (payment.status() != PaymentStatus.REJECTED && payment.status() != PaymentStatus.CANCELLED) {
            return;
        }
        try {
            instructionClient.releaseUse(payment.instructionId(), payment.paymentId(), bearerToken, sessionId);
        } catch (Exception ex) {
            log.error("failed to release SINGLE_USE instruction payment_id={} instruction_id={} error={}",
                    payment.paymentId(), payment.instructionId(), ex.getMessage(), ex);
        }
    }

    private boolean shouldRecordSecurityEvent(Subject subject) {
        return !properties.securityEventExcludedUserIdSet().contains(subject.userId());
    }

    private VersionedPayment getCurrentOr404(String paymentId) {
        try {
            return repository.getCurrent(paymentId);
        } catch (PaymentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    private static void validateInstructionAtCreate(JsonNode instruction) {
        validateInstructionForDraftPayment(instruction);
    }

    private static void validateInstructionForDraftPayment(JsonNode instruction) {
        String status = text(instruction, "status");
        if (!DRAFT_PAYMENT_INSTRUCTION_STATUSES.contains(status)) {
            throw new IllegalStateException(
                    "instruction is not in a usable state for drafting a payment (status=" + status + "). "
                            + "Only DRAFT, SUBMITTED, or APPROVED instructions can back a draft payment.");
        }
        validateInstructionNotExpired(instruction);
    }

    private static void validateInstructionApprovedForSubmit(JsonNode instruction) {
        String status = text(instruction, "status");
        if (!APPROVED_STATUSES.contains(status)) {
            throw new IllegalStateException(
                    "instruction must be APPROVED before a payment can be submitted (status=" + status + ")");
        }
        validateInstructionNotExpired(instruction);
    }

    private static void validateSingleUseSubmitExclusivity(Payment payment, List<VersionedPayment> associated) {
        List<VersionedPayment> active = associated.stream()
                .filter(record -> record.payment().status() == PaymentStatus.DRAFT
                        || record.payment().status() == PaymentStatus.SUBMITTED)
                .toList();
        if (active.size() > 1) {
            String paymentIds = active.stream()
                    .map(record -> record.payment().paymentId())
                    .sorted()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            throw new IllegalStateException(
                    "cannot submit payment for SINGLE_USE instruction " + payment.instructionId() + ": "
                            + active.size() + " payments are in DRAFT or SUBMITTED (" + paymentIds
                            + "); coordinate with the other drafter to cancel or withdraw conflicting payments before submitting");
        }
    }

    private static void validateInstructionNotExpired(JsonNode instruction) {
        String endDateRaw = textOrEmpty(instruction, "end_date");
        if (!endDateRaw.isBlank()) {
            try {
                Instant end = Instant.parse(endDateRaw.endsWith("Z") ? endDateRaw : endDateRaw + "Z");
                if (end.isBefore(Instant.now())) {
                    throw new IllegalStateException("instruction has expired (end_date=" + endDateRaw + ")");
                }
            } catch (DateTimeParseException ex) {
                throw new IllegalStateException("instruction has unparseable end_date value: " + endDateRaw);
            }
        }
    }

    private static String checkInstructionValidityForApproval(Payment payment, JsonNode instruction) {
        Instant now = Instant.now();
        int currentVersion = intValue(instruction, "version_number", 0);
        if (currentVersion != payment.instructionVersion()) {
            return "instruction was modified after payment creation — payment was created against version "
                    + payment.instructionVersion() + " but the current version is " + currentVersion
                    + "; please review the instruction changes and create a new payment if still valid";
        }
        String status = text(instruction, "status");
        String instructionType = textOrEmpty(instruction, "instruction_type");
        boolean backingOk = APPROVED_STATUSES.contains(status)
                || ("USED".equals(status) && "SINGLE_USE".equals(payment.instructionType())
                && "SINGLE_USE".equals(instructionType));
        if (!backingOk) {
            return "instruction is no longer in an approvable state (current status=" + status
                    + "); it must be APPROVED, or USED when SINGLE_USE, to approve a payment";
        }
        String endDateRaw = textOrEmpty(instruction, "end_date");
        if (!endDateRaw.isBlank()) {
            try {
                Instant end = Instant.parse(endDateRaw.endsWith("Z") ? endDateRaw : endDateRaw + "Z");
                if (end.isBefore(now)) {
                    return "instruction has expired (end_date=" + endDateRaw
                            + "); the payment cannot be approved against an expired instruction";
                }
            } catch (DateTimeParseException ex) {
                return "instruction has an unparseable end_date value: " + endDateRaw;
            }
        }
        String currentType = textOrEmpty(instruction, "instruction_type");
        if (!currentType.isBlank() && !payment.instructionType().isBlank()
                && !currentType.equals(payment.instructionType())) {
            return "instruction type changed since payment creation (payment snapshot="
                    + payment.instructionType() + ", current=" + currentType + ")";
        }

        String effectiveDateRaw = textOrEmpty(instruction, "effective_date");
        if (!effectiveDateRaw.isBlank()) {
            try {
                Instant eff = Instant.parse(effectiveDateRaw.endsWith("Z") ? effectiveDateRaw : effectiveDateRaw + "Z");
                if (eff.isAfter(now)) {
                    return "instruction is not yet effective (effective_date=" + effectiveDateRaw
                            + "); payments cannot be approved before the instruction becomes active";
                }
            } catch (DateTimeParseException ex) {
                return "instruction has an unparseable effective_date value: " + effectiveDateRaw;
            }
        }

        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String textOrEmpty(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? "" : value;
    }

    private static int intValue(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asInt(defaultValue);
    }
}
