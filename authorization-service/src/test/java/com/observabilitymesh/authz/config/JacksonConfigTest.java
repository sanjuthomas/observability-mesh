package com.observabilitymesh.authz.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    @Test
    void authzObjectMapperUsesSnakeCase() {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.authzObjectMapper();
        assertThat(mapper.getPropertyNamingStrategy()).isEqualTo(PropertyNamingStrategies.SNAKE_CASE);
    }
}
