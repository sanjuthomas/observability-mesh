package com.observabilitymesh.payment.metrics;

import com.observabilitymesh.payment.model.PaymentAction;
import com.observabilitymesh.payment.model.SecurityEventSeverity;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import org.springframework.stereotype.Component;

@Component
public class PaymentSecurityEventMetrics {

    static final String METRIC_NAME = "payment_security_events_total";

    private final LongCounter counter;

    public PaymentSecurityEventMetrics(OpenTelemetry openTelemetry) {
        this.counter = openTelemetry.getMeter("com.observabilitymesh.payment")
                .counterBuilder(METRIC_NAME)
                .setDescription("Payment security events emitted to the audit log")
                .build();
    }

    public void recordPolicyAlert(PaymentAction action, SecurityEventSeverity severity) {
        if (action == null || severity != SecurityEventSeverity.ALERT) {
            return;
        }
        counter.add(1, Attributes.builder()
                .put("action", action.name())
                .put("severity", severity.name())
                .put("outcome", "failure")
                .build());
    }
}
