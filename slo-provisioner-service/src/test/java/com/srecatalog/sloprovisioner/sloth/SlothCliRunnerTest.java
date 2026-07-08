package com.srecatalog.sloprovisioner.sloth;

import com.srecatalog.sloprovisioner.config.SloProvisionerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlothCliRunnerTest {

    @TempDir Path tempDir;

    @Test
    void generateWritesOutputWhenBinarySucceeds() throws Exception {
        Path script = tempDir.resolve("sloth.sh");
        Files.writeString(script, """
                #!/bin/sh
                while [ $# -gt 0 ]; do
                  if [ "$1" = "-o" ]; then
                    out="$2"
                    shift 2
                  else
                    shift
                  fi
                done
                echo 'groups: []' > "$out"
                """);
        script.toFile().setExecutable(true);

        SlothCliRunner runner = new SlothCliRunner(properties(script.toString()));
        Path input = tempDir.resolve("input.yml");
        Path output = tempDir.resolve("output.yml");
        Files.writeString(input, "apiVersion: openslo/v1alpha\n");

        runner.generate(input, output);

        assertThat(Files.readString(output)).contains("groups");
        assertThat(runner.isAvailable()).isTrue();
    }

    @Test
    void generateFailsWhenBinaryExitsNonZero() throws Exception {
        Path script = tempDir.resolve("fail.sh");
        Files.writeString(script, "#!/bin/sh\nexit 2\n");
        script.toFile().setExecutable(true);
        SlothCliRunner runner = new SlothCliRunner(properties(script.toString()));
        Path input = tempDir.resolve("input.yml");
        Path output = tempDir.resolve("output.yml");
        Files.writeString(input, "x\n");

        assertThatThrownBy(() -> runner.generate(input, output))
                .isInstanceOf(SlothExecutionException.class)
                .hasMessageContaining("exit code");
    }

    @Test
    void generateFailsWhenOutputEmpty() throws Exception {
        Path script = tempDir.resolve("empty.sh");
        Files.writeString(script, """
                #!/bin/sh
                while [ $# -gt 0 ]; do
                  if [ "$1" = "-o" ]; then
                    : > "$2"
                    shift 2
                  else
                    shift
                  fi
                done
                """);
        script.toFile().setExecutable(true);
        SlothCliRunner runner = new SlothCliRunner(properties(script.toString()));
        Path input = tempDir.resolve("input.yml");
        Path output = tempDir.resolve("output.yml");
        Files.writeString(input, "x\n");

        assertThatThrownBy(() -> runner.generate(input, output))
                .isInstanceOf(SlothExecutionException.class)
                .hasMessageContaining("no output");
    }

    @Test
    void generateFailsForMissingBinary() {
        SlothCliRunner runner = new SlothCliRunner(properties("/nonexistent/sloth"));
        Path input = tempDir.resolve("input.yml");
        Path output = tempDir.resolve("output.yml");

        assertThatThrownBy(() -> runner.generate(input, output))
                .isInstanceOf(SlothExecutionException.class);
    }

    @Test
    void isAvailableReturnsFalseForMissingBinary() {
        SlothCliRunner runner = new SlothCliRunner(properties("/nonexistent/sloth"));
        assertThat(runner.isAvailable()).isFalse();
    }

    private static SloProvisionerProperties properties(String slothBinary) {
        return new SloProvisionerProperties(
                60_000, "service-level-objectives", "slo-provision-state",
                "/rules", "_archive", "", slothBinary, "/work", "payment-prometheus");
    }
}
