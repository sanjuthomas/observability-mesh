package com.srecatalog.instruction.web;

import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.common.model.Subject;
import com.srecatalog.instruction.model.InstructionConstants;
import com.srecatalog.instruction.model.VersionedInstruction;
import com.srecatalog.instruction.repo.InstructionNotFoundException;
import com.srecatalog.instruction.repo.InstructionRepository;
import com.srecatalog.instruction.service.SubjectAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class UiController {

    private static final HttpHeaders NO_CACHE = new HttpHeaders();

    static {
        NO_CACHE.setCacheControl("no-cache");
    }

    private final InstructionRepository repository;
    private final RequestSubjectResolver subjectResolver;

    public UiController(InstructionRepository repository, RequestSubjectResolver subjectResolver) {
        this.repository = repository;
        this.subjectResolver = subjectResolver;
    }

    @GetMapping(value = {"/ui", "/ui/"})
    public ResponseEntity<ClassPathResource> index() {
        return html("static/index.html");
    }

    @GetMapping("/ui/instructions/{instructionId}")
    public ResponseEntity<ClassPathResource> instructionDetail(@PathVariable String instructionId) {
        return html("static/instruction.html");
    }

    @GetMapping("/api/ui/instructions")
    public Map<String, Object> listInstructions(
            @RequestParam(required = false) String status,
            @RequestParam(name = "owning_lob", required = false) String owningLob,
            @RequestParam(defaultValue = "200") int limit,
            HttpServletRequest request) {
        requireAdmin(request);
        List<VersionedInstruction> records = repository.listCurrent(owningLob, status, Math.min(limit, 500));
        List<Map<String, Object>> instructions = records.stream().map(this::toUiMap).toList();
        return Map.of("instructions", instructions, "count", instructions.size());
    }

    @GetMapping("/api/ui/instructions/{instructionId}")
    public Map<String, Object> getInstruction(@PathVariable String instructionId, HttpServletRequest request) {
        requireAdmin(request);
        try {
            return Map.of("instruction", toUiMap(repository.getCurrent(instructionId)));
        } catch (InstructionNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "instruction not found: " + instructionId);
        }
    }

    private void requireAdmin(HttpServletRequest request) {
        Subject subject = subjectResolver.resolveActor(request);
        SubjectAccess.requirePlatformAdmin(subject);
    }

    private Map<String, Object> toUiMap(VersionedInstruction record) {
        Map<String, Object> map = new LinkedHashMap<>();
        var instruction = record.instruction();
        map.put("instruction_id", instruction.instructionId());
        map.put("version_number", record.versionNumber());
        map.put("status", instruction.status().name());
        map.put("instruction_type", instruction.instructionType().name());
        map.put("owning_lob", instruction.owningLob());
        map.put("wire_scope", instruction.wireScope().name());
        map.put("currency", instruction.currency());
        map.put("funding_account", instruction.fundingAccount());
        map.put("debtor", instruction.debtor());
        map.put("creditor", instruction.creditor());
        map.put("created_by", instruction.createdBy());
        map.put("approved_by", instruction.approvedBy());
        map.put("rejected_by", instruction.rejectedBy());
        map.put("rejection_reason", instruction.rejectionReason());
        map.put("created_at", instruction.createdAt());
        map.put("updated_at", instruction.updatedAt());
        map.put("submitted_at", instruction.submittedAt());
        map.put("approved_at", instruction.approvedAt());
        map.put("rejected_at", instruction.rejectedAt());
        map.put("cancelled_at", instruction.cancelledAt());
        map.put("suspended_by", instruction.suspendedBy());
        map.put("suspended_at", instruction.suspendedAt());
        map.put("last_used_at", instruction.lastUsedAt());
        map.put("usage_count", instruction.usageCount());
        map.put("used_by", instruction.usedBy());
        map.put("lifecycle_events", instruction.lifecycleEvents());
        map.put("in", record.validIn());
        map.put("out", record.validOut() == null ? InstructionConstants.CURRENT_OUT : record.validOut());
        return map;
    }

    private static ResponseEntity<ClassPathResource> html(String path) {
        return ResponseEntity.ok()
                .headers(NO_CACHE)
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource(path));
    }
}
