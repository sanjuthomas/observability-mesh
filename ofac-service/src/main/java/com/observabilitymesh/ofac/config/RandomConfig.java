package com.observabilitymesh.ofac.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

@Configuration
public class RandomConfig {

    @Bean
    Random ofacScanRandom() {
        return new Random();
    }
}
