package com.observabilitymesh.sloprovisioner.repo;

import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import com.observabilitymesh.sloprovisioner.model.ProvisionStatus;
import com.observabilitymesh.sloprovisioner.model.SloProvisionState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class SloProvisionStateRepository {

    private final JdbcTemplate jdbcTemplate;
    private final String table;
    private final RowMapper<SloProvisionState> rowMapper = SloProvisionStateRepository::mapRow;

    public SloProvisionStateRepository(JdbcTemplate jdbcTemplate, SloProvisionerProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = SqlIdentifiers.requireTableName(properties.provisionStateTable());
    }

    public SloProvisionState findByLogicalKey(String logicalKey) {
        String sql = """
                SELECT logical_key, openslo_version, status, rules_file_name, content_hash, last_synced_at, last_error
                FROM %s
                WHERE logical_key = ?
                """.formatted(table);
        List<SloProvisionState> rows = jdbcTemplate.query(sql, rowMapper, logicalKey);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<SloProvisionState> listByStatus(ProvisionStatus status) {
        String sql = """
                SELECT logical_key, openslo_version, status, rules_file_name, content_hash, last_synced_at, last_error
                FROM %s
                WHERE status = ?
                """.formatted(table);
        return jdbcTemplate.query(sql, rowMapper, status.name());
    }

    public void upsert(SloProvisionState state) {
        String sql = """
                INSERT INTO %s (logical_key, openslo_version, status, rules_file_name, content_hash, last_synced_at, last_error)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (logical_key) DO UPDATE SET
                  openslo_version = EXCLUDED.openslo_version,
                  status = EXCLUDED.status,
                  rules_file_name = EXCLUDED.rules_file_name,
                  content_hash = EXCLUDED.content_hash,
                  last_synced_at = EXCLUDED.last_synced_at,
                  last_error = EXCLUDED.last_error
                """.formatted(table);
        jdbcTemplate.update(
                sql,
                state.logicalKey(),
                state.opensloVersion(),
                state.status().name(),
                state.rulesFileName(),
                state.contentHash(),
                Timestamp.from(state.lastSyncedAt()),
                state.lastError());
    }

    private static SloProvisionState mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp syncedAt = rs.getTimestamp("last_synced_at");
        Instant lastSyncedAt = syncedAt != null ? syncedAt.toInstant() : Instant.EPOCH;
        return new SloProvisionState(
                rs.getString("logical_key"),
                rs.getInt("openslo_version"),
                ProvisionStatus.valueOf(rs.getString("status")),
                rs.getString("rules_file_name"),
                rs.getString("content_hash"),
                lastSyncedAt,
                rs.getString("last_error"));
    }
}
