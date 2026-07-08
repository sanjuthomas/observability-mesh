package com.observabilitymesh.sloprovisioner.sloth;

import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class SlothCliRunner {

    private final String slothBinary;

    public SlothCliRunner(SloProvisionerProperties properties) {
        this.slothBinary = properties.slothBinary();
    }

    public void generate(Path inputFile, Path outputFile) {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    slothBinary, "generate", "-i", inputFile.toString(), "-o", outputFile.toString());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new SlothExecutionException(
                        "sloth generate failed with exit code " + exitCode + ": " + output.trim());
            }
            if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
                throw new SlothExecutionException("sloth generate produced no output");
            }
        } catch (IOException ex) {
            throw new SlothExecutionException("failed to run sloth: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SlothExecutionException("sloth generate interrupted", ex);
        }
    }

    public boolean isAvailable() {
        try {
            ProcessBuilder builder = new ProcessBuilder(List.of(slothBinary, "validate", "-i", "/dev/null"));
            builder.redirectErrorStream(true);
            Process process = builder.start();
            process.waitFor();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
