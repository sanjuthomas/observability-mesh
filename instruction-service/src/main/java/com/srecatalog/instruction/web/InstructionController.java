package com.srecatalog.instruction.web;

import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.common.model.Subject;
import com.srecatalog.instruction.config.InstructionProperties;
import com.srecatalog.instruction.service.InstructionService;
import com.srecatalog.instruction.service.SubjectAccess;
import com.srecatalog.instruction.web.dto.CancelInstructionRequest;
import com.srecatalog.instruction.web.dto.CreateInstructionRequest;
import com.srecatalog.instruction.web.dto.InstructionEligibleApproversResponse;
import com.srecatalog.instruction.web.dto.InstructionResponse;
import com.srecatalog.instruction.web.dto.RejectInstructionRequest;
import com.srecatalog.instruction.web.dto.ReleaseUseInstructionRequest;
import com.srecatalog.instruction.web.dto.UseInstructionRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/instructions")
public class InstructionController {

    private final InstructionService instructionService;
    private final RequestSubjectResolver subjectResolver;
    private final InstructionProperties properties;

    public InstructionController(
            InstructionService instructionService,
            RequestSubjectResolver subjectResolver,
            InstructionProperties properties) {
        this.instructionService = instructionService;
        this.subjectResolver = subjectResolver;
        this.properties = properties;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstructionResponse create(@Valid @RequestBody CreateInstructionRequest request, HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return InstructionResponseMapper.toResponse(instructionService.create(
                request,
                subject,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    @GetMapping
    public List<InstructionResponse> list(
            @RequestParam(required = false) String owningLob,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return instructionService.list(
                        subject,
                        owningLob,
                        status,
                        Math.min(limit, 500),
                        subjectResolver.bearerToken(httpRequest),
                        subjectResolver.sessionId(httpRequest))
                .stream()
                .map(InstructionResponseMapper::toResponse)
                .toList();
    }

    @GetMapping("/{instructionId}")
    public InstructionResponse get(@PathVariable String instructionId, HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return InstructionResponseMapper.toResponse(instructionService.get(
                instructionId,
                subject,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    @GetMapping("/{instructionId}/versions")
    public List<InstructionResponse> listVersions(@PathVariable String instructionId, HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return instructionService.listVersions(
                        instructionId,
                        subject,
                        subjectResolver.bearerToken(httpRequest),
                        subjectResolver.sessionId(httpRequest))
                .stream()
                .map(InstructionResponseMapper::toResponse)
                .toList();
    }

    @PutMapping("/{instructionId}")
    public InstructionResponse update(
            @PathVariable String instructionId,
            @Valid @RequestBody CreateInstructionRequest request,
            HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return InstructionResponseMapper.toResponse(instructionService.update(
                instructionId,
                request,
                subject,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    @PostMapping("/{instructionId}/eligible-approvers")
    public InstructionEligibleApproversResponse eligibleApprovers(
            @PathVariable String instructionId,
            HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        SubjectAccess.requireCompliance(subject, properties);
        Map<String, Object> data = instructionService.eligibleApprovers(instructionId);
        return new InstructionEligibleApproversResponse(
                stringValue(data, "instruction_id"),
                stringValue(data, "instruction_status"),
                stringValue(data, "instruction_type"),
                stringValue(data, "owning_lob"),
                stringValue(data, "created_by_user_id"),
                stringValue(data, "created_by_title"),
                stringValue(data, "evaluated_at"),
                listValue(data, "eligible"),
                listValue(data, "prospective_eligible"),
                intValue(data, "candidates_evaluated"),
                stringValue(data, "approval_blocked_reason"));
    }

    @PostMapping("/{instructionId}/submit")
    public InstructionResponse submit(@PathVariable String instructionId, HttpServletRequest httpRequest) {
        return lifecycle(instructionId, httpRequest, (id, subject, token, session) ->
                instructionService.submit(id, subject, token, session));
    }

    @PostMapping("/{instructionId}/approve")
    public InstructionResponse approve(@PathVariable String instructionId, HttpServletRequest httpRequest) {
        return lifecycle(instructionId, httpRequest, (id, subject, token, session) ->
                instructionService.approve(id, subject, token, session));
    }

    @PostMapping("/{instructionId}/reject")
    public InstructionResponse reject(
            @PathVariable String instructionId,
            @Valid @RequestBody RejectInstructionRequest request,
            HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return InstructionResponseMapper.toResponse(instructionService.reject(
                instructionId,
                subject,
                request,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    @PostMapping("/{instructionId}/cancel")
    public InstructionResponse cancel(
            @PathVariable String instructionId,
            @RequestBody(required = false) CancelInstructionRequest request,
            HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return InstructionResponseMapper.toResponse(instructionService.cancel(
                instructionId,
                subject,
                request,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    @PostMapping("/{instructionId}/suspend")
    public InstructionResponse suspend(@PathVariable String instructionId, HttpServletRequest httpRequest) {
        return lifecycle(instructionId, httpRequest, (id, subject, token, session) ->
                instructionService.suspend(id, subject, token, session));
    }

    @PostMapping("/{instructionId}/reactivate")
    public InstructionResponse reactivate(@PathVariable String instructionId, HttpServletRequest httpRequest) {
        return lifecycle(instructionId, httpRequest, (id, subject, token, session) ->
                instructionService.reactivate(id, subject, token, session));
    }

    @PostMapping("/{instructionId}/use")
    public InstructionResponse use(
            @PathVariable String instructionId,
            @Valid @RequestBody UseInstructionRequest request,
            HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return InstructionResponseMapper.toResponse(instructionService.use(
                instructionId,
                subject,
                request,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    @PostMapping("/{instructionId}/release-use")
    public InstructionResponse releaseUse(
            @PathVariable String instructionId,
            @Valid @RequestBody ReleaseUseInstructionRequest request,
            HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return InstructionResponseMapper.toResponse(instructionService.releaseUse(
                instructionId,
                subject,
                request,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    private InstructionResponse lifecycle(
            String instructionId,
            HttpServletRequest httpRequest,
            LifecycleHandler handler) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return InstructionResponseMapper.toResponse(handler.handle(
                instructionId,
                subject,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    @FunctionalInterface
    private interface LifecycleHandler {
        com.srecatalog.instruction.model.VersionedInstruction handle(
                String instructionId, Subject subject, String bearerToken, String sessionId);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }

    private static String stringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
