package com.observabilitymesh.sloprovisioner.service;

import com.observabilitymesh.sloprovisioner.model.OpenSloDocumentView;
import com.observabilitymesh.sloprovisioner.model.ProvisionStatus;
import com.observabilitymesh.sloprovisioner.model.SloProvisionState;
import com.observabilitymesh.sloprovisioner.repo.OpenSloDocumentRepository;
import com.observabilitymesh.sloprovisioner.repo.SloProvisionStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvisionUiServiceTest {

    @Mock OpenSloDocumentRepository documentRepository;
    @Mock SloProvisionStateRepository provisionStateRepository;
    @Mock RulesFileManager rulesFileManager;

    private ProvisionUiService service;

    @BeforeEach
    void setUp() {
        service = new ProvisionUiService(documentRepository, provisionStateRepository, rulesFileManager);
    }

    @Test
    void listSlosJoinsProvisionStateAndFiltersStatus() {
        OpenSloDocumentView slo = slo("sanction-scan-latency-30d", "sanction-scan-under-one-minute");
        OpenSloDocumentView unprovisioned = slo("pending-slo", "demo-sli");
        SloProvisionState state = new SloProvisionState(
                slo.logicalKey(),
                1,
                ProvisionStatus.ACTIVE,
                "sanction-scan-latency-30d.yml",
                "hash",
                Instant.parse("2026-07-08T12:00:00Z"),
                null);
        when(documentRepository.listActiveByKind("SLO")).thenReturn(List.of(slo, unprovisioned));
        when(provisionStateRepository.listAll()).thenReturn(List.of(state));
        when(rulesFileManager.readActiveRulesContent("sanction-scan-latency-30d")).thenReturn(Optional.empty());

        List<Map<String, Object>> active = service.listSlos("ACTIVE", 100);
        List<Map<String, Object>> notProvisioned = service.listSlos("NOT_PROVISIONED", 100);

        assertThat(active).hasSize(1);
        assertThat(active.getFirst().get("provision_status")).isEqualTo("ACTIVE");
        assertThat(notProvisioned).hasSize(1);
        assertThat(notProvisioned.getFirst().get("provision_status")).isEqualTo("NOT_PROVISIONED");
    }

    @Test
    void listSlosReturnsAllWhenFilterBlank() {
        when(documentRepository.listActiveByKind("SLO")).thenReturn(List.of(slo("demo-slo", "demo-sli")));
        when(provisionStateRepository.listAll()).thenReturn(List.of());

        assertThat(service.listSlos(null, 100)).hasSize(1);
        assertThat(service.listSlos("ALL", 100)).hasSize(1);
    }

    @Test
    void getSloIncludesIndicatorAndRules() {
        OpenSloDocumentView slo = slo("demo-slo", "demo-sli");
        OpenSloDocumentView sli = sli("demo-sli");
        SloProvisionState state = new SloProvisionState(
                slo.logicalKey(),
                1,
                ProvisionStatus.ACTIVE,
                "demo-slo.yml",
                "hash",
                Instant.parse("2026-07-08T12:00:00Z"),
                null);

        when(documentRepository.findActiveByKindAndName("SLO", "demo-slo")).thenReturn(Optional.of(slo));
        when(provisionStateRepository.findByLogicalKey(slo.logicalKey())).thenReturn(state);
        when(documentRepository.findActiveByKindAndName("SLI", "demo-sli")).thenReturn(Optional.of(sli));
        when(rulesFileManager.readActiveRulesContent("demo-slo")).thenReturn(Optional.of("groups: []\n"));

        Map<String, Object> row = service.getSlo("demo-slo");

        assertThat(row.get("prometheus_rules")).isEqualTo("groups: []\n");
        assertThat(row.get("indicator")).isNotNull();
        assertThat(row.get("content")).isNotNull();
    }

    @Test
    void getSloThrowsWhenMissing() {
        when(documentRepository.findActiveByKindAndName("SLO", "missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getSlo("missing"))
                .isInstanceOf(ProvisionDocumentNotFoundException.class);
    }

    @Test
    void listSlisReturnsPromqlFields() {
        when(documentRepository.listActiveByKind("SLI")).thenReturn(List.of(sli("demo-sli")));

        List<Map<String, Object>> slis = service.listSlis(100);

        assertThat(slis).hasSize(1);
        assertThat(slis.getFirst().get("good_query")).isEqualTo("good_query");
        assertThat(slis.getFirst().get("total_query")).isEqualTo("total_query");
    }

    @Test
    void getSliReturnsDocument() {
        when(documentRepository.findActiveByKindAndName("SLI", "demo-sli")).thenReturn(Optional.of(sli("demo-sli")));
        Map<String, Object> row = service.getSli("demo-sli");
        assertThat(row.get("content")).isNotNull();
    }

    @Test
    void getSliThrowsWhenMissing() {
        when(documentRepository.findActiveByKindAndName("SLI", "missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getSli("missing"))
                .isInstanceOf(ProvisionDocumentNotFoundException.class);
    }

    @Test
    void failedProvisionStateOmitsPrometheusRules() {
        OpenSloDocumentView slo = slo("failed-slo", "");
        SloProvisionState state = new SloProvisionState(
                slo.logicalKey(),
                1,
                ProvisionStatus.FAILED,
                "failed-slo.yml",
                "hash",
                Instant.parse("2026-07-08T12:00:00Z"),
                "boom");
        when(documentRepository.findActiveByKindAndName("SLO", "failed-slo")).thenReturn(Optional.of(slo));
        when(provisionStateRepository.findByLogicalKey(slo.logicalKey())).thenReturn(state);

        Map<String, Object> row = service.getSlo("failed-slo");

        assertThat(row.get("provision_status")).isEqualTo("FAILED");
        assertThat(row.get("prometheus_rules")).isNull();
        assertThat(row.get("last_error")).isEqualTo("boom");
    }

    private static OpenSloDocumentView slo(String name, String indicatorRef) {
        return new OpenSloDocumentView(
                "1",
                "openslo/v1/SLO/" + name,
                1,
                false,
                "SLO",
                name,
                Map.of(
                        "metadata", Map.of("displayName", name),
                        "spec", Map.of(
                                "service", "payment-platform",
                                "description", "desc",
                                "indicatorRef", indicatorRef,
                                "timeWindow", List.of(Map.of("duration", "30d")),
                                "objectives", List.of(Map.of("target", 0.999)))));
    }

    private static OpenSloDocumentView sli(String name) {
        return new OpenSloDocumentView(
                "2",
                "openslo/v1/SLI/" + name,
                1,
                false,
                "SLI",
                name,
                Map.of(
                        "spec", Map.of(
                                "ratioMetric", Map.of(
                                        "good", Map.of(
                                                "metricSource", Map.of(
                                                        "metricSourceRef", "payment-prometheus",
                                                        "spec", Map.of("query", "good_query"))),
                                        "total", Map.of(
                                                "metricSource", Map.of(
                                                        "metricSourceRef", "payment-prometheus",
                                                        "spec", Map.of("query", "total_query")))))));
    }
}
