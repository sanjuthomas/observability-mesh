package com.observabilitymesh.sloprovisioner.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.sloprovisioner.config.SloProvisionerProperties;
import com.observabilitymesh.sloprovisioner.model.OpenSloDocumentView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class OpenSloDocumentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final String table;
    private final RowMapper<OpenSloDocumentView> rowMapper;

    public OpenSloDocumentRepository(
            JdbcTemplate jdbcTemplate, SloProvisionerProperties properties, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = SqlIdentifiers.requireTableName(properties.opensloTable());
        this.rowMapper = (rs, rowNum) -> mapRow(rs, objectMapper);
    }

    public List<OpenSloDocumentView> listActiveByKind(String kind) {
        String sql = """
                SELECT id::text, logical_key, version, stale, kind, name, content
                FROM %s
                WHERE stale = false AND kind = ?
                """.formatted(table);
        return jdbcTemplate.query(sql, rowMapper, kind);
    }

    public Optional<OpenSloDocumentView> findActiveByKindAndName(String kind, String name) {
        String sql = """
                SELECT id::text, logical_key, version, stale, kind, name, content
                FROM %s
                WHERE stale = false AND kind = ? AND name = ?
                LIMIT 1
                """.formatted(table);
        List<OpenSloDocumentView> rows = jdbcTemplate.query(sql, rowMapper, kind, name);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<String> listActiveSloLogicalKeys() {
        return listActiveByKind("SLO").stream().map(OpenSloDocumentView::logicalKey).toList();
    }

    private static OpenSloDocumentView mapRow(ResultSet rs, ObjectMapper objectMapper) throws SQLException {
        Map<String, Object> content = readContent(rs, objectMapper);
        return new OpenSloDocumentView(
                rs.getString("id"),
                rs.getString("logical_key"),
                rs.getInt("version"),
                rs.getBoolean("stale"),
                rs.getString("kind"),
                rs.getString("name"),
                content);
    }

    private static Map<String, Object> readContent(ResultSet rs, ObjectMapper objectMapper) throws SQLException {
        String json = rs.getString("content");
        if (json == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new SQLException("Failed to parse OpenSLO content JSON", e);
        }
    }
}
