package com.observabilitymesh.sloprovisioner.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.sloprovisioner.service.ProvisionDocumentNotFoundException;
import com.observabilitymesh.sloprovisioner.service.ProvisionUiService;
import com.observabilitymesh.sloprovisioner.service.SubjectAccess;
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

    private final ProvisionUiService provisionUiService;
    private final RequestSubjectResolver subjectResolver;

    public UiController(ProvisionUiService provisionUiService, RequestSubjectResolver subjectResolver) {
        this.provisionUiService = provisionUiService;
        this.subjectResolver = subjectResolver;
    }

    @GetMapping(value = {"/ui", "/ui/"})
    public ResponseEntity<ClassPathResource> index() {
        return html("static/index.html");
    }

    @GetMapping("/ui/slos/{name}")
    public ResponseEntity<ClassPathResource> sloDetail(@PathVariable String name) {
        return html("static/slo.html");
    }

    @GetMapping("/ui/slis/{name}")
    public ResponseEntity<ClassPathResource> sliDetail(@PathVariable String name) {
        return html("static/sli.html");
    }

    @GetMapping("/api/ui/slos")
    public Map<String, Object> listSlos(
            @RequestParam(name = "provision_status", defaultValue = "ALL") String provisionStatus,
            @RequestParam(defaultValue = "200") int limit,
            HttpServletRequest request) {
        requireAdmin(request);
        List<Map<String, Object>> slos = provisionUiService.listSlos(provisionStatus, limit);
        return Map.of("slos", slos, "count", slos.size());
    }

    @GetMapping("/api/ui/slos/{name}")
    public Map<String, Object> getSlo(@PathVariable String name, HttpServletRequest request) {
        requireAdmin(request);
        try {
            return Map.of("slo", provisionUiService.getSlo(name));
        } catch (ProvisionDocumentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @GetMapping("/api/ui/slis")
    public Map<String, Object> listSlis(
            @RequestParam(defaultValue = "200") int limit, HttpServletRequest request) {
        requireAdmin(request);
        List<Map<String, Object>> slis = provisionUiService.listSlis(limit);
        return Map.of("slis", slis, "count", slis.size());
    }

    @GetMapping("/api/ui/slis/{name}")
    public Map<String, Object> getSli(@PathVariable String name, HttpServletRequest request) {
        requireAdmin(request);
        try {
            return Map.of("sli", provisionUiService.getSli(name));
        } catch (ProvisionDocumentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
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
