package com.srecatalog.harness.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    @Test
    void harnessObjectMapperIgnoresUnknownProperties() throws Exception {
        ObjectMapper mapper = new JacksonConfig().harnessObjectMapper();
        Sample sample = mapper.readValue("{\"name\":\"x\",\"extra\":1}", Sample.class);
        assertThat(sample.name()).isEqualTo("x");
    }

    private record Sample(String name) {
    }
}
