package com.observabilitymesh.sloprovisioner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "observability-mesh.slo-provisioner")
public record SloProvisionerProperties(
        long pollIntervalMs,
        String opensloTable,
        String provisionStateTable,
        String prometheusRulesDir,
        String archiveSubdir,
        String prometheusReloadUrl,
        String slothBinary,
        String workDir,
        String datasourceNames
) {
    public Set<String> datasourceNameSet() {
        if (datasourceNames == null || datasourceNames.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(datasourceNames.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
