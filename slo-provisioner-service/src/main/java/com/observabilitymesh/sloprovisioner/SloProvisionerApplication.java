package com.observabilitymesh.sloprovisioner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.observabilitymesh")
@EnableScheduling
@ConfigurationPropertiesScan
public class SloProvisionerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SloProvisionerApplication.class, args);
    }
}
