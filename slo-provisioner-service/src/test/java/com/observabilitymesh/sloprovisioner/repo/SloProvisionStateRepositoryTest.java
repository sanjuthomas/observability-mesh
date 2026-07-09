package com.observabilitymesh.sloprovisioner.repo;

import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import com.observabilitymesh.sloprovisioner.model.ProvisionStatus;
import com.observabilitymesh.sloprovisioner.model.SloProvisionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SloProvisionStateRepositoryTest {

    @Mock JdbcTemplate jdbcTemplate;

    private SloProvisionStateRepository repository;

    @BeforeEach
    void setUp() {
        SloProvisionerProperties properties = new SloProvisionerProperties(
                60_000, "service_level_objectives", "slo_provision_state",
                "/rules", "_archive", "", "sloth", "/work", "payment-prometheus");
        repository = new SloProvisionStateRepository(jdbcTemplate, properties);
    }

    @Test
    void findByLogicalKeyReturnsNullWhenMissing() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("missing")))
                .thenReturn(List.of());
        assertThat(repository.findByLogicalKey("missing")).isNull();
    }

    @Test
    void findByLogicalKeyReturnsState() {
        SloProvisionState state = new SloProvisionState(
                "key", 2, ProvisionStatus.ACTIVE, "demo.yml", "abc", Instant.parse("2026-01-01T00:00:00Z"), null);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("key")))
                .thenReturn(List.of(state));

        SloProvisionState found = repository.findByLogicalKey("key");

        assertThat(found.status()).isEqualTo(ProvisionStatus.ACTIVE);
        assertThat(found.opensloVersion()).isEqualTo(2);
    }

    @Test
    void listByStatusMapsDocuments() {
        SloProvisionState state = new SloProvisionState(
                "key", 1, ProvisionStatus.ARCHIVED, "demo.yml", "abc", Instant.now(), "gone");
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("ARCHIVED")))
                .thenReturn(List.of(state));

        List<SloProvisionState> states = repository.listByStatus(ProvisionStatus.ARCHIVED);

        assertThat(states).hasSize(1);
        assertThat(states.getFirst().lastError()).isEqualTo("gone");
    }

    @Test
    void listAllReturnsStates() {
        SloProvisionState state = new SloProvisionState(
                "key", 1, ProvisionStatus.ACTIVE, "demo.yml", "abc", Instant.now(), null);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class)))
                .thenReturn(List.of(state));

        assertThat(repository.listAll()).hasSize(1);
    }

    @Test
    void upsertPersistsRow() {
        Instant syncedAt = Instant.parse("2026-01-01T00:00:00Z");
        SloProvisionState state = new SloProvisionState(
                "key", 1, ProvisionStatus.ACTIVE, "demo.yml", "hash", syncedAt, null);

        repository.upsert(state);

        verify(jdbcTemplate).update(
                any(String.class),
                eq("key"),
                eq(1),
                eq("ACTIVE"),
                eq("demo.yml"),
                eq("hash"),
                eq(Timestamp.from(syncedAt)),
                eq(null));
    }
}
