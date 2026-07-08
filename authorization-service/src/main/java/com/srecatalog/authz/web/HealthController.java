package com.srecatalog.authz.web;

import com.srecatalog.authz.opa.OpaClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private static final int MINIMUM_POLICIES = 11;

    private final OpaClient opaClient;

    public HealthController(OpaClient opaClient) {
        this.opaClient = opaClient;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> opaStatus = opaClient.policyHealth(MINIMUM_POLICIES);
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("opa", opaStatus);
        String overall = Boolean.TRUE.equals(opaStatus.get("ok")) ? "UP" : "DEGRADED";
        return Map.of("status", overall, "components", components);
    }
}
