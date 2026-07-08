package com.srecatalog.ofac.service;

import com.srecatalog.ofac.config.OfacProperties;
import com.srecatalog.ofac.model.OfacScanLifecycleStatus;
import com.srecatalog.ofac.model.OfacScanRequestRef;
import com.srecatalog.ofac.model.OfacScanResult;
import com.srecatalog.ofac.repo.ConcurrentModificationException;
import com.srecatalog.ofac.repo.OfacScanRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class OfacScanProcessor {

    private static final Logger log = LoggerFactory.getLogger(OfacScanProcessor.class);

    private final OfacScanRequestRepository repository;
    private final OfacProperties properties;
    private final Executor scanExecutor;
    private final Random random;

    public OfacScanProcessor(
            OfacScanRequestRepository repository,
            OfacProperties properties,
            Executor ofacScanExecutor,
            Random ofacScanRandom) {
        this.repository = repository;
        this.properties = properties;
        this.scanExecutor = ofacScanExecutor;
        this.random = ofacScanRandom;
    }

    @Scheduled(fixedDelayString = "${sre-catalog.ofac.poll-interval-ms:30000}")
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
        long delayMs = scanDelayMs();
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
            OfacScanResult result = pickResult();
            OfacScanRequestRef processed = repository.transition(
                    inProgress.paymentId(),
                    inProgress.paymentVersion(),
                    inProgress.versionNumber(),
                    OfacScanLifecycleStatus.PROCESSED,
                    result);
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

    long scanDelayMs() {
        long min = properties.minScanDelayMs();
        long max = properties.maxScanDelayMs();
        if (max <= min) {
            return min;
        }
        return min + random.nextLong(max - min + 1);
    }

    OfacScanResult pickResult() {
        return random.nextBoolean() ? OfacScanResult.PASSED : OfacScanResult.FAILED;
    }
}
