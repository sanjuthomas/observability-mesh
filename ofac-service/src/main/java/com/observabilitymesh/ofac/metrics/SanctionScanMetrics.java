package com.observabilitymesh.ofac.metrics;

import com.observabilitymesh.ofac.model.OfacScanResult;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SanctionScanMetrics {

    static final String METRIC_NAME = "sanction_scan_completed_total";
    static final String DURATION_LE_60S = "60s";
    static final Duration LATENCY_BUDGET = Duration.ofSeconds(60);

    private final LongCounter counter;

    public SanctionScanMetrics(OpenTelemetry openTelemetry) {
        this.counter = openTelemetry.getMeter("com.observabilitymesh.ofac")
                .counterBuilder(METRIC_NAME)
                .setDescription("OFAC sanction scans completed")
                .build();
    }

    public void recordCompletion(OfacScanResult result, Duration durationSinceRequest) {
        if (result == null || durationSinceRequest == null || durationSinceRequest.isNegative()) {
            return;
        }
        AttributesBuilder attributes = Attributes.builder().put("result", result.name());
        if (withinLatencyBudget(result, durationSinceRequest)) {
            attributes.put("duration_le", DURATION_LE_60S);
        }
        counter.add(1, attributes.build());
    }

    static boolean withinLatencyBudget(OfacScanResult result, Duration durationSinceRequest) {
        return isDefinitive(result) && durationSinceRequest.compareTo(LATENCY_BUDGET) <= 0;
    }

    static boolean isDefinitive(OfacScanResult result) {
        return result == OfacScanResult.PASSED || result == OfacScanResult.FAILED;
    }
}
