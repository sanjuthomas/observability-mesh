package com.observabilitymesh.ofac.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.ofac.model.OfacScanRequestView;
import com.observabilitymesh.ofac.repo.OfacScanRequestNotFoundException;
import com.observabilitymesh.ofac.repo.OfacScanRequestRepository;
import com.observabilitymesh.ofac.service.SubjectAccess;
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

import java.util.List;
import java.util.Map;

@RestController
public class UiController {

    private static final HttpHeaders NO_CACHE = new HttpHeaders();

    static {
        NO_CACHE.setCacheControl("no-cache");
    }

    private final OfacScanRequestRepository repository;
    private final RequestSubjectResolver subjectResolver;

    public UiController(OfacScanRequestRepository repository, RequestSubjectResolver subjectResolver) {
        this.repository = repository;
        this.subjectResolver = subjectResolver;
    }

    @GetMapping(value = {"/ui", "/ui/"})
    public ResponseEntity<ClassPathResource> index() {
        return html("static/index.html");
    }

    @GetMapping("/ui/scan-requests/{paymentId}")
    public ResponseEntity<ClassPathResource> scanRequestDetail(@PathVariable String paymentId) {
        return html("static/scan-request.html");
    }

    @GetMapping("/api/ui/scan-requests")
    public Map<String, Object> listScanRequests(
            @RequestParam(name = "owning_lob", required = false) String owningLob,
            @RequestParam(name = "lifecycle_status", required = false) String lifecycleStatus,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "200") int limit,
            HttpServletRequest request) {
        requireAdmin(request);
        List<OfacScanRequestView> records = repository.listCurrent(owningLob, lifecycleStatus, result, limit);
        List<Map<String, Object>> scanRequests = records.stream().map(OfacScanRequestView::toUiMap).toList();
        return Map.of("scan_requests", scanRequests, "count", scanRequests.size());
    }

    @GetMapping("/api/ui/scan-requests/{paymentId}")
    public Map<String, Object> getScanRequest(
            @PathVariable String paymentId,
            @RequestParam(name = "payment_version") int paymentVersion,
            HttpServletRequest request) {
        requireAdmin(request);
        try {
            return Map.of("scan_request", repository.getCurrent(paymentId, paymentVersion).toUiMap());
        } catch (OfacScanRequestNotFoundException ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "scan request not found for payment " + paymentId + " version " + paymentVersion);
        }
    }

    private void requireAdmin(HttpServletRequest request) {
        Subject subject = subjectResolver.resolveActor(request);
        SubjectAccess.requirePlatformAdmin(subject);
    }

    private static ResponseEntity<ClassPathResource> html(String path) {
        return ResponseEntity.ok()
                .headers(NO_CACHE)
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource(path));
    }
}
