package com.observabilitymesh.payment.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.model.PaymentConstants;
import com.observabilitymesh.payment.model.VersionedPayment;
import com.observabilitymesh.payment.repo.PaymentNotFoundException;
import com.observabilitymesh.payment.repo.PaymentRepository;
import com.observabilitymesh.payment.service.SubjectAccess;
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

    private final PaymentRepository repository;
    private final RequestSubjectResolver subjectResolver;

    public UiController(PaymentRepository repository, RequestSubjectResolver subjectResolver) {
        this.repository = repository;
        this.subjectResolver = subjectResolver;
    }

    @GetMapping(value = {"/ui", "/ui/"})
    public ResponseEntity<ClassPathResource> index() {
        return html("static/index.html");
    }

    @GetMapping("/ui/payments/{paymentId}")
    public ResponseEntity<ClassPathResource> paymentDetail(@PathVariable String paymentId) {
        return html("static/payment.html");
    }

    @GetMapping("/api/ui/payments")
    public Map<String, Object> listPayments(
            @RequestParam(required = false) String status,
            @RequestParam(name = "owning_lob", required = false) String owningLob,
            @RequestParam(name = "instruction_id", required = false) String instructionId,
            @RequestParam(defaultValue = "200") int limit,
            HttpServletRequest request) {
        requireAdmin(request);
        List<VersionedPayment> records = repository.listCurrent(instructionId, status, Math.min(limit, 500), true);
        if (owningLob != null && !owningLob.isBlank()) {
            records = records.stream().filter(r -> owningLob.equals(r.payment().owningLob())).toList();
        }
        List<Map<String, Object>> payments = records.stream().map(this::toUiMap).toList();
        return Map.of("payments", payments, "count", payments.size());
    }

    @GetMapping("/api/ui/payments/{paymentId}")
    public Map<String, Object> getPayment(@PathVariable String paymentId, HttpServletRequest request) {
        requireAdmin(request);
        try {
            return Map.of("payment", toUiMap(repository.getCurrent(paymentId)));
        } catch (PaymentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "payment not found: " + paymentId);
        }
    }

    private void requireAdmin(HttpServletRequest request) {
        Subject subject = subjectResolver.resolveActor(request);
        SubjectAccess.requirePlatformAdmin(subject);
    }

    private Map<String, Object> toUiMap(VersionedPayment record) {
        Map<String, Object> map = new LinkedHashMap<>();
        var payment = record.payment();
        map.put("payment_id", payment.paymentId());
        map.put("instruction_id", payment.instructionId());
        map.put("instruction_version", payment.instructionVersion());
        map.put("status", payment.status().name());
        map.put("amount", payment.amount());
        map.put("currency", payment.currency());
        map.put("value_date", payment.valueDate());
        map.put("owning_lob", payment.owningLob());
        map.put("instruction_type", payment.instructionType());
        map.put("created_by", payment.createdBy());
        map.put("submitted_by", payment.submittedBy());
        map.put("approved_by", payment.approvedBy());
        map.put("rejected_by", payment.rejectedBy());
        map.put("cancelled_by", payment.cancelledBy());
        map.put("rejection_reason", payment.rejectionReason());
        map.put("cancellation_reason", payment.cancellationReason());
        map.put("created_at", payment.createdAt());
        map.put("updated_at", payment.updatedAt());
        map.put("submitted_at", payment.submittedAt());
        map.put("approved_at", payment.approvedAt());
        map.put("rejected_at", payment.rejectedAt());
        map.put("cancelled_at", payment.cancelledAt());
        map.put("lifecycle_events", payment.lifecycleEvents());
        map.put("version_number", record.versionNumber());
        map.put("in", record.validIn());
        map.put("out", record.validOut() == null ? PaymentConstants.CURRENT_OUT : record.validOut());
        return map;
    }

    private static ResponseEntity<ClassPathResource> html(String path) {
        return ResponseEntity.ok()
                .headers(NO_CACHE)
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource(path));
    }
}
