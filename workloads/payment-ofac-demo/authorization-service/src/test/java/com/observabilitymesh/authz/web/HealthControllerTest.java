package com.observabilitymesh.authz.web;

import com.observabilitymesh.authz.opa.OpaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock OpaClient opaClient;
    @InjectMocks HealthController controller;

    @Test
    void healthReportsUpWhenOpaHealthy() {
        when(opaClient.policyHealth(anyInt())).thenReturn(Map.of("ok", true, "policy_count", 11));

        Map<String, Object> health = controller.health();

        assertThat(health.get("status")).isEqualTo("UP");
        assertThat(health.get("components")).isInstanceOf(Map.class);
    }

    @Test
    void healthReportsDegradedWhenOpaUnhealthy() {
        when(opaClient.policyHealth(anyInt())).thenReturn(Map.of("ok", false, "detail", "down"));

        Map<String, Object> health = controller.health();

        assertThat(health.get("status")).isEqualTo("DEGRADED");
    }
}
