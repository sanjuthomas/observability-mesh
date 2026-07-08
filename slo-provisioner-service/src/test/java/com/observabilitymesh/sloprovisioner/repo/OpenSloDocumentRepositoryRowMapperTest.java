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

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenSloDocumentRepositoryRowMapperTest {

    @Mock JdbcTemplate jdbcTemplate;

    @Mock ResultSet resultSet;

    private OpenSloDocumentRepository repository;

    @BeforeEach
    void setUp() {
        SloProvisionerProperties properties = new SloProvisionerProperties(
                60_000, "service_level_objectives", "slo_provision_state",
                "/rules", "_archive", "", "sloth", "/work", "payment-prometheus");
        repository = new OpenSloDocumentRepository(jdbcTemplate, properties, new ObjectMapper());
    }

    @Test
    void rowMapperHandlesNullContent() throws Exception {
        when(resultSet.getString("id")).thenReturn("doc-1");
        when(resultSet.getString("logical_key")).thenReturn("openslo/v1/SLO/demo");
        when(resultSet.getInt("version")).thenReturn(1);
        when(resultSet.getBoolean("stale")).thenReturn(false);
        when(resultSet.getString("kind")).thenReturn("SLO");
        when(resultSet.getString("name")).thenReturn("demo");
        when(resultSet.getString("content")).thenReturn(null);

        doAnswer(invocation -> {
            RowMapper<OpenSloDocumentView> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(any(String.class), any(RowMapper.class), eq("SLO"));

        assertThat(repository.listActiveByKind("SLO").getFirst().content()).isEmpty();
    }

    @Test
    void rowMapperParsesJsonContent() throws Exception {
        when(resultSet.getString("id")).thenReturn("doc-1");
        when(resultSet.getString("logical_key")).thenReturn("openslo/v1/SLO/demo");
        when(resultSet.getInt("version")).thenReturn(2);
        when(resultSet.getBoolean("stale")).thenReturn(false);
        when(resultSet.getString("kind")).thenReturn("SLO");
        when(resultSet.getString("name")).thenReturn("demo");
        when(resultSet.getString("content")).thenReturn("{\"spec\":{\"service\":\"payments\"}}");

        doAnswer(invocation -> {
            RowMapper<OpenSloDocumentView> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(any(String.class), any(RowMapper.class), eq("SLO"));

        OpenSloDocumentView view = repository.listActiveByKind("SLO").getFirst();

        assertThat(view.id()).isEqualTo("doc-1");
        assertThat(view.content()).isEqualTo(Map.of("spec", Map.of("service", "payments")));
    }
}
