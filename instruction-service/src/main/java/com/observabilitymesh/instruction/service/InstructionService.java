package com.observabilitymesh.instruction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.authzclient.AuthzClient;
import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.common.web.PermissionDeniedException;
import com.observabilitymesh.instruction.config.InstructionProperties;
import com.observabilitymesh.instruction.config.ServiceTokenHolder;
import com.observabilitymesh.instruction.model.CashSettlementInstruction;
import com.observabilitymesh.instruction.model.InstructionAction;
import com.observabilitymesh.instruction.model.InstructionRouteValidator;
import com.observabilitymesh.instruction.model.InstructionStatus;
import com.observabilitymesh.instruction.model.InstructionType;
import com.observabilitymesh.instruction.model.LifecycleEvent;
import com.observabilitymesh.instruction.model.VersionedInstruction;
import com.observabilitymesh.instruction.repo.ConcurrentModificationException;
import com.observabilitymesh.instruction.repo.InstructionNotFoundException;
import com.observabilitymesh.instruction.repo.InstructionRepository;
import com.observabilitymesh.instruction.security.InstructionSecurityEvent;
import com.observabilitymesh.instruction.security.SecurityEventRepository;
import com.observabilitymesh.instruction.web.dto.CancelInstructionRequest;
import com.observabilitymesh.instruction.web.dto.CreateInstructionRequest;
import com.observabilitymesh.instruction.web.dto.RejectInstructionRequest;
import com.observabilitymesh.instruction.web.dto.ReleaseUseInstructionRequest;
import com.observabilitymesh.instruction.web.dto.UseInstructionRequest;
import com.observabilitymesh.sequenceclient.SequenceClient;
import com.observabilitymesh.sequenceclient.SequenceClientException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InstructionService {

    private final InstructionRepository repository;
    private final SecurityEventRepository securityEventRepository;
    private final AuthzClient authzClient;
    private final SequenceClient sequenceClient;
    private final ServiceTokenHolder serviceTokenHolder;
    private final InstructionProperties properties;
    private final ObjectMapper objectMapper;

    public InstructionService(
            InstructionRepository repository,
            SecurityEventRepository securityEventRepository,
            AuthzClient authzClient,
            SequenceClient sequenceClient,
            ServiceTokenHolder serviceTokenHolder,
            InstructionProperties properties,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.securityEventRepository = securityEventRepository;
        this.authzClient = authzClient;
        this.sequenceClient = sequenceClient;
        this.serviceTokenHolder = serviceTokenHolder;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public VersionedInstruction create(
            CreateInstructionRequest request,
            Subject subject,
            String bearerToken,
            String sessionId) {
        String businessDate = LocalDate.now(ZoneOffset.UTC).toString().replace("-", "");
        String instructionId;
        try {
            instructionId = sequenceClient.nextInstructionId(businessDate, request.owningLob());
        } catch (SequenceClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "sequence allocation failed: " + ex.getMessage());
        }

        CashSettlementInstruction instruction = InstructionMapper.fromRequest(
                request, subject, instructionId, InstructionStatus.DRAFT, null);
        InstructionRouteValidator.validate(instruction);

        Map<String, Object> authorization = authorize(
                InstructionAction.CREATE, subject, instruction, bearerToken, sessionId, true, null);
        Map<String, Object> details = InstructionAuthorization.detailsWithAuthorization(null, authorization);
        recordEvent(instruction, InstructionAction.CREATE, subject, details);
        return saveWithSecurityEvent(instruction, InstructionAction.CREATE, subject, details, true);
    }

    public VersionedInstruction update(
            String instructionId,
            CreateInstructionRequest request,
            Subject subject,
            String bearerToken,
            String sessionId) {
        VersionedInstruction current = getCurrentOr404(instructionId);
        CashSettlementInstruction existing = current.instruction();
        if (existing.status() != InstructionStatus.DRAFT) {
            throw new InvalidStateTransitionException("only DRAFT instructions can be updated");
        }

        CashSettlementInstruction instruction = InstructionMapper.fromRequest(
                request, subject, existing.instructionId(), existing.status(), existing.createdBy());
        instruction.setLifecycleEvents(existing.lifecycleEvents());
        instruction.setCreatedAt(existing.createdAt());
        instruction.setSubmittedAt(existing.submittedAt());
        instruction.setApprovedBy(existing.approvedBy());
        instruction.setApprovedAt(existing.approvedAt());
        instruction.setRejectedBy(existing.rejectedBy());
        instruction.setRejectedAt(existing.rejectedAt());
        instruction.setRejectionReason(existing.rejectionReason());
        instruction.setCancelledAt(existing.cancelledAt());
        instruction.setSuspendedBy(existing.suspendedBy());
        instruction.setSuspendedAt(existing.suspendedAt());
        instruction.setLastUsedAt(existing.lastUsedAt());
        instruction.setUsageCount(existing.usageCount());
        instruction.setUsedBy(existing.usedBy());
        InstructionRouteValidator.validate(instruction);

        return persistNewVersion(instruction, InstructionAction.UPDATE, subject, null, bearerToken, sessionId, false);
    }

    public VersionedInstruction get(String instructionId, Subject subject, String bearerToken, String sessionId) {
        VersionedInstruction record = getCurrentOr404(instructionId);
        boolean recordView = shouldRecordViewSecurityEvent(subject);
        Map<String, Object> authorization = authorize(
                InstructionAction.VIEW, subject, record.instruction(), bearerToken, sessionId, recordView, null);
        if (recordView) {
            recordAuthorizedView(subject, record, authorization);
        }
        return record;
    }

    public List<VersionedInstruction> list(
            Subject subject,
            String owningLob,
            String status,
            int limit,
            String bearerToken,
            String sessionId) {
        List<VersionedInstruction> records = repository.listCurrent(owningLob, status, limit);
        return records.stream()
                .filter(record -> record.instruction().status() != InstructionStatus.CANCELLED)
                .filter(record -> tryAuthorizeView(subject, record, bearerToken, sessionId))
                .toList();
    }

    public List<VersionedInstruction> listVersions(
            String instructionId,
            Subject subject,
            String bearerToken,
            String sessionId) {
        get(instructionId, subject, bearerToken, sessionId);
        return repository.listVersions(instructionId);
    }

    public Map<String, Object> eligibleApprovers(String instructionId) {
        VersionedInstruction record = getCurrentOr404(instructionId);
        serviceTokenHolder.ensureLoggedIn();
        Map<String, Object> instructionPayload = objectMapper.convertValue(record.instruction(), Map.class);
        return authzClient.eligibleInstructionApprovers(
                instructionPayload,
                serviceTokenHolder.token(),
                serviceTokenHolder.sessionId());
    }

    public VersionedInstruction submit(String instructionId, Subject subject, String bearerToken, String sessionId) {
        return lifecycleTransition(instructionId, InstructionStatus.DRAFT, InstructionStatus.SUBMITTED,
                InstructionAction.SUBMIT, subject, bearerToken, sessionId, instruction -> {
                    instruction.setSubmittedAt(Instant.now());
                    return null;
                });
    }

    public VersionedInstruction approve(String instructionId, Subject subject, String bearerToken, String sessionId) {
        return lifecycleTransition(instructionId, InstructionStatus.SUBMITTED, InstructionStatus.APPROVED,
                InstructionAction.APPROVE, subject, bearerToken, sessionId, instruction -> {
                    instruction.setApprovedBy(InstructionAuthorization.userRef(subject));
                    instruction.setApprovedAt(Instant.now());
                    return null;
                });
    }

    public VersionedInstruction reject(
            String instructionId,
            Subject subject,
            RejectInstructionRequest request,
            String bearerToken,
            String sessionId) {
        return lifecycleTransition(instructionId, InstructionStatus.SUBMITTED, InstructionStatus.REJECTED,
                InstructionAction.REJECT, subject, bearerToken, sessionId, instruction -> {
                    instruction.setRejectedBy(InstructionAuthorization.userRef(subject));
                    instruction.setRejectedAt(Instant.now());
                    instruction.setRejectionReason(request.reason());
                    return Map.of("reason", request.reason());
                });
    }

    public VersionedInstruction cancel(
            String instructionId,
            Subject subject,
            CancelInstructionRequest request,
            String bearerToken,
            String sessionId) {
        VersionedInstruction current = getCurrentOr404(instructionId);
        CashSettlementInstruction instruction = current.instruction().copy();
        if (instruction.status() == InstructionStatus.CANCELLED) {
            throw new InvalidStateTransitionException("instruction is already cancelled");
        }
        if (instruction.status() != InstructionStatus.DRAFT && instruction.status() != InstructionStatus.SUBMITTED) {
            throw new InvalidStateTransitionException("only DRAFT or SUBMITTED instructions can be cancelled");
        }
        String reason = request == null ? null : request.reason();
        Map<String, Object> details = reason == null ? Map.of() : Map.of("reason", reason);
        authorize(InstructionAction.CANCEL, subject, instruction, bearerToken, sessionId, true, details);
        instruction.setStatus(InstructionStatus.CANCELLED);
        instruction.setCancelledAt(Instant.now());
        return persistNewVersion(instruction, InstructionAction.CANCEL, subject, details, bearerToken, sessionId, true);
    }

    public VersionedInstruction suspend(String instructionId, Subject subject, String bearerToken, String sessionId) {
        return lifecycleTransition(instructionId, InstructionStatus.APPROVED, InstructionStatus.SUSPENDED,
                InstructionAction.SUSPEND, subject, bearerToken, sessionId, instruction -> {
                    instruction.setSuspendedBy(subject.userId());
                    instruction.setSuspendedAt(Instant.now());
                    return null;
                });
    }

    public VersionedInstruction reactivate(String instructionId, Subject subject, String bearerToken, String sessionId) {
        return lifecycleTransition(instructionId, InstructionStatus.SUSPENDED, InstructionStatus.APPROVED,
                InstructionAction.REACTIVATE, subject, bearerToken, sessionId, instruction -> {
                    instruction.setSuspendedBy(null);
                    instruction.setSuspendedAt(null);
                    return null;
                });
    }

    public VersionedInstruction use(
            String instructionId,
            Subject subject,
            UseInstructionRequest request,
            String bearerToken,
            String sessionId) {
        VersionedInstruction current = getCurrentOr404(instructionId);
        CashSettlementInstruction instruction = current.instruction().copy();
        if (instruction.status() != InstructionStatus.APPROVED) {
            throw new InvalidStateTransitionException("only APPROVED instructions can be used");
        }
        instruction.setUsageCount(instruction.usageCount() + 1);
        instruction.setLastUsedAt(Instant.now());
        Map<String, Object> useDetails = new LinkedHashMap<>();
        useDetails.put("payment_reference", request.paymentReference());
        useDetails.put("end_to_end_identification", request.endToEndIdentification());
        useDetails.put("currency", instruction.currency());
        Map<String, Object> authorization = authorize(
                InstructionAction.USE, subject, instruction, bearerToken, sessionId, true, useDetails);
        if (instruction.instructionType() == InstructionType.SINGLE_USE) {
            instruction.setStatus(InstructionStatus.USED);
            instruction.setUsedBy(request.paymentReference());
        }
        Map<String, Object> details = InstructionAuthorization.detailsWithAuthorization(useDetails, authorization);
        recordEvent(instruction, InstructionAction.USE, subject, details);
        return saveWithSecurityEvent(instruction, InstructionAction.USE, subject, details, false);
    }

    public VersionedInstruction releaseUse(
            String instructionId,
            Subject subject,
            ReleaseUseInstructionRequest request,
            String bearerToken,
            String sessionId) {
        VersionedInstruction current = getCurrentOr404(instructionId);
        CashSettlementInstruction instruction = current.instruction().copy();
        if (instruction.instructionType() != InstructionType.SINGLE_USE) {
            throw new InvalidStateTransitionException("only SINGLE_USE instructions support release");
        }
        if (instruction.status() != InstructionStatus.USED) {
            throw new InvalidStateTransitionException("only USED instructions can be released");
        }
        if (!request.paymentReference().equals(instruction.usedBy())) {
            throw new InvalidStateTransitionException("instruction used_by does not match the releasing payment");
        }
        Map<String, Object> releaseDetails = Map.of("payment_reference", request.paymentReference());
        Map<String, Object> authorization = authorize(
                InstructionAction.RELEASE_USE, subject, instruction, bearerToken, sessionId, true, releaseDetails);
        instruction.setStatus(InstructionStatus.APPROVED);
        instruction.setUsedBy(null);
        if (instruction.usageCount() > 0) {
            instruction.setUsageCount(instruction.usageCount() - 1);
        }
        Map<String, Object> details = InstructionAuthorization.detailsWithAuthorization(releaseDetails, authorization);
        recordEvent(instruction, InstructionAction.RELEASE_USE, subject, details);
        return saveWithSecurityEvent(instruction, InstructionAction.RELEASE_USE, subject, details, false);
    }

    @FunctionalInterface
    private interface LifecycleMutator {
        Map<String, Object> apply(CashSettlementInstruction instruction);
    }

    private VersionedInstruction lifecycleTransition(
            String instructionId,
            InstructionStatus requiredStatus,
            InstructionStatus nextStatus,
            InstructionAction action,
            Subject subject,
            String bearerToken,
            String sessionId,
            LifecycleMutator mutator) {
        VersionedInstruction current = getCurrentOr404(instructionId);
        CashSettlementInstruction instruction = current.instruction().copy();
        if (instruction.status() != requiredStatus) {
            throw new InvalidStateTransitionException("only " + requiredStatus + " instructions can "
                    + action.name().toLowerCase().replace('_', ' '));
        }
        Map<String, Object> authorization = authorize(
                action, subject, instruction, bearerToken, sessionId, true, null);
        instruction.setStatus(nextStatus);
        Map<String, Object> extra = mutator.apply(instruction);
        Map<String, Object> details = InstructionAuthorization.detailsWithAuthorization(extra, authorization);
        recordEvent(instruction, action, subject, details);
        return saveWithSecurityEvent(instruction, action, subject, details, false);
    }

    private VersionedInstruction persistNewVersion(
            CashSettlementInstruction instruction,
            InstructionAction action,
            Subject subject,
            Map<String, Object> details,
            String bearerToken,
            String sessionId,
            boolean skipAuthorize) {
        if (!skipAuthorize) {
            Map<String, Object> authorization = authorize(
                    action, subject, instruction, bearerToken, sessionId, true, details);
            details = InstructionAuthorization.detailsWithAuthorization(details, authorization);
        }
        recordEvent(instruction, action, subject, details);
        return saveWithSecurityEvent(instruction, action, subject, details, false);
    }

    private Map<String, Object> authorize(
            InstructionAction action,
            Subject subject,
            CashSettlementInstruction instruction,
            String bearerToken,
            String sessionId,
            boolean recordSecurityEvent,
            Map<String, Object> securityEventDetails) {
        serviceTokenHolder.ensureLoggedIn();
        PolicyDecision decision = authzClient.evaluateInstruction(
                action.name(),
                instruction.toOpaInstruction(),
                instruction.toOpaAccount(),
                serviceTokenHolder.token(),
                serviceTokenHolder.sessionId(),
                bearerToken,
                sessionId,
                subject);
        Map<String, Object> authorization = InstructionAuthorization.buildAuthorizationBlock(
                decision,
                subject,
                action,
                InstructionAuthorization.instructionResourceContext(instruction));
        if (!decision.allowed()) {
            if (recordSecurityEvent && shouldRecordSecurityEvent(subject)) {
                String eventId = securityEventRepository.allocateEventId(instruction.instructionId());
                InstructionSecurityEvent event = InstructionSecurityEvent.policyDenial(
                        action, subject, instruction, String.valueOf(authorization.get("summary")),
                        InstructionAuthorization.detailsWithAuthorization(securityEventDetails, authorization),
                        objectMapper);
                securityEventRepository.insert(event, eventId);
            }
            throw new PermissionDeniedException(String.valueOf(authorization.get("summary")));
        }
        return authorization;
    }

    @Transactional("instructionTransactionManager")
    VersionedInstruction saveWithSecurityEvent(
            CashSettlementInstruction instruction,
            InstructionAction action,
            Subject subject,
            Map<String, Object> details,
            boolean initial) {
        VersionedInstruction saved = initial
                ? repository.insertInitial(instruction)
                : repository.appendVersion(instruction);
        if (shouldRecordSecurityEvent(subject)) {
            String eventId = securityEventRepository.allocateEventId(instruction.instructionId());
            InstructionSecurityEvent event = InstructionSecurityEvent.authorizedAction(
                    action, subject, saved.instruction(), saved.versionNumber(), details, objectMapper);
            securityEventRepository.insert(event, eventId);
        }
        return saved;
    }

    private void recordEvent(
            CashSettlementInstruction instruction,
            InstructionAction action,
            Subject subject,
            Map<String, Object> details) {
        instruction.addLifecycleEvent(new LifecycleEvent(
                CashSettlementInstruction.newEventId(),
                action.name(),
                subject.userId(),
                Instant.now().toString(),
                details));
    }

    private void recordAuthorizedView(
            Subject subject,
            VersionedInstruction record,
            Map<String, Object> authorization) {
        if (!shouldRecordSecurityEvent(subject)) {
            return;
        }
        String eventId = securityEventRepository.allocateEventId(record.instruction().instructionId());
        Map<String, Object> details = InstructionAuthorization.detailsWithAuthorization(null, authorization);
        InstructionSecurityEvent event = InstructionSecurityEvent.authorizedAction(
                InstructionAction.VIEW,
                subject,
                record.instruction(),
                record.versionNumber(),
                details,
                objectMapper);
        securityEventRepository.insert(event, eventId);
    }

    private boolean tryAuthorizeView(
            Subject subject,
            VersionedInstruction record,
            String bearerToken,
            String sessionId) {
        try {
            boolean recordView = shouldRecordViewSecurityEvent(subject);
            Map<String, Object> authorization = authorize(
                    InstructionAction.VIEW, subject, record.instruction(), bearerToken, sessionId, recordView, null);
            if (recordView) {
                recordAuthorizedView(subject, record, authorization);
            }
            return true;
        } catch (PermissionDeniedException ex) {
            return false;
        }
    }

    private boolean shouldRecordSecurityEvent(Subject subject) {
        return !properties.securityEventExcludedUserIdSet().contains(subject.userId());
    }

    private boolean shouldRecordViewSecurityEvent(Subject subject) {
        if (properties.securityEventViewExcludedUserIdSet().contains(subject.userId())) {
            return false;
        }
        return shouldRecordSecurityEvent(subject);
    }

    private VersionedInstruction getCurrentOr404(String instructionId) {
        try {
            return repository.getCurrent(instructionId);
        } catch (InstructionNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (ConcurrentModificationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }
}
