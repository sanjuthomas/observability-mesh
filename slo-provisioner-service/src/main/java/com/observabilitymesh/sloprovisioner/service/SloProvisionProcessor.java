package com.observabilitymesh.sloprovisioner.service;

import com.observabilitymesh.sloprovisioner.compile.OpenSloCompilationException;
import com.observabilitymesh.sloprovisioner.compile.OpenSloMetricAlertCompiler;
import com.observabilitymesh.sloprovisioner.compile.OpenSloV1ToSlothCompiler;
import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import com.observabilitymesh.sloprovisioner.model.OpenSloDocumentView;
import com.observabilitymesh.sloprovisioner.model.ProvisionStatus;
import com.observabilitymesh.sloprovisioner.model.SloProvisionState;
import com.observabilitymesh.sloprovisioner.prometheus.PrometheusReloader;
import com.observabilitymesh.sloprovisioner.prometheus.PrometheusReloadException;
import com.observabilitymesh.sloprovisioner.repo.OpenSloDocumentRepository;
import com.observabilitymesh.sloprovisioner.repo.SloProvisionStateRepository;
import com.observabilitymesh.sloprovisioner.sloth.SlothCliRunner;
import com.observabilitymesh.sloprovisioner.sloth.SlothExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SloProvisionProcessor {

    private static final Logger log = LoggerFactory.getLogger(SloProvisionProcessor.class);

    private final OpenSloDocumentRepository openSloDocumentRepository;
    private final SloProvisionStateRepository provisionStateRepository;
    private final SloProvisionerProperties properties;
    private final SlothCliRunner slothCliRunner;
    private final RulesFileManager rulesFileManager;
    private final PrometheusReloader prometheusReloader;
    private final ContentHasher contentHasher;
    private final Path workDir;

    public SloProvisionProcessor(
            OpenSloDocumentRepository openSloDocumentRepository,
            SloProvisionStateRepository provisionStateRepository,
            SloProvisionerProperties properties,
            SlothCliRunner slothCliRunner,
            RulesFileManager rulesFileManager,
            PrometheusReloader prometheusReloader,
            ContentHasher contentHasher) throws IOException {
        this.openSloDocumentRepository = openSloDocumentRepository;
        this.provisionStateRepository = provisionStateRepository;
        this.properties = properties;
        this.slothCliRunner = slothCliRunner;
        this.rulesFileManager = rulesFileManager;
        this.prometheusReloader = prometheusReloader;
        this.contentHasher = contentHasher;
        this.workDir = Path.of(properties.workDir());
        Files.createDirectories(workDir);
    }

    @Scheduled(fixedDelayString = "${observability-mesh.slo-provisioner.poll-interval-ms:60000}")
    public void pollAndProvision() {
        boolean reloadNeeded = false;
        Set<String> activeLogicalKeys = new HashSet<>();

        for (OpenSloDocumentView slo : openSloDocumentRepository.listActiveByKind("SLO")) {
            activeLogicalKeys.add(slo.logicalKey());
            if (syncActiveSlo(slo)) {
                reloadNeeded = true;
            }
        }

        Set<String> activeAlertPolicyKeys = new HashSet<>();
        for (OpenSloDocumentView alertPolicy : openSloDocumentRepository.listActiveByKind("AlertPolicy")) {
            activeAlertPolicyKeys.add(alertPolicy.logicalKey());
            if (syncActiveAlertPolicy(alertPolicy)) {
                reloadNeeded = true;
            }
        }

        for (SloProvisionState state : provisionStateRepository.listByStatus(ProvisionStatus.ACTIVE)) {
            if (state.logicalKey().contains("/AlertPolicy/") && !activeAlertPolicyKeys.contains(state.logicalKey())) {
                if (archiveAlertPolicy(state)) {
                    reloadNeeded = true;
                }
            } else if (!state.logicalKey().contains("/AlertPolicy/")
                    && !activeLogicalKeys.contains(state.logicalKey())) {
                if (archiveSlo(state)) {
                    reloadNeeded = true;
                }
            }
        }

        if (reloadNeeded) {
            try {
                prometheusReloader.reload();
            } catch (PrometheusReloadException ex) {
                log.warn("prometheus reload after SLO provision failed: {}", ex.getMessage());
            }
        }
    }

    boolean syncActiveSlo(OpenSloDocumentView slo) {
        try {
            String indicatorRef = indicatorRef(slo);
            OpenSloDocumentView sli = openSloDocumentRepository.findActiveByKindAndName("SLI", indicatorRef)
                    .orElseThrow(() -> new OpenSloCompilationException(
                            "active SLI not found for indicatorRef=" + indicatorRef));

            String slothInput = OpenSloV1ToSlothCompiler.compile(
                    slo, sli, properties.datasourceNameSet());
            String contentHash = contentHasher.sha256(slothInput);

            SloProvisionState existing = provisionStateRepository.findByLogicalKey(slo.logicalKey());
            if (existing != null
                    && existing.status() == ProvisionStatus.ACTIVE
                    && existing.opensloVersion() == slo.version()
                    && contentHash.equals(existing.contentHash())) {
                return false;
            }

            Path inputFile = workDir.resolve(safeWorkName(slo.name()) + "-input.yml");
            Path outputFile = workDir.resolve(safeWorkName(slo.name()) + "-rules.yml");
            Files.writeString(inputFile, slothInput, StandardCharsets.UTF_8);
            slothCliRunner.generate(inputFile, outputFile);
            rulesFileManager.publishActiveRules(slo.name(), outputFile);

            provisionStateRepository.upsert(new SloProvisionState(
                    slo.logicalKey(),
                    slo.version(),
                    ProvisionStatus.ACTIVE,
                    RulesFileManager.safeFileName(slo.name()) + ".yml",
                    contentHash,
                    Instant.now(),
                    null));
            log.info("provisioned SLO logicalKey={} version={} rules={}",
                    slo.logicalKey(), slo.version(), slo.name());
            return true;
        } catch (RuntimeException | IOException ex) {
            log.error("failed to provision SLO logicalKey={}: {}", slo.logicalKey(), ex.getMessage());
            provisionStateRepository.upsert(new SloProvisionState(
                    slo.logicalKey(),
                    slo.version(),
                    ProvisionStatus.FAILED,
                    RulesFileManager.safeFileName(slo.name()) + ".yml",
                    "",
                    Instant.now(),
                    ex.getMessage()));
            return false;
        }
    }

    boolean syncActiveAlertPolicy(OpenSloDocumentView alertPolicy) {
        try {
            String conditionName = alertPolicyConditionRef(alertPolicy);
            OpenSloDocumentView alertCondition = openSloDocumentRepository
                    .findActiveByKindAndName("AlertCondition", conditionName)
                    .orElseThrow(() -> new OpenSloCompilationException(
                            "active AlertCondition not found for conditionRef=" + conditionName));

            String sliName = OpenSloMetricAlertCompiler.metadataAnnotation(
                    (Map<String, Object>) alertCondition.content(), OpenSloMetricAlertCompiler.SLI_REF_ANNOTATION);
            if (sliName.isBlank()) {
                throw new OpenSloCompilationException(
                        "AlertCondition metadata.annotations."
                                + OpenSloMetricAlertCompiler.SLI_REF_ANNOTATION
                                + " is required for metric-threshold alerts");
            }

            OpenSloDocumentView sli = openSloDocumentRepository.findActiveByKindAndName("SLI", sliName)
                    .orElseThrow(() -> new OpenSloCompilationException(
                            "active SLI not found for annotation "
                                    + OpenSloMetricAlertCompiler.SLI_REF_ANNOTATION
                                    + "="
                                    + sliName));

            String rulesYaml = OpenSloMetricAlertCompiler.compile(
                    alertPolicy, alertCondition, sli, properties.datasourceNameSet());
            String contentHash = contentHasher.sha256(rulesYaml);
            String rulesFileName = "alert-" + RulesFileManager.safeFileName(alertPolicy.name()) + ".yml";

            SloProvisionState existing = provisionStateRepository.findByLogicalKey(alertPolicy.logicalKey());
            if (existing != null
                    && existing.status() == ProvisionStatus.ACTIVE
                    && existing.opensloVersion() == alertPolicy.version()
                    && contentHash.equals(existing.contentHash())) {
                return false;
            }

            Path outputFile = workDir.resolve(safeWorkName(alertPolicy.name()) + "-alert-rules.yml");
            Files.writeString(outputFile, rulesYaml, StandardCharsets.UTF_8);
            rulesFileManager.publishAlertPolicyRules(alertPolicy.name(), outputFile);

            provisionStateRepository.upsert(new SloProvisionState(
                    alertPolicy.logicalKey(),
                    alertPolicy.version(),
                    ProvisionStatus.ACTIVE,
                    rulesFileName,
                    contentHash,
                    Instant.now(),
                    null));
            log.info("provisioned AlertPolicy logicalKey={} version={} rules={}",
                    alertPolicy.logicalKey(), alertPolicy.version(), alertPolicy.name());
            return true;
        } catch (RuntimeException | IOException ex) {
            log.error("failed to provision AlertPolicy logicalKey={}: {}", alertPolicy.logicalKey(), ex.getMessage());
            provisionStateRepository.upsert(new SloProvisionState(
                    alertPolicy.logicalKey(),
                    alertPolicy.version(),
                    ProvisionStatus.FAILED,
                    "alert-" + RulesFileManager.safeFileName(alertPolicy.name()) + ".yml",
                    "",
                    Instant.now(),
                    ex.getMessage()));
            return false;
        }
    }

    boolean archiveAlertPolicy(SloProvisionState state) {
        try {
            String policyName = policyNameFromLogicalKey(state.logicalKey());
            rulesFileManager.archiveAlertPolicyRules(policyName);
            provisionStateRepository.upsert(new SloProvisionState(
                    state.logicalKey(),
                    state.opensloVersion(),
                    ProvisionStatus.ARCHIVED,
                    state.rulesFileName(),
                    state.contentHash(),
                    Instant.now(),
                    null));
            log.info("archived AlertPolicy logicalKey={}", state.logicalKey());
            return true;
        } catch (IOException ex) {
            log.error("failed to archive AlertPolicy logicalKey={}: {}", state.logicalKey(), ex.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static String alertPolicyConditionRef(OpenSloDocumentView alertPolicy) {
        Object content = alertPolicy.content();
        if (!(content instanceof Map<?, ?> contentMap)) {
            throw new OpenSloCompilationException("AlertPolicy content is missing");
        }
        Object spec = contentMap.get("spec");
        if (!(spec instanceof Map<?, ?> specMap)) {
            throw new OpenSloCompilationException("AlertPolicy spec is missing");
        }
        Object conditions = specMap.get("conditions");
        if (!(conditions instanceof Iterable<?> iterable)) {
            throw new OpenSloCompilationException("AlertPolicy spec.conditions is required");
        }
        for (Object entry : iterable) {
            if (entry instanceof Map<?, ?> condition) {
                Object conditionRef = condition.get("conditionRef");
                if (conditionRef != null && !String.valueOf(conditionRef).isBlank()) {
                    return String.valueOf(conditionRef).trim();
                }
            }
        }
        throw new OpenSloCompilationException("AlertPolicy spec.conditions[0].conditionRef is required");
    }

    private static String policyNameFromLogicalKey(String logicalKey) {
        int slash = logicalKey.lastIndexOf('/');
        return slash >= 0 ? logicalKey.substring(slash + 1) : logicalKey;
    }

    boolean archiveSlo(SloProvisionState state) {
        try {
            rulesFileManager.archiveRules(fileBaseName(state.rulesFileName()));
            provisionStateRepository.upsert(new SloProvisionState(
                    state.logicalKey(),
                    state.opensloVersion(),
                    ProvisionStatus.ARCHIVED,
                    state.rulesFileName(),
                    state.contentHash(),
                    Instant.now(),
                    null));
            log.info("archived SLO logicalKey={}", state.logicalKey());
            return true;
        } catch (IOException ex) {
            log.error("failed to archive SLO logicalKey={}: {}", state.logicalKey(), ex.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static String indicatorRef(OpenSloDocumentView slo) {
        Object content = slo.content();
        if (!(content instanceof Map<?, ?> contentMap)) {
            throw new OpenSloCompilationException("SLO content is missing");
        }
        Object spec = contentMap.get("spec");
        if (!(spec instanceof Map<?, ?> specMap)) {
            throw new OpenSloCompilationException("SLO spec is missing");
        }
        Object indicatorRef = specMap.get("indicatorRef");
        if (indicatorRef == null || String.valueOf(indicatorRef).isBlank()) {
            throw new OpenSloCompilationException("SLO spec.indicatorRef is required");
        }
        return String.valueOf(indicatorRef);
    }

    private static String safeWorkName(String sloName) {
        return sloName.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private static String fileBaseName(String rulesFileName) {
        if (rulesFileName == null) {
            return "";
        }
        return rulesFileName.endsWith(".yml")
                ? rulesFileName.substring(0, rulesFileName.length() - 4)
                : rulesFileName;
    }
}
