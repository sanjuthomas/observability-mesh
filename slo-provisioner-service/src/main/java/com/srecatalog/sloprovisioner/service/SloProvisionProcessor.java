package com.srecatalog.sloprovisioner.service;

import com.srecatalog.sloprovisioner.compile.OpenSloCompilationException;
import com.srecatalog.sloprovisioner.compile.OpenSloV1ToSlothCompiler;
import com.srecatalog.sloprovisioner.config.SloProvisionerProperties;
import com.srecatalog.sloprovisioner.model.OpenSloDocumentView;
import com.srecatalog.sloprovisioner.model.ProvisionStatus;
import com.srecatalog.sloprovisioner.model.SloProvisionState;
import com.srecatalog.sloprovisioner.prometheus.PrometheusReloader;
import com.srecatalog.sloprovisioner.prometheus.PrometheusReloadException;
import com.srecatalog.sloprovisioner.repo.OpenSloDocumentRepository;
import com.srecatalog.sloprovisioner.repo.SloProvisionStateRepository;
import com.srecatalog.sloprovisioner.sloth.SlothCliRunner;
import com.srecatalog.sloprovisioner.sloth.SlothExecutionException;
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

    @Scheduled(fixedDelayString = "${sre-catalog.slo-provisioner.poll-interval-ms:60000}")
    public void pollAndProvision() {
        boolean reloadNeeded = false;
        Set<String> activeLogicalKeys = new HashSet<>();

        for (OpenSloDocumentView slo : openSloDocumentRepository.listActiveByKind("SLO")) {
            activeLogicalKeys.add(slo.logicalKey());
            if (syncActiveSlo(slo)) {
                reloadNeeded = true;
            }
        }

        for (SloProvisionState state : provisionStateRepository.listByStatus(ProvisionStatus.ACTIVE)) {
            if (!activeLogicalKeys.contains(state.logicalKey())) {
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
