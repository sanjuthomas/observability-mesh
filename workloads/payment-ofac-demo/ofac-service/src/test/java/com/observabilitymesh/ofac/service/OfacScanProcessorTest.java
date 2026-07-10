package com.observabilitymesh.ofac.service;

import com.observabilitymesh.ofac.config.OfacMutantMode;
import com.observabilitymesh.ofac.config.OfacProperties;
import com.observabilitymesh.ofac.metrics.SanctionScanMetrics;
import com.observabilitymesh.ofac.model.OfacScanLifecycleStatus;
import com.observabilitymesh.ofac.model.OfacScanRequestRef;
import com.observabilitymesh.ofac.model.OfacScanResult;
import com.observabilitymesh.ofac.repo.ConcurrentModificationException;
import com.observabilitymesh.ofac.repo.OfacScanRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfacScanProcessorTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock OfacScanRequestRepository repository;
    @Mock Random random;
    @Mock SanctionScanMetrics sanctionScanMetrics;

    private OfacScanProcessor processor;

    private static final OfacScanRequestRef REQUEST =
            new OfacScanRequestRef("P-1", 2, 1, REQUESTED_AT);

    @BeforeEach
    void setUp() {
        OfacProperties properties = baselineProperties();
        processor = new OfacScanProcessor(repository, properties, Runnable::run, random, sanctionScanMetrics);
    }

    private static OfacProperties baselineProperties() {
        return new OfacProperties("scan-requests", 30_000, 0, 0, OfacMutantMode.OFF, "", 15, 90_000, 120_000);
    }

    @Test
    void pollDoesNothingWhenNoOpenRequests() {
        when(repository.listOpenCurrent()).thenReturn(List.of());

        processor.pollOpenRequests();

        verify(repository, never()).transition(any(), any(Integer.class), any(Integer.class), any(), any());
    }

    @Test
    void pollClaimsOpenRequestAndCompletesScan() {
        OfacScanRequestRef open = new OfacScanRequestRef("P-1", 2, 1, REQUESTED_AT);
        OfacScanRequestRef inProgress = new OfacScanRequestRef("P-1", 2, 2, REQUESTED_AT);
        OfacScanRequestRef processed = new OfacScanRequestRef("P-1", 2, 3, REQUESTED_AT);
        when(repository.listOpenCurrent()).thenReturn(List.of(open));
        when(repository.transition("P-1", 2, 1, OfacScanLifecycleStatus.IN_PROGRESS, null)).thenReturn(inProgress);
        when(repository.transition("P-1", 2, 2, OfacScanLifecycleStatus.PROCESSED, OfacScanResult.PASSED))
                .thenReturn(processed);
        when(random.nextInt(100)).thenReturn(1);
        when(random.nextBoolean()).thenReturn(true);

        processor.pollOpenRequests();

        verify(repository).transition("P-1", 2, 2, OfacScanLifecycleStatus.PROCESSED, OfacScanResult.PASSED);
        verify(sanctionScanMetrics).recordCompletion(eq(OfacScanResult.PASSED), any(Duration.class));
    }

    @Test
    void pollSkipsRequestLostToConcurrentClaim() {
        OfacScanRequestRef open = new OfacScanRequestRef("P-1", 2, 1, REQUESTED_AT);
        when(repository.listOpenCurrent()).thenReturn(List.of(open));
        when(repository.transition("P-1", 2, 1, OfacScanLifecycleStatus.IN_PROGRESS, null))
                .thenThrow(new ConcurrentModificationException("already claimed"));

        processor.pollOpenRequests();

        verify(repository, never()).transition(
                eq("P-1"), eq(2), eq(2), eq(OfacScanLifecycleStatus.PROCESSED), any());
        verify(sanctionScanMetrics, never()).recordCompletion(any(), any());
    }

    @Test
    void scanDelayUsesConfiguredRange() {
        OfacProperties properties = new OfacProperties(
                "scan-requests", 30_000, 30_000, 60_000, OfacMutantMode.OFF, "", 15, 90_000, 120_000);
        OfacScanProcessor rangedProcessor = new OfacScanProcessor(
                repository, properties, Runnable::run, random, sanctionScanMetrics);
        when(random.nextLong(30_001)).thenReturn(5_000L);

        assertThat(rangedProcessor.scanDelayMs(REQUEST)).isEqualTo(35_000);
    }

    @Test
    void scanDelayReturnsMinWhenRangeCollapsed() {
        assertThat(processor.scanDelayMs(REQUEST)).isZero();
    }

    @Test
    void pickResultAlternatesBetweenPassedAndFailed() {
        when(random.nextInt(100)).thenReturn(1);
        when(random.nextBoolean()).thenReturn(false);
        assertThat(processor.pickResult(REQUEST)).isEqualTo(OfacScanResult.FAILED);
    }

    @Test
    void pickResultReturnsUnableToDetermineRoughlyOnePercent() {
        when(random.nextInt(100)).thenReturn(0);
        assertThat(processor.pickResult(REQUEST)).isEqualTo(OfacScanResult.UNABLE_TO_DETERMINE);
    }

    @Test
    void completeAfterDelayHandlesInterrupted() {
        OfacScanRequestRef inProgress = new OfacScanRequestRef("P-1", 2, 2, REQUESTED_AT);
        Thread.currentThread().interrupt();
        try {
            processor.completeAfterDelay(inProgress, 0);
        } finally {
            Thread.interrupted();
        }
        verify(repository, never()).transition(
                eq("P-1"), eq(2), eq(2), eq(OfacScanLifecycleStatus.PROCESSED), any());
        verify(sanctionScanMetrics, never()).recordCompletion(any(), any());
    }

    @Test
    void completeAfterDelayHandlesCompletionRace() {
        OfacScanRequestRef inProgress = new OfacScanRequestRef("P-1", 2, 2, REQUESTED_AT);
        when(random.nextInt(100)).thenReturn(1);
        when(random.nextBoolean()).thenReturn(true);
        when(repository.transition("P-1", 2, 2, OfacScanLifecycleStatus.PROCESSED, OfacScanResult.PASSED))
                .thenThrow(new ConcurrentModificationException("lost race"));

        processor.completeAfterDelay(inProgress, 0);

        verify(repository).transition("P-1", 2, 2, OfacScanLifecycleStatus.PROCESSED, OfacScanResult.PASSED);
        verify(sanctionScanMetrics, never()).recordCompletion(any(), any());
    }

    @Test
    void completeAfterDelaySkipsMetricsWhenRequestedAtMissing() {
        OfacScanRequestRef inProgress = new OfacScanRequestRef("P-1", 2, 2, null);
        when(random.nextInt(100)).thenReturn(1);
        when(random.nextBoolean()).thenReturn(true);
        when(repository.transition("P-1", 2, 2, OfacScanLifecycleStatus.PROCESSED, OfacScanResult.PASSED))
                .thenReturn(new OfacScanRequestRef("P-1", 2, 3, null));

        processor.completeAfterDelay(inProgress, 0);

        verify(sanctionScanMetrics, never()).recordCompletion(any(), any());
    }
}
