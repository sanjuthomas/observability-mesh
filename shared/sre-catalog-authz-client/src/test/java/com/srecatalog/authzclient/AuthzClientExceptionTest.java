package com.srecatalog.authzclient;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthzClientExceptionTest {

    @Test
    void carriesMessage() {
        AuthzClientException exception = new AuthzClientException("authorization-service unavailable");
        assertThat(exception.getMessage()).isEqualTo("authorization-service unavailable");
    }
}
