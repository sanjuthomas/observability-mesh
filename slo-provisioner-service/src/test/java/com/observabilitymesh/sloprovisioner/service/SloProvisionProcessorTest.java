package com.observabilitymesh.sloprovisioner.service;

import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import com.observabilitymesh.sloprovisioner.model.OpenSloDocumentView;
import com.observabilitymesh.sloprovisioner.model.ProvisionStatus;
import com.observabilitymesh.sloprovisioner.model.SloProvisionState;
import com.observabilitymesh.sloprovisioner.prometheus.PrometheusReloadException;
import com.observabilitymesh.sloprovisioner.prometheus.PrometheusReloader;
import com.observabilitymesh.sloprovisioner.repo.OpenSloDocumentRepository;
import com.observabilitymesh.sloprovisioner.repo.SloProvisionStateRepository;
import com.observabilitymesh.sloprovisioner.sloth.SlothCliRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SloProvisionProcessorTest {

    @Mock OpenSloDocumentRepository openSloDocumentRepository;
    @Mock SloProvisionStateRepository provisionStateRepository;
    @Mock SlothCliRunner slothCliRunner;
    @Mock PrometheusReloader prometheusReloader;

    @TempDir Path tempDir;

    private SloProvisionProcessor processor;
    private RulesFileManager rulesFileManager;

    @BeforeEach
    void setUp() throws Exception {
        SloProvisionerProperties properties = new SloProvisionerProperties(
                60_000,
                "service-level-objectives",
                "slo-provision-state",
                tempDir.resolve("rules").toString(),
                "_archive",
                "http://prometheus:9090/-/reload",
                "/tmp/sloth",
                tempDir.resolve("work").toString(),
                "payment-prometheus");
        rulesFileManager = new RulesFileManager(properties);
        processor = new SloProvisionProcessor(
                openSloDocumentRepository,
                provisionStateRepository,
                properties,
                slothCliRunner,
                rulesFileManager,
                prometheusReloader,
                new ContentHasher());
    }

    @Test
    void syncReprovisionsWhenVersionChanges() throws Exception {
        OpenSloDocumentView slo = sloDocument();
        OpenSloDocumentView sli = sliDocument();
        when(openSloDocumentRepository.findActiveByKindAndName("SLI", "sanction-scan-under-one-minute"))
                .thenReturn(Optional.of(sli));
        when(provisionStateRepository.findByLogicalKey(slo.logicalKey()))
                .thenReturn(new SloProvisionState(
                        slo.logicalKey(), 0, ProvisionStatus.ACTIVE, "sanction-scan-latency-30d.yml",
                        "old-hash", Instant.now(), null));
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(1), "groups: []\n");
            return null;
        }).when(slothCliRunner).generate(any(), any());

        assertThat(processor.syncActiveSlo(slo)).isTrue();
    }

    @Test
    void syncReprovisionsWhenPreviousStateFailed() throws Exception {
        OpenSloDocumentView slo = sloDocument();
        OpenSloDocumentView sli = sliDocument();
        when(openSloDocumentRepository.findActiveByKindAndName("SLI", "sanction-scan-under-one-minute"))
                .thenReturn(Optional.of(sli));
        when(provisionStateRepository.findByLogicalKey(slo.logicalKey()))
                .thenReturn(new SloProvisionState(
                        slo.logicalKey(), 1, ProvisionStatus.FAILED, "sanction-scan-latency-30d.yml",
                        "", Instant.now(), "previous error"));
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(1), "groups: []\n");
            return null;
        }).when(slothCliRunner).generate(any(), any());

        assertThat(processor.syncActiveSlo(slo)).isTrue();
    }

    @Test
    void pollReloadsPrometheusAfterSuccessfulSync() throws Exception {
        OpenSloDocumentView slo = sloDocument();
        OpenSloDocumentView sli = sliDocument();
        when(openSloDocumentRepository.listActiveByKind("SLO")).thenReturn(List.of(slo));
        when(openSloDocumentRepository.findActiveByKindAndName("SLI", "sanction-scan-under-one-minute"))
                .thenReturn(Optional.of(sli));
        when(provisionStateRepository.listByStatus(ProvisionStatus.ACTIVE)).thenReturn(List.of());
        when(provisionStateRepository.findByLogicalKey(slo.logicalKey())).thenReturn(null);
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(1), "groups: []\n");
            return null;
        }).when(slothCliRunner).generate(any(), any());

        processor.pollAndProvision();

        verify(prometheusReloader).reload();
    }

    @Test
    void pollSurvivesPrometheusReloadFailure() {
        when(openSloDocumentRepository.listActiveByKind("SLO")).thenReturn(List.of());
        when(provisionStateRepository.listByStatus(ProvisionStatus.ACTIVE))
                .thenReturn(List.of(new SloProvisionState(
                        "openslo/v1/SLO/removed", 1, ProvisionStatus.ACTIVE, "removed.yml",
                        "hash", Instant.now(), null)));
        doThrow(new PrometheusReloadException("down")).when(prometheusReloader).reload();

        processor.pollAndProvision();
    }

    @Test
    void syncRecordsFailureWhenSliMissing() {
        OpenSloDocumentView slo = sloDocument();
        when(openSloDocumentRepository.findActiveByKindAndName("SLI", "sanction-scan-under-one-minute"))
                .thenReturn(Optional.empty());

        assertThat(processor.syncActiveSlo(slo)).isFalse();
        verify(provisionStateRepository).upsert(org.mockito.ArgumentMatchers.argThat(
                state -> state.status() == ProvisionStatus.FAILED));
    }

    @Test
    void syncRecordsFailureWhenContentMissing() {
        OpenSloDocumentView slo = new OpenSloDocumentView(
                "1", "openslo/v1/SLO/bad", 1, false, "SLO", "bad", null);

        assertThat(processor.syncActiveSlo(slo)).isFalse();
        verify(provisionStateRepository).upsert(org.mockito.ArgumentMatchers.argThat(
                state -> state.status() == ProvisionStatus.FAILED));
    }

    @Test
    void syncRecordsFailureWhenSpecMissing() {
        OpenSloDocumentView slo = new OpenSloDocumentView(
                "1", "openslo/v1/SLO/bad", 1, false, "SLO", "bad", Map.of());

        assertThat(processor.syncActiveSlo(slo)).isFalse();
    }

    @Test
    void syncRecordsFailureWhenSlothFails() throws Exception {
        OpenSloDocumentView slo = sloDocument();
        OpenSloDocumentView sli = sliDocument();
        when(openSloDocumentRepository.findActiveByKindAndName("SLI", "sanction-scan-under-one-minute"))
                .thenReturn(Optional.of(sli));
        when(provisionStateRepository.findByLogicalKey(slo.logicalKey())).thenReturn(null);
        doThrow(new com.observabilitymesh.sloprovisioner.sloth.SlothExecutionException("boom"))
                .when(slothCliRunner).generate(any(), any());

        assertThat(processor.syncActiveSlo(slo)).isFalse();
    }

    @Test
    void syncRecordsFailureWhenIndicatorRefMissing() {
        OpenSloDocumentView slo = new OpenSloDocumentView(
                "1", "openslo/v1/SLO/bad", 1, false, "SLO", "bad",
                Map.of("spec", Map.of("objectives", List.of(Map.of("target", 0.99)))));

        assertThat(processor.syncActiveSlo(slo)).isFalse();
        verify(provisionStateRepository).upsert(org.mockito.ArgumentMatchers.argThat(
                state -> state.status() == ProvisionStatus.FAILED));
    }

    @Test
    void syncActiveSloGeneratesRulesAndUpdatesState() throws Exception {
        OpenSloDocumentView slo = sloDocument();
        OpenSloDocumentView sli = sliDocument();
        when(openSloDocumentRepository.findActiveByKindAndName("SLI", "sanction-scan-under-one-minute"))
                .thenReturn(Optional.of(sli));
        when(provisionStateRepository.findByLogicalKey(slo.logicalKey())).thenReturn(null);
        doAnswer(invocation -> {
            Path output = invocation.getArgument(1);
            Files.writeString(output, "groups: []\n");
            return null;
        }).when(slothCliRunner).generate(any(), any());

        boolean changed = processor.syncActiveSlo(slo);

        assertThat(changed).isTrue();
        assertThat(Files.exists(rulesFileManager.activeRulesPath("sanction-scan-latency-30d"))).isTrue();
        verify(slothCliRunner).generate(any(), any());
        verify(provisionStateRepository).upsert(org.mockito.ArgumentMatchers.argThat(state ->
                state.status() == ProvisionStatus.ACTIVE && state.opensloVersion() == 1));
    }

    @Test
    void syncSkipsWhenVersionAndHashUnchanged() {
        OpenSloDocumentView slo = sloDocument();
        OpenSloDocumentView sli = sliDocument();
        when(openSloDocumentRepository.findActiveByKindAndName("SLI", "sanction-scan-under-one-minute"))
                .thenReturn(Optional.of(sli));
        ContentHasher hasher = new ContentHasher();
        String hash = hasher.sha256(com.observabilitymesh.sloprovisioner.compile.OpenSloV1ToSlothCompiler.compile(
                slo, sli, java.util.Set.of("payment-prometheus")));
        when(provisionStateRepository.findByLogicalKey(slo.logicalKey()))
                .thenReturn(new SloProvisionState(
                        slo.logicalKey(), 1, ProvisionStatus.ACTIVE, "sanction-scan-latency-30d.yml",
                        hash, Instant.now(), null));

        assertThat(processor.syncActiveSlo(slo)).isFalse();
        verify(slothCliRunner, never()).generate(any(), any());
    }

    @Test
    void archiveMovesRulesAndUpdatesState() throws Exception {
        Path generated = tempDir.resolve("rules/generated.yml");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, "groups: []\n");
        rulesFileManager.publishActiveRules("sanction-scan-latency-30d", generated);

        SloProvisionState active = new SloProvisionState(
                "openslo/v1/SLO/sanction-scan-latency-30d",
                1,
                ProvisionStatus.ACTIVE,
                "sanction-scan-latency-30d.yml",
                "hash",
                Instant.now(),
                null);

        assertThat(processor.archiveSlo(active)).isTrue();
        verify(provisionStateRepository).upsert(org.mockito.ArgumentMatchers.argThat(
                state -> state.status() == ProvisionStatus.ARCHIVED));
    }

    @Test
    void pollArchivesRemovedActiveSlo() {
        when(openSloDocumentRepository.listActiveByKind("SLO")).thenReturn(List.of());
        SloProvisionState active = new SloProvisionState(
                "openslo/v1/SLO/removed",
                2,
                ProvisionStatus.ACTIVE,
                "removed.yml",
                "hash",
                Instant.now(),
                null);
        when(provisionStateRepository.listByStatus(ProvisionStatus.ACTIVE)).thenReturn(List.of(active));

        processor.pollAndProvision();

        verify(provisionStateRepository).upsert(org.mockito.ArgumentMatchers.argThat(
                state -> state.logicalKey().equals("openslo/v1/SLO/removed")
                        && state.status() == ProvisionStatus.ARCHIVED));
        verify(prometheusReloader).reload();
    }

    private static OpenSloDocumentView sloDocument() {
        return new OpenSloDocumentView(
                "1",
                "openslo/v1/SLO/sanction-scan-latency-30d",
                1,
                false,
                "SLO",
                "sanction-scan-latency-30d",
                Map.of(
                        "metadata", Map.of("displayName", "Sanction scan latency"),
                        "spec", Map.of(
                                "service", "payment-platform",
                                "description", "desc",
                                "indicatorRef", "sanction-scan-under-one-minute",
                                "timeWindow", List.of(Map.of("duration", "30d")),
                                "objectives", List.of(Map.of("target", 0.999)))));
    }

    private static OpenSloDocumentView sliDocument() {
        return new OpenSloDocumentView(
                "2",
                "openslo/v1/SLI/sanction-scan-under-one-minute",
                1,
                false,
                "SLI",
                "sanction-scan-under-one-minute",
                Map.of(
                        "spec", Map.of(
                                "ratioMetric", Map.of(
                                        "good", Map.of(
                                                "metricSource", Map.of(
                                                        "metricSourceRef", "payment-prometheus",
                                                        "spec", Map.of("query", "sum(increase(good[5m]))"))),
                                        "total", Map.of(
                                                "metricSource", Map.of(
                                                        "metricSourceRef", "payment-prometheus",
                                                        "spec", Map.of("query", "sum(increase(total[5m]))")))))));
    }
}
