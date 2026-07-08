package com.observabilitymesh.ofac.metrics;

import com.observabilitymesh.ofac.model.OfacScanResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SanctionScanMetrics {

    static final String METRIC_NAME = "sanction_scan_completed_total";
    static final String DURATION_LE_60S = "60s";
    static final Duration LATENCY_BUDGET = Duration.ofSeconds(60);

    private final MeterRegistry registry;

    public SanctionScanMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordCompletion(OfacScanResult result, Duration durationSinceRequest) {
        if (result == null || durationSinceRequest == null || durationSinceRequest.isNegative()) {
            return;
        }
        if (withinLatencyBudget(result, durationSinceRequest)) {
            counter(result.name(), DURATION_LE_60S).increment();
        } else {
            counter(result.name(), null).increment();
        }
    }

    private Counter counter(String result, String durationLe) {
        if (durationLe == null) {
            return Counter.builder(METRIC_NAME)
                    .tag("result", result)
                    .register(registry);
        }
        return Counter.builder(METRIC_NAME)
                .tag("result", result)
                .tag("duration_le", durationLe)
                .register(registry);
    }

    static boolean withinLatencyBudget(OfacScanResult result, Duration durationSinceRequest) {
        return isDefinitive(result) && durationSinceRequest.compareTo(LATENCY_BUDGET) <= 0;
    }

    static boolean isDefinitive(OfacScanResult result) {
        return result == OfacScanResult.PASSED || result == OfacScanResult.FAILED;
    }
}
