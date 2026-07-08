package com.srecatalog.ofac.service;

import com.srecatalog.ofac.config.OfacProperties;
import com.srecatalog.ofac.model.OfacScanLifecycleStatus;
import com.srecatalog.ofac.model.OfacScanRequestRef;
import com.srecatalog.ofac.model.OfacScanResult;
import com.srecatalog.ofac.repo.ConcurrentModificationException;
import com.srecatalog.ofac.repo.OfacScanRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock OfacScanRequestRepository repository;
    @Mock Random random;

    private OfacScanProcessor processor;

    @BeforeEach
    void setUp() {
        OfacProperties properties = new OfacProperties("ofac-scan-requests", 30_000, 0, 0);
        processor = new OfacScanProcessor(repository, properties, Runnable::run, random);
    }

    @Test
    void pollDoesNothingWhenNoOpenRequests() {
        when(repository.listOpenCurrent()).thenReturn(List.of());

        processor.pollOpenRequests();

        verify(repository, never()).transition(any(), any(Integer.class), any(Integer.class), any(), any());
    }

    @Test
    void pollClaimsOpenRequestAndCompletesScan() {
        OfacScanRequestRef open = new OfacScanRequestRef("P-1", 2, 1);
        OfacScanRequestRef inProgress = new OfacScanRequestRef("P-1", 2, 2);
        OfacScanRequestRef processed = new OfacScanRequestRef("P-1", 2, 3);
        when(repository.listOpenCurrent()).thenReturn(List.of(open));
        when(repository.transition("P-1", 2, 1, OfacScanLifecycleStatus.IN_PROGRESS, null)).thenReturn(inProgress);
        when(repository.transition("P-1", 2, 2, OfacScanLifecycleStatus.PROCESSED, OfacScanResult.PASSED))
                .thenReturn(processed);
        when(random.nextBoolean()).thenReturn(true);

        processor.pollOpenRequests();

        verify(repository).transition("P-1", 2, 2, OfacScanLifecycleStatus.PROCESSED, OfacScanResult.PASSED);
    }

    @Test
    void pollSkipsRequestLostToConcurrentClaim() {
        OfacScanRequestRef open = new OfacScanRequestRef("P-1", 2, 1);
        when(repository.listOpenCurrent()).thenReturn(List.of(open));
        when(repository.transition("P-1", 2, 1, OfacScanLifecycleStatus.IN_PROGRESS, null))
                .thenThrow(new ConcurrentModificationException("already claimed"));

        processor.pollOpenRequests();

        verify(repository, never()).transition(
                eq("P-1"), eq(2), eq(2), eq(OfacScanLifecycleStatus.PROCESSED), any());
    }

    @Test
    void scanDelayUsesConfiguredRange() {
        OfacProperties properties = new OfacProperties("ofac-scan-requests", 30_000, 30_000, 60_000);
        OfacScanProcessor rangedProcessor = new OfacScanProcessor(repository, properties, Runnable::run, random);
        when(random.nextLong(30_001)).thenReturn(5_000L);

        assertThat(rangedProcessor.scanDelayMs()).isEqualTo(35_000);
    }

    @Test
    void scanDelayReturnsMinWhenRangeCollapsed() {
        assertThat(processor.scanDelayMs()).isZero();
    }

    @Test
    void pickResultAlternatesBetweenPassedAndFailed() {
        when(random.nextBoolean()).thenReturn(false);
        assertThat(processor.pickResult()).isEqualTo(OfacScanResult.FAILED);
    }

    @Test
    void completeAfterDelayHandlesInterrupted() {
        OfacScanRequestRef inProgress = new OfacScanRequestRef("P-1", 2, 2);
        Thread.currentThread().interrupt();
        try {
            processor.completeAfterDelay(inProgress, 0);
        } finally {
            Thread.interrupted();
        }
        verify(repository, never()).transition(
                eq("P-1"), eq(2), eq(2), eq(OfacScanLifecycleStatus.PROCESSED), any());
    }

    @Test
    void completeAfterDelayHandlesCompletionRace() {
        OfacScanRequestRef inProgress = new OfacScanRequestRef("P-1", 2, 2);
        when(random.nextBoolean()).thenReturn(true);
        when(repository.transition("P-1", 2, 2, OfacScanLifecycleStatus.PROCESSED, OfacScanResult.PASSED))
                .thenThrow(new ConcurrentModificationException("lost race"));

        processor.completeAfterDelay(inProgress, 0);

        verify(repository).transition("P-1", 2, 2, OfacScanLifecycleStatus.PROCESSED, OfacScanResult.PASSED);
    }
}
