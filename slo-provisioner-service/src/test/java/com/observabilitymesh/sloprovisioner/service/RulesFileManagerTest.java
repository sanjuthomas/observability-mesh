package com.observabilitymesh.sloprovisioner.service;

import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RulesFileManagerTest {

    @TempDir
    Path tempDir;

    private RulesFileManager rulesFileManager;

    @BeforeEach
    void setUp() throws Exception {
        SloProvisionerProperties properties = new SloProvisionerProperties(
                60_000,
                "service-level-objectives",
                "slo-provision-state",
                tempDir.toString(),
                "_archive",
                "",
                "sloth",
                tempDir.resolve("work").toString(),
                "payment-prometheus");
        rulesFileManager = new RulesFileManager(properties);
    }

    @Test
    void publishesAndArchivesRulesFile() throws Exception {
        Path generated = tempDir.resolve("generated.yml");
        Files.writeString(generated, "groups: []\n");

        rulesFileManager.publishActiveRules("sanction-scan-latency-30d", generated);

        Path active = rulesFileManager.activeRulesPath("sanction-scan-latency-30d");
        assertThat(Files.exists(active)).isTrue();

        rulesFileManager.archiveRules("sanction-scan-latency-30d");

        assertThat(Files.exists(active)).isFalse();
        assertThat(Files.exists(tempDir.resolve("_archive/sanction-scan-latency-30d.yml"))).isTrue();
    }

    @Test
    void archiveRulesNoOpWhenActiveFileMissing() throws Exception {
        rulesFileManager.archiveRules("missing-slo");
    }
}
