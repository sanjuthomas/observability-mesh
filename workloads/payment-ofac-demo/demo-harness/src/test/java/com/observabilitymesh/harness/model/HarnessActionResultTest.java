package com.observabilitymesh.harness.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessActionResultTest {

    @Test
    void toMapIncludesAllFields() {
        HarnessActionResult result = new HarnessActionResult("create_instructions", 3);
        result.log("created one");
        result.recordSuccess();
        result.recordFailure();
        result.recordSkip();

        var map = result.toMap();
        assertThat(map)
                .containsEntry("action", "create_instructions")
                .containsEntry("requested", 3)
                .containsEntry("succeeded", 1)
                .containsEntry("failed", 1)
                .containsEntry("skipped", 1)
                .containsEntry("ok", true);
        assertThat(map.get("logs")).asList().containsExactly("created one");
    }
}
