package com.observabilitymesh.sequence.format;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SequenceFormattingTest {

    @Test
    void buildsBusinessSequenceId() {
        String key = SequenceFormatting.counterKey("20260707", "FICC", "I");
        assertThat(SequenceFormatting.sequenceId(key, 42)).isEqualTo("20260707-FICC-I-42");
    }

    @Test
    void buildsSecurityEventSequenceId() {
        assertThat(SequenceFormatting.securityEventSequenceId("20260707-FICC-I-1", 3))
                .isEqualTo("20260707-FICC-I-1-SE-3");
    }
}
