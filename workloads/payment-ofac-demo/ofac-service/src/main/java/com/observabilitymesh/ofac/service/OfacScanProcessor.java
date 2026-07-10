package com.observabilitymesh.ofac.service;

import com.observabilitymesh.ofac.config.OfacProperties;
import com.observabilitymesh.ofac.metrics.SanctionScanMetrics;
import com.observabilitymesh.ofac.model.OfacScanLifecycleStatus;
import com.observabilitymesh.ofac.model.OfacScanRequestRef;
import com.observabilitymesh.ofac.model.OfacScanResult;
import com.observabilitymesh.ofac.repo.ConcurrentModificationException;
import com.observabilitymesh.ofac.repo.OfacScanRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class OfacScanProcessor {

    private static final Logger log = LoggerFactory.getLogger(OfacScanProcessor.class);

    private final OfacScanRequestRepository repository;
    private final OfacProperties properties;
    private final Executor scanExecutor;
    private final Random random;
    private final SanctionScanMetrics sanctionScanMetrics;

    public OfacScanProcessor(
            OfacScanRequestRepository repository,
            OfacProperties properties,
            Executor ofacScanExecutor,
            Random ofacScanRandom,
            SanctionScanMetrics sanctionScanMetrics) {
        this.repository = repository;
        this.properties = properties;
        this.scanExecutor = ofacScanExecutor;
        this.random = ofacScanRandom;
        this.sanctionScanMetrics = sanctionScanMetrics;
    }

    @Scheduled(fixedDelayString = "${observability-mesh.ofac.poll-interval-ms:30000}")
    public void pollOpenRequests() {
        List<OfacScanRequestRef> openRequests = repository.listOpenCurrent();
        if (openRequests.isEmpty()) {
            return;
        }
        log.info("OFAC batch poll picked up {} open scan request(s)", openRequests.size());
        for (OfacScanRequestRef request : openRequests) {
            claimAndSimulateScan(request);
        }
    }

    private void claimAndSimulateScan(OfacScanRequestRef request) {
        try {
            OfacScanRequestRef inProgress = repository.transition(
                    request.paymentId(),
                    request.paymentVersion(),
                    request.versionNumber(),
                    OfacScanLifecycleStatus.IN_PROGRESS,
                    null);
            log.info("OFAC scan started payment_id={} payment_version={} version_number={}",
                    inProgress.paymentId(), inProgress.paymentVersion(), inProgress.versionNumber());
            scheduleCompletion(inProgress);
        } catch (ConcurrentModificationException ex) {
            log.debug("Skipping OFAC request already claimed: {}", ex.getMessage());
        }
    }

    private void scheduleCompletion(OfacScanRequestRef inProgress) {
        long delayMs = scanDelayMs(inProgress);
        scanExecutor.execute(() -> completeAfterDelay(inProgress, delayMs));
    }

    void completeAfterDelay(OfacScanRequestRef inProgress, long delayMs) {
        try {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (delayMs > 0) {
                TimeUnit.MILLISECONDS.sleep(delayMs);
            }
            OfacScanResult result = pickResult(inProgress);
            OfacScanRequestRef processed = repository.transition(
                    inProgress.paymentId(),
                    inProgress.paymentVersion(),
                    inProgress.versionNumber(),
                    OfacScanLifecycleStatus.PROCESSED,
                    result);
            recordCompletionMetric(inProgress, result);
            log.info("OFAC scan completed payment_id={} payment_version={} version_number={} result={}",
                    processed.paymentId(), processed.paymentVersion(), processed.versionNumber(), result);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("OFAC scan interrupted payment_id={} payment_version={}",
                    inProgress.paymentId(), inProgress.paymentVersion());
        } catch (ConcurrentModificationException ex) {
            log.warn("OFAC scan completion lost race payment_id={} payment_version={}: {}",
                    inProgress.paymentId(), inProgress.paymentVersion(), ex.getMessage());
        }
    }

    long scanDelayMs(OfacScanRequestRef request) {
        return OfacScanMutator.scanDelayMs(properties, request, random);
    }

    OfacScanResult pickResult(OfacScanRequestRef request) {
        return OfacScanMutator.pickResult(properties, request, random);
    }

    private void recordCompletionMetric(OfacScanRequestRef inProgress, OfacScanResult result) {
        if (inProgress.requestedAt() == null) {
            return;
        }
        Duration duration = Duration.between(inProgress.requestedAt(), Instant.now());
        sanctionScanMetrics.recordCompletion(result, duration);
    }
}
