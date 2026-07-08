package com.srecatalog.auth;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({SecurityConfig.class, SecurityIntegrationTestSupport.class})
public class SecurityIntegrationTestApp {

    @RestController
    static class ProbeController {
        @GetMapping("/health")
        String health() {
            return "UP";
        }
    }
}
