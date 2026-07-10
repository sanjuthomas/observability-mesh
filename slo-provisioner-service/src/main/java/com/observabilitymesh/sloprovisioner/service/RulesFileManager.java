package com.observabilitymesh.sloprovisioner.service;

import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Component
public class RulesFileManager {

    private final Path rulesDir;
    private final Path archiveDir;

    public RulesFileManager(SloProvisionerProperties properties) throws IOException {
        this.rulesDir = Path.of(properties.prometheusRulesDir());
        this.archiveDir = rulesDir.resolve(properties.archiveSubdir());
        Files.createDirectories(rulesDir);
        Files.createDirectories(archiveDir);
    }

    public Path activeRulesPath(String sloName) {
        return rulesDir.resolve(safeFileName(sloName) + ".yml");
    }

    public void publishActiveRules(String sloName, Path generatedRules) throws IOException {
        publishRules(activeRulesPath(sloName), generatedRules);
    }

    public Path alertPolicyRulesPath(String policyName) {
        return rulesDir.resolve("alert-" + safeFileName(policyName) + ".yml");
    }

    public void publishAlertPolicyRules(String policyName, Path generatedRules) throws IOException {
        publishRules(alertPolicyRulesPath(policyName), generatedRules);
    }

    public void archiveAlertPolicyRules(String policyName) throws IOException {
        moveRulesFileToArchive("alert-" + safeFileName(policyName));
    }

    private void publishRules(Path target, Path generatedRules) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(generatedRules, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void moveRulesFileToArchive(String fileBase) throws IOException {
        Path active = rulesDir.resolve(fileBase + ".yml");
        if (!Files.exists(active)) {
            return;
        }
        Path archived = archiveDir.resolve(fileBase + ".yml");
        Files.move(active, archived, StandardCopyOption.REPLACE_EXISTING);
    }

    public void archiveRules(String sloName) throws IOException {
        moveRulesFileToArchive(safeFileName(sloName));
    }

    public Optional<String> readActiveRulesContent(String sloName) {
        Path active = activeRulesPath(sloName);
        if (!Files.exists(active)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(active));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    static String safeFileName(String sloName) {
        return sloName.replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}
