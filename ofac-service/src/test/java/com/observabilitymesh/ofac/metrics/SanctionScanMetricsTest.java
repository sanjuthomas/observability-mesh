package com.observabilitymesh.ofac.metrics;

import com.observabilitymesh.ofac.model.OfacScanResult;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SanctionScanMetricsTest {

    private InMemoryMetricReader metricReader;
    private SanctionScanMetrics metrics;

    @BeforeEach
    void setUp() {
        metricReader = InMemoryMetricReader.create();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder()
                        .registerMetricReader(metricReader)
                        .build())
                .build();
        metrics = new SanctionScanMetrics(openTelemetry);
    }

    @Test
    void recordsFastDefinitiveScanWithDurationLeLabel() {
        metrics.recordCompletion(OfacScanResult.PASSED, Duration.ofSeconds(45));

        assertThat(count("PASSED", "60s")).isEqualTo(1);
        assertThat(count("PASSED", null)).isZero();
    }

    @Test
    void recordsSlowDefinitiveScanWithoutDurationLeLabel() {
        metrics.recordCompletion(OfacScanResult.FAILED, Duration.ofSeconds(90));

        assertThat(count("FAILED", null)).isEqualTo(1);
        assertThat(count("FAILED", "60s")).isZero();
    }

    @Test
    void recordsUnableToDetermineWithoutDurationLeLabel() {
        metrics.recordCompletion(OfacScanResult.UNABLE_TO_DETERMINE, Duration.ofSeconds(10));

        assertThat(count("UNABLE_TO_DETERMINE", null)).isEqualTo(1);
        assertThat(count("UNABLE_TO_DETERMINE", "60s")).isZero();
    }

    @Test
    void ignoresNullResultOrDuration() {
        metrics.recordCompletion(null, Duration.ofSeconds(1));
        metrics.recordCompletion(OfacScanResult.PASSED, null);
        metrics.recordCompletion(OfacScanResult.PASSED, Duration.ofSeconds(-1));

        assertThat(metricReader.collectAllMetrics()).isEmpty();
    }

    @Test
    void withinLatencyBudgetTreatsSixtySecondsAsGood() {
        assertThat(SanctionScanMetrics.withinLatencyBudget(OfacScanResult.PASSED, Duration.ofSeconds(60)))
                .isTrue();
        assertThat(SanctionScanMetrics.withinLatencyBudget(OfacScanResult.PASSED, Duration.ofSeconds(61)))
                .isFalse();
    }

    private long count(String result, String durationLe) {
        return metricReader.collectAllMetrics().stream()
                .filter(metric -> SanctionScanMetrics.METRIC_NAME.equals(metric.getName()))
                .map(MetricData::getLongSumData)
                .flatMap(sum -> sum.getPoints().stream())
                .filter(point -> result.equals(point.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("result"))))
                .filter(point -> durationLe == null
                        ? point.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("duration_le")) == null
                        : durationLe.equals(point.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("duration_le"))))
                .mapToLong(point -> point.getValue())
                .sum();
    }
}
