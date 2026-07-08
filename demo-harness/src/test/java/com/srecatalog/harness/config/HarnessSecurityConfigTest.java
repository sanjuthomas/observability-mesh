package com.srecatalog.harness.config;

import com.srecatalog.harness.DemoHarnessApplication;
import com.srecatalog.harness.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {DemoHarnessApplication.class, TestSecurityConfig.class})
@TestPropertySource(properties = {
        "otel.sdk.disabled=true",
        "sre-catalog.harness.verify-security-events=false"
})
class HarnessSecurityConfigTest {

    @Autowired
    SecurityFilterChain[] filterChains;

    @Test
    void registersSecurityFilterChains() {
        assertThat(filterChains).hasSizeGreaterThanOrEqualTo(3);
    }
}
