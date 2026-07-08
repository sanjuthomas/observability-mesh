package com.observabilitymesh.harness.seed;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionPayloadFactoryTest {

    @Test
    void buildIncludesCoreFields() {
        Map<String, Object> payload = InstructionPayloadFactory.build("FICC", "STANDING", "USD");

        assertThat(payload)
                .containsEntry("instruction_type", "STANDING")
                .containsEntry("owning_lob", "FICC")
                .containsEntry("currency", "USD")
                .containsEntry("wire_scope", "DOMESTIC");
        assertThat(payload.get("funding_account")).isInstanceOf(Map.class);
    }
}
