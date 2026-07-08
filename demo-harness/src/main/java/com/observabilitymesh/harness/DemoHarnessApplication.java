package com.observabilitymesh.harness;

import com.observabilitymesh.auth.SecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(scanBasePackages = "com.observabilitymesh")
@ConfigurationPropertiesScan
@ComponentScan(
        basePackages = "com.observabilitymesh",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SecurityConfig.class))
public class DemoHarnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoHarnessApplication.class, args);
    }
}
