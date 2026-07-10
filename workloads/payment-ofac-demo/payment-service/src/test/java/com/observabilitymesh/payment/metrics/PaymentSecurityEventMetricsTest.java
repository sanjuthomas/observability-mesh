package com.observabilitymesh.payment.metrics;

import com.observabilitymesh.payment.model.PaymentAction;
import com.observabilitymesh.payment.model.SecurityEventSeverity;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSecurityEventMetricsTest {

    private InMemoryMetricReader metricReader;
    private PaymentSecurityEventMetrics metrics;

    @BeforeEach
    void setUp() {
        metricReader = InMemoryMetricReader.create();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder()
                        .registerMetricReader(metricReader)
                        .build())
                .build();
        metrics = new PaymentSecurityEventMetrics(openTelemetry);
    }

    @Test
    void recordsPaymentApprovalAlert() {
        metrics.recordPolicyAlert(PaymentAction.APPROVE, SecurityEventSeverity.ALERT);

        assertThat(count("APPROVE", "ALERT")).isEqualTo(1);
    }

    @Test
    void ignoresNonAlertSeverity() {
        metrics.recordPolicyAlert(PaymentAction.APPROVE, SecurityEventSeverity.INFO);

        assertThat(metricReader.collectAllMetrics()).isEmpty();
    }

    @Test
    void ignoresNullAction() {
        metrics.recordPolicyAlert(null, SecurityEventSeverity.ALERT);

        assertThat(metricReader.collectAllMetrics()).isEmpty();
    }

    private long count(String action, String severity) {
        return metricReader.collectAllMetrics().stream()
                .filter(metric -> PaymentSecurityEventMetrics.METRIC_NAME.equals(metric.getName()))
                .map(MetricData::getLongSumData)
                .flatMap(sum -> sum.getPoints().stream())
                .filter(point -> action.equals(point.getAttributes().get(
                        io.opentelemetry.api.common.AttributeKey.stringKey("action"))))
                .filter(point -> severity.equals(point.getAttributes().get(
                        io.opentelemetry.api.common.AttributeKey.stringKey("severity"))))
                .mapToLong(point -> point.getValue())
                .sum();
    }
}
