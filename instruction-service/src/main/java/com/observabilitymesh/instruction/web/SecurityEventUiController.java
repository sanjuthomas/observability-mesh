package com.observabilitymesh.instruction.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.instruction.config.InstructionProperties;
import com.observabilitymesh.instruction.security.SecurityEventRepository;
import com.observabilitymesh.instruction.service.SubjectAccess;
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
public class SecurityEventUiController {

    private static final HttpHeaders NO_CACHE = new HttpHeaders();

    static {
        NO_CACHE.setCacheControl("no-cache");
    }

    private final SecurityEventRepository securityEventRepository;
    private final RequestSubjectResolver subjectResolver;
    private final InstructionProperties properties;

    public SecurityEventUiController(
            SecurityEventRepository securityEventRepository,
            RequestSubjectResolver subjectResolver,
            InstructionProperties properties) {
        this.securityEventRepository = securityEventRepository;
        this.subjectResolver = subjectResolver;
        this.properties = properties;
    }

    @GetMapping(value = {"/ui/security-events", "/ui/security-events/"})
    public ResponseEntity<ClassPathResource> index() {
        return html("static/security_events/index.html");
    }

    @GetMapping("/ui/security-events/events/{eventId}")
    public ResponseEntity<ClassPathResource> eventDetail(@PathVariable String eventId) {
        return html("static/security_events/event.html");
    }

    @GetMapping("/api/ui/security-events")
    public Map<String, Object> listEvents(
            @RequestParam(defaultValue = "0") int limit,
            HttpServletRequest request) {
        requireAdmin(request);
        int effectiveLimit = limit <= 0 ? properties.uiInitialSecurityEventLimit() : Math.min(limit, 1000);
        List<Map<String, Object>> events = securityEventRepository.listRecent(effectiveLimit);
        return Map.of("events", events, "count", events.size());
    }

    @GetMapping("/api/ui/security-events/{eventId}")
    public Map<String, Object> getEvent(@PathVariable String eventId, HttpServletRequest request) {
        requireAdmin(request);
        Map<String, Object> event = securityEventRepository.findByEventId(eventId);
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "security event not found: " + eventId);
        }
        return Map.of("event", event);
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
