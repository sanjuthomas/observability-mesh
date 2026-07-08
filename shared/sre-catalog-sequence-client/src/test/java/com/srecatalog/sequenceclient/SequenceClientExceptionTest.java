package com.srecatalog.sequenceclient;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SequenceClientExceptionTest {

    @Test
    void carriesMessage() {
        SequenceClientException exception = new SequenceClientException("sequence-service unavailable");
        assertThat(exception.getMessage()).isEqualTo("sequence-service unavailable");
    }
}
