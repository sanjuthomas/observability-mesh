package com.observabilitymesh.payment.metrics;

import com.observabilitymesh.payment.model.PaymentAction;
import com.observabilitymesh.payment.model.PaymentStatus;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import org.springframework.stereotype.Component;

@Component
public class PaymentLifecycleMetrics {

    static final String METRIC_NAME = "payment_lifecycle_total";

    private final LongCounter counter;

    public PaymentLifecycleMetrics(OpenTelemetry openTelemetry) {
        this.counter = openTelemetry.getMeter("com.observabilitymesh.payment")
                .counterBuilder(METRIC_NAME)
                .setDescription("Payment lifecycle transitions")
                .build();
    }

    public void recordTransition(PaymentAction action, PaymentStatus status, String owningLob) {
        if (action == null || status == null) {
            return;
        }
        AttributesBuilder attributes = Attributes.builder()
                .put("action", action.name())
                .put("status", status.name());
        if (owningLob != null && !owningLob.isBlank()) {
            attributes.put("owning_lob", owningLob);
        }
        counter.add(1, attributes.build());
    }
}
