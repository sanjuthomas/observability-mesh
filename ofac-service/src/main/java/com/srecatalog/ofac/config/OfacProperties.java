package com.srecatalog.ofac.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sre-catalog.ofac")
public record OfacProperties(
        String scanRequestsCollection,
        long pollIntervalMs,
        long minScanDelayMs,
        long maxScanDelayMs
) {
}
