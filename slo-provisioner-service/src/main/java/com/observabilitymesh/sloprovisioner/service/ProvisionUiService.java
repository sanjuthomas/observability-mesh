package com.observabilitymesh.sloprovisioner.service;

import com.observabilitymesh.sloprovisioner.model.OpenSloContentReader;
import com.observabilitymesh.sloprovisioner.model.OpenSloDocumentView;
import com.observabilitymesh.sloprovisioner.model.ProvisionStatus;
import com.observabilitymesh.sloprovisioner.model.SloProvisionState;
import com.observabilitymesh.sloprovisioner.repo.OpenSloDocumentRepository;
import com.observabilitymesh.sloprovisioner.repo.SloProvisionStateRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProvisionUiService {

    private final OpenSloDocumentRepository documentRepository;
    private final SloProvisionStateRepository provisionStateRepository;
    private final RulesFileManager rulesFileManager;

    public ProvisionUiService(
            OpenSloDocumentRepository documentRepository,
            SloProvisionStateRepository provisionStateRepository,
            RulesFileManager rulesFileManager) {
        this.documentRepository = documentRepository;
        this.provisionStateRepository = provisionStateRepository;
        this.rulesFileManager = rulesFileManager;
    }

    public List<Map<String, Object>> listSlos(String statusFilter, int limit) {
        Map<String, SloProvisionState> statesByKey = provisionStateRepository.listAll().stream()
                .collect(Collectors.toMap(SloProvisionState::logicalKey, Function.identity(), (a, b) -> a));
        return documentRepository.listActiveByKind("SLO").stream()
                .map(slo -> toSloUiMap(slo, statesByKey.get(slo.logicalKey()), false))
                .filter(row -> matchesProvisionStatus(statusFilter, row))
                .limit(Math.min(limit, 500))
                .toList();
    }

    public Map<String, Object> getSlo(String name) {
        OpenSloDocumentView slo = documentRepository.findActiveByKindAndName("SLO", name)
                .orElseThrow(() -> new ProvisionDocumentNotFoundException("SLO", name));
        SloProvisionState state = provisionStateRepository.findByLogicalKey(slo.logicalKey());
        return toSloUiMap(slo, state, true);
    }

    public List<Map<String, Object>> listSlis(int limit) {
        return documentRepository.listActiveByKind("SLI").stream()
                .map(sli -> toSliUiMap(sli, false))
                .limit(Math.min(limit, 500))
                .toList();
    }

    public Map<String, Object> getSli(String name) {
        OpenSloDocumentView sli = documentRepository.findActiveByKindAndName("SLI", name)
                .orElseThrow(() -> new ProvisionDocumentNotFoundException("SLI", name));
        return toSliUiMap(sli, true);
    }

    private Map<String, Object> toSloUiMap(OpenSloDocumentView slo, SloProvisionState state, boolean includeDetails) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", slo.name());
        map.put("logical_key", slo.logicalKey());
        map.put("version", slo.version());
        map.put("display_name", OpenSloContentReader.displayName(slo.content(), slo.name()));
        map.put("service", OpenSloContentReader.service(slo.content()));
        map.put("description", OpenSloContentReader.description(slo.content()));
        map.put("indicator_ref", OpenSloContentReader.indicatorRef(slo.content()));
        map.put("objective_target", OpenSloContentReader.objectiveTarget(slo.content()));
        map.put("time_window", OpenSloContentReader.timeWindowLabel(slo.content()));

        if (state == null) {
            map.put("provision_status", "NOT_PROVISIONED");
            map.put("rules_file_name", null);
            map.put("last_synced_at", null);
            map.put("last_error", null);
            map.put("prometheus_rules", null);
        } else {
            map.put("provision_status", state.status().name());
            map.put("rules_file_name", state.rulesFileName());
            map.put("last_synced_at", state.lastSyncedAt().toString());
            map.put("last_error", state.lastError());
            map.put(
                    "prometheus_rules",
                    state.status() == ProvisionStatus.ACTIVE
                            ? rulesFileManager.readActiveRulesContent(slo.name()).orElse(null)
                            : null);
        }

        String indicatorRef = OpenSloContentReader.indicatorRef(slo.content());
        if (includeDetails && indicatorRef != null && !indicatorRef.isBlank()) {
            documentRepository.findActiveByKindAndName("SLI", indicatorRef)
                    .ifPresent(sli -> map.put("indicator", toSliUiMap(sli, true)));
        }
        if (includeDetails) {
            map.put("content", slo.content());
        }
        return map;
    }

    private static Map<String, Object> toSliUiMap(OpenSloDocumentView sli, boolean includeContent) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", sli.name());
        map.put("logical_key", sli.logicalKey());
        map.put("version", sli.version());
        map.put("datasource", OpenSloContentReader.datasourceRef(sli.content()));
        map.put("good_query", OpenSloContentReader.goodQuery(sli.content()));
        map.put("total_query", OpenSloContentReader.totalQuery(sli.content()));
        if (includeContent) {
            map.put("content", sli.content());
        }
        return map;
    }

    private static boolean matchesProvisionStatus(String statusFilter, Map<String, Object> row) {
        if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
            return true;
        }
        Object status = row.get("provision_status");
        return status != null && statusFilter.equalsIgnoreCase(String.valueOf(status));
    }
}
