package com.srecatalog.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionDeniedExceptionTest {

    @Test
    void carriesMessage() {
        PermissionDeniedException exception = new PermissionDeniedException("not allowed");
        assertThat(exception.getMessage()).isEqualTo("not allowed");
    }
}
