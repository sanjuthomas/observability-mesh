package com.observabilitymesh.sloauthor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.observabilitymesh")
public class SloAuthorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SloAuthorServiceApplication.class, args);
    }
}
