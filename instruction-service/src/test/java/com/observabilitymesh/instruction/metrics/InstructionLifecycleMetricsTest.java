package com.observabilitymesh.instruction.metrics;

import com.observabilitymesh.instruction.model.InstructionAction;
import com.observabilitymesh.instruction.model.InstructionStatus;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionLifecycleMetricsTest {

    private InMemoryMetricReader metricReader;
    private InstructionLifecycleMetrics metrics;

    @BeforeEach
    void setUp() {
        metricReader = InMemoryMetricReader.create();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder()
                        .registerMetricReader(metricReader)
                        .build())
                .build();
        metrics = new InstructionLifecycleMetrics(openTelemetry);
    }

    @Test
    void recordsTransitionWithActionStatusAndLob() {
        metrics.recordTransition(InstructionAction.SUBMIT, InstructionStatus.SUBMITTED, "DESK_RATES");

        assertThat(count("SUBMIT", "SUBMITTED", "DESK_RATES")).isEqualTo(1);
    }

    @Test
    void ignoresViewAction() {
        metrics.recordTransition(InstructionAction.VIEW, InstructionStatus.APPROVED, "FICC");

        assertThat(metricReader.collectAllMetrics()).isEmpty();
    }

    private long count(String action, String status, String owningLob) {
        return metricReader.collectAllMetrics().stream()
                .filter(metric -> InstructionLifecycleMetrics.METRIC_NAME.equals(metric.getName()))
                .map(MetricData::getLongSumData)
                .flatMap(sum -> sum.getPoints().stream())
                .filter(point -> action.equals(point.getAttributes().get(
                        io.opentelemetry.api.common.AttributeKey.stringKey("action"))))
                .filter(point -> status.equals(point.getAttributes().get(
                        io.opentelemetry.api.common.AttributeKey.stringKey("status"))))
                .filter(point -> owningLob.equals(point.getAttributes().get(
                        io.opentelemetry.api.common.AttributeKey.stringKey("owning_lob"))))
                .mapToLong(point -> point.getValue())
                .sum();
    }
}
