package com.srecatalog.harness.seed;

import com.srecatalog.harness.config.HarnessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class SeedFileLoaderTest {

    @Test
    void loadsBundledUsersYaml() {
        HarnessProperties properties = new HarnessProperties(
                "http://localhost:9000",
                "/api/v1",
                "http://localhost:9093",
                "/api/v1",
                "classpath:users.yaml",
                "Password1!",
                "ssi.local",
                "admin-001",
                "security_events",
                "instruction_service",
                "payment_service",
                true);
        SeedFileLoader loader = new SeedFileLoader(properties, new DefaultResourceLoader());

        SeedFile seed = loader.load();

        assertThat(seed.users()).isNotEmpty();
        assertThat(seed.userById("mo-100").title()).isEqualTo("Analyst");
        assertThat(seed.defaults()).containsEntry("email_domain", "ssi.local");
    }
}
