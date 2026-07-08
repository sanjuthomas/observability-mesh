package com.srecatalog.harness;

import com.srecatalog.auth.SecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(scanBasePackages = "com.srecatalog")
@ConfigurationPropertiesScan
@ComponentScan(
        basePackages = "com.srecatalog",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SecurityConfig.class))
public class DemoHarnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoHarnessApplication.class, args);
    }
}
