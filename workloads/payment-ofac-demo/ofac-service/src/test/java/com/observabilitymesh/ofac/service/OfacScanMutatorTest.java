package com.observabilitymesh.ofac.service;

import com.observabilitymesh.ofac.config.OfacMutantMode;
import com.observabilitymesh.ofac.config.OfacProperties;
import com.observabilitymesh.ofac.model.OfacScanRequestRef;
import com.observabilitymesh.ofac.model.OfacScanResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class OfacScanMutatorTest {

    private static final OfacScanRequestRef REQUEST =
            new OfacScanRequestRef("P-SLO-BREACH-1", 2, 1, Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void latencyMutantUsesSlowDelayRange() {
        OfacProperties properties = properties(OfacMutantMode.LATENCY);
        Random random = new Random(0);

        long delay = OfacScanMutator.scanDelayMs(properties, REQUEST, random);

        assertThat(delay).isBetween(90_000L, 120_000L);
    }

    @Test
    void completionMutantSkewsUnableToDetermine() {
        OfacProperties properties = properties(OfacMutantMode.COMPLETION);
        Random random = new Random(1);
        int unable = 0;
        for (int i = 0; i < 200; i++) {
            if (OfacScanMutator.pickResult(properties, REQUEST, random) == OfacScanResult.UNABLE_TO_DETERMINE) {
                unable++;
            }
        }
        assertThat(unable).isGreaterThan(20);
    }

    @Test
    void paymentIdPrefixLimitsMutantToMatchingRequests() {
        OfacProperties properties = new OfacProperties(
                "scan-requests",
                30_000,
                0,
                0,
                OfacMutantMode.LATENCY,
                "SLO-BREACH",
                15,
                90_000,
                120_000);
        OfacScanRequestRef other = new OfacScanRequestRef("P-1", 2, 1, Instant.now());

        assertThat(OfacScanMutator.applies(properties, REQUEST)).isTrue();
        assertThat(OfacScanMutator.applies(properties, other)).isFalse();
        assertThat(OfacScanMutator.scanDelayMs(properties, other, new Random(0))).isZero();
    }

    @Test
    void bothMutantAppliesLatencyAndCompletion() {
        OfacProperties properties = properties(OfacMutantMode.BOTH);
        Random random = new Random(2);

        assertThat(OfacScanMutator.scanDelayMs(properties, REQUEST, random)).isBetween(90_000L, 120_000L);

        int unable = 0;
        for (int i = 0; i < 100; i++) {
            if (OfacScanMutator.pickResult(properties, REQUEST, random) == OfacScanResult.UNABLE_TO_DETERMINE) {
                unable++;
            }
        }
        assertThat(unable).isGreaterThan(5);
    }

    @Test
    void offModeUsesBaselineBehavior() {
        OfacProperties properties = properties(OfacMutantMode.OFF);
        Random random = new Random(0);
        random.nextLong(30_001);

        assertThat(OfacScanMutator.scanDelayMs(properties, REQUEST, random)).isBetween(30_000L, 60_000L);
    }

    private static OfacProperties properties(OfacMutantMode mode) {
        return new OfacProperties(
                "scan-requests",
                30_000,
                30_000,
                60_000,
                mode,
                "",
                15,
                90_000,
                120_000);
    }
}
