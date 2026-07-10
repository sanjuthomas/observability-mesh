package com.observabilitymesh.ofac.service;

import com.observabilitymesh.ofac.config.OfacMutantMode;
import com.observabilitymesh.ofac.config.OfacProperties;
import com.observabilitymesh.ofac.model.OfacScanRequestRef;
import com.observabilitymesh.ofac.model.OfacScanResult;

import java.util.Random;

/**
 * Injects demo mutants into sanction scan simulation to breach latency and completion SLOs.
 */
final class OfacScanMutator {

    private OfacScanMutator() {
    }

    static boolean applies(OfacProperties properties, OfacScanRequestRef request) {
        OfacMutantMode mode = properties.mutantMode();
        if (mode == null || mode == OfacMutantMode.OFF) {
            return false;
        }
        String prefix = properties.mutantPaymentIdPrefix();
        if (prefix == null || prefix.isBlank()) {
            return true;
        }
        return request.paymentId() != null && request.paymentId().contains(prefix);
    }

    static long scanDelayMs(OfacProperties properties, OfacScanRequestRef request, Random random) {
        if (applies(properties, request) && properties.mutantMode().affectsLatency()) {
            return randomDelay(
                    properties.mutantMinScanDelayMs(),
                    properties.mutantMaxScanDelayMs(),
                    random);
        }
        return randomDelay(properties.minScanDelayMs(), properties.maxScanDelayMs(), random);
    }

    static OfacScanResult pickResult(OfacProperties properties, OfacScanRequestRef request, Random random) {
        if (applies(properties, request) && properties.mutantMode().affectsCompletion()) {
            int percent = Math.clamp(properties.mutantUnableToDeterminePercent(), 0, 100);
            if (percent > 0 && random.nextInt(100) < percent) {
                return OfacScanResult.UNABLE_TO_DETERMINE;
            }
        } else if (random.nextInt(100) == 0) {
            return OfacScanResult.UNABLE_TO_DETERMINE;
        }
        return random.nextBoolean() ? OfacScanResult.PASSED : OfacScanResult.FAILED;
    }

    private static long randomDelay(long min, long max, Random random) {
        if (max <= min) {
            return min;
        }
        return min + random.nextLong(max - min + 1);
    }
}
