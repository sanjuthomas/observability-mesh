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

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SloProvisionStateRepositoryRowMapperTest {

    @Mock JdbcTemplate jdbcTemplate;

    @Mock ResultSet resultSet;

    private SloProvisionStateRepository repository;

    @BeforeEach
    void setUp() {
        SloProvisionerProperties properties = new SloProvisionerProperties(
                60_000, "service_level_objectives", "slo_provision_state",
                "/rules", "_archive", "", "sloth", "/work", "payment-prometheus");
        repository = new SloProvisionStateRepository(jdbcTemplate, properties);
    }

    @Test
    void rowMapperUsesEpochWhenSyncedAtMissing() throws Exception {
        when(resultSet.getString("logical_key")).thenReturn("key");
        when(resultSet.getInt("openslo_version")).thenReturn(1);
        when(resultSet.getString("status")).thenReturn("ACTIVE");
        when(resultSet.getString("rules_file_name")).thenReturn("demo.yml");
        when(resultSet.getString("content_hash")).thenReturn("hash");
        when(resultSet.getTimestamp("last_synced_at")).thenReturn(null);
        when(resultSet.getString("last_error")).thenReturn(null);

        doAnswer(invocation -> {
            RowMapper<SloProvisionState> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(any(String.class), any(RowMapper.class), eq("key"));

        assertThat(repository.findByLogicalKey("key").lastSyncedAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void rowMapperMapsProvisionState() throws Exception {
        Instant syncedAt = Instant.parse("2026-02-01T12:00:00Z");
        when(resultSet.getString("logical_key")).thenReturn("key");
        when(resultSet.getInt("openslo_version")).thenReturn(3);
        when(resultSet.getString("status")).thenReturn("FAILED");
        when(resultSet.getString("rules_file_name")).thenReturn("demo.yml");
        when(resultSet.getString("content_hash")).thenReturn("hash");
        when(resultSet.getTimestamp("last_synced_at")).thenReturn(Timestamp.from(syncedAt));
        when(resultSet.getString("last_error")).thenReturn("boom");

        doAnswer(invocation -> {
            RowMapper<SloProvisionState> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(any(String.class), any(RowMapper.class), eq("key"));

        SloProvisionState state = repository.findByLogicalKey("key");

        assertThat(state.status()).isEqualTo(ProvisionStatus.FAILED);
        assertThat(state.lastSyncedAt()).isEqualTo(syncedAt);
        assertThat(state.lastError()).isEqualTo("boom");
    }
}
