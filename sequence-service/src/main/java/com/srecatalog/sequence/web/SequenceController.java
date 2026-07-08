package com.srecatalog.sequence.web;

import com.srecatalog.sequence.model.NextSecurityEventSequenceRequest;
import com.srecatalog.sequence.model.NextSecurityEventSequenceResponse;
import com.srecatalog.sequence.model.NextSequenceRequest;
import com.srecatalog.sequence.model.NextSequenceResponse;
import com.srecatalog.sequence.service.SequenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class SequenceController {

    private final SequenceService sequenceService;

    public SequenceController(SequenceService sequenceService) {
        this.sequenceService = sequenceService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/api/v1/sequences/next")
    public NextSequenceResponse next(@Valid @RequestBody NextSequenceRequest request) {
        return sequenceService.next(request);
    }

    @PostMapping("/api/v1/sequences/security-events/next")
    public NextSecurityEventSequenceResponse nextSecurityEvent(
            @Valid @RequestBody NextSecurityEventSequenceRequest request) {
        return sequenceService.nextSecurityEvent(request);
    }
}
