package com.srecatalog.sloprovisioner.sloth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SlothCliRunnerIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    @EnabledIf("slothAvailable")
    void generatesPrometheusRulesFromOpenSlo() throws Exception {
        String openSlo = """
                apiVersion: openslo/v1alpha
                kind: SLO
                metadata:
                  name: sanction-scan-latency-30d
                  displayName: Sanction scan latency
                spec:
                  service: payment-platform
                  description: demo
                  budgetingMethod: Occurrences
                  objectives:
                    - target: 0.999
                      ratioMetrics:
                        good:
                          source: prometheus
                          queryType: promql
                          query: sum(increase(sanction_scan_completed_total{duration_le="60s",status="success"}[{{.window}}]))
                        total:
                          source: prometheus
                          queryType: promql
                          query: sum(increase(sanction_scan_completed_total{status="success"}[{{.window}}]))
                  timeWindows:
                    - count: 30
                      unit: Day
                """;
        Path input = tempDir.resolve("input.yml");
        Path output = tempDir.resolve("rules.yml");
        Files.writeString(input, openSlo);

        SlothCliRunner runner = new SlothCliRunner(new com.srecatalog.sloprovisioner.config.SloProvisionerProperties(
                60_000, "service-level-objectives", "slo-provision-state",
                tempDir.toString(), "_archive", "", slothBinary(), tempDir.toString(), "payment-prometheus"));
        runner.generate(input, output);

        String rules = Files.readString(output);
        assertThat(rules).contains("slo:sli_error:ratio_rate5m");
        assertThat(rules).contains("sloth_service");
        assertThat(rules).contains("payment-platform");
    }

    static boolean slothAvailable() {
        return Files.isExecutable(Path.of("/tmp/sloth-mac")) || Files.isExecutable(Path.of("/usr/local/bin/sloth"));
    }

    private static String slothBinary() {
        if (Files.isExecutable(Path.of("/tmp/sloth-mac"))) {
            return "/tmp/sloth-mac";
        }
        return "/usr/local/bin/sloth";
    }
}
