package com.observabilitymesh.instruction.metrics;

import com.observabilitymesh.instruction.model.InstructionAction;
import com.observabilitymesh.instruction.model.InstructionStatus;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import org.springframework.stereotype.Component;

@Component
public class InstructionLifecycleMetrics {

    static final String METRIC_NAME = "instruction_lifecycle_total";

    private final LongCounter counter;

    public InstructionLifecycleMetrics(OpenTelemetry openTelemetry) {
        this.counter = openTelemetry.getMeter("com.observabilitymesh.instruction")
                .counterBuilder(METRIC_NAME)
                .setDescription("Instruction lifecycle transitions")
                .build();
    }

    public void recordTransition(InstructionAction action, InstructionStatus status, String owningLob) {
        if (action == null || status == null || action == InstructionAction.VIEW) {
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
