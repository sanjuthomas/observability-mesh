package com.observabilitymesh.payment.metrics;

import com.observabilitymesh.payment.model.PaymentAction;
import com.observabilitymesh.payment.model.PaymentStatus;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentLifecycleMetricsTest {

    private InMemoryMetricReader metricReader;
    private PaymentLifecycleMetrics metrics;

    @BeforeEach
    void setUp() {
        metricReader = InMemoryMetricReader.create();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder()
                        .registerMetricReader(metricReader)
                        .build())
                .build();
        metrics = new PaymentLifecycleMetrics(openTelemetry);
    }

    @Test
    void recordsTransitionWithActionStatusAndLob() {
        metrics.recordTransition(PaymentAction.APPROVE, PaymentStatus.APPROVED, "FICC");

        assertThat(count("APPROVE", "APPROVED", "FICC")).isEqualTo(1);
    }

    @Test
    void ignoresNullActionOrStatus() {
        metrics.recordTransition(null, PaymentStatus.DRAFT, "FICC");
        metrics.recordTransition(PaymentAction.CREATE, null, "FICC");

        assertThat(metricReader.collectAllMetrics()).isEmpty();
    }

    private long count(String action, String status, String owningLob) {
        return metricReader.collectAllMetrics().stream()
                .filter(metric -> PaymentLifecycleMetrics.METRIC_NAME.equals(metric.getName()))
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
