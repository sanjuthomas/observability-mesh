package com.observabilitymesh.sloprovisioner.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import com.observabilitymesh.sloprovisioner.model.OpenSloDocumentView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenSloDocumentRepositoryTest {

    @Mock JdbcTemplate jdbcTemplate;

    private OpenSloDocumentRepository repository;

    @BeforeEach
    void setUp() {
        SloProvisionerProperties properties = new SloProvisionerProperties(
                60_000, "service_level_objectives", "slo_provision_state",
                "/rules", "_archive", "", "sloth", "/work", "payment-prometheus");
        repository = new OpenSloDocumentRepository(jdbcTemplate, properties, new ObjectMapper());
    }

    @Test
    void listActiveByKindMapsDocuments() {
        OpenSloDocumentView view = new OpenSloDocumentView(
                "1", "openslo/v1/SLO/demo", 1, false, "SLO", "demo", Map.of("spec", Map.of()));
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("SLO")))
                .thenReturn(List.of(view));

        List<OpenSloDocumentView> views = repository.listActiveByKind("SLO");

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().name()).isEqualTo("demo");
    }

    @Test
    void listActiveSloLogicalKeysReturnsKeys() {
        OpenSloDocumentView view = new OpenSloDocumentView(
                "1", "openslo/v1/SLO/demo", 1, false, "SLO", "demo", Map.of());
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("SLO")))
                .thenReturn(List.of(view));

        assertThat(repository.listActiveSloLogicalKeys()).containsExactly("openslo/v1/SLO/demo");
    }

    @Test
    void findActiveByKindAndNameReturnsEmptyWhenMissing() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("SLI"), eq("missing")))
                .thenReturn(List.of());
        assertThat(repository.findActiveByKindAndName("SLI", "missing")).isEmpty();
    }

    @Test
    void findActiveByKindAndNameReturnsOptional() {
        OpenSloDocumentView view = new OpenSloDocumentView(
                "2", "openslo/v1/SLI/demo", 1, false, "SLI", "demo", Map.of());
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("SLI"), eq("demo")))
                .thenReturn(List.of(view));

        assertThat(repository.findActiveByKindAndName("SLI", "demo")).isPresent();
    }
}
