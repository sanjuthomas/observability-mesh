package com.srecatalog.harness.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceResponseTest {

    @Test
    void isSuccessFor2xx() {
        assertThat(new ServiceResponse(200, "ok", null).isSuccess()).isTrue();
        assertThat(new ServiceResponse(201, "created", null).isSuccess()).isTrue();
        assertThat(new ServiceResponse(403, "denied", null).isSuccess()).isFalse();
    }

    @Test
    void textTruncatesLongBodies() {
        ServiceResponse response = new ServiceResponse(400, "x".repeat(400), null);
        assertThat(response.text(300)).hasSize(300);
    }

    @Test
    void textHandlesNullAndShortBodies() {
        assertThat(new ServiceResponse(400, null, null).text(300)).isEmpty();
        assertThat(new ServiceResponse(400, "  short  ", null).text(300)).isEqualTo("short");
        assertThat(new ServiceResponse(400, "x".repeat(300), null).text(300)).hasSize(300);
    }
}
