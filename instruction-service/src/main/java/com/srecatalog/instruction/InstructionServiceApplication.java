package com.srecatalog.instruction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.srecatalog")
@ConfigurationPropertiesScan
public class InstructionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InstructionServiceApplication.class, args);
    }
}
