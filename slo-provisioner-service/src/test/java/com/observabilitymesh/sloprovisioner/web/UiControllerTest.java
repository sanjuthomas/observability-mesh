package com.observabilitymesh.sloprovisioner.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.sloprovisioner.service.ProvisionDocumentNotFoundException;
import com.observabilitymesh.sloprovisioner.service.ProvisionUiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UiControllerTest {

    @Mock ProvisionUiService provisionUiService;
    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;
    private final Subject admin = new Subject(
            "admin-001", "Admin", "User", "Platform Admin", "FICC",
            List.of("PLATFORM_ADMIN"), List.of(), null, List.of("FICC"), null, List.of());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UiController(provisionUiService, subjectResolver)).build();
        when(subjectResolver.resolveActor(any())).thenReturn(admin);
    }

    @Test
    void uiIndexServesHtml() throws Exception {
        mockMvc.perform(get("/ui/")).andExpect(status().isOk());
    }

    @Test
    void apiUiSlosListsRecords() throws Exception {
        when(provisionUiService.listSlos("ALL", 200))
                .thenReturn(List.of(Map.of("name", "demo-slo", "provision_status", "ACTIVE")));

        mockMvc.perform(get("/api/ui/slos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.slos[0].name").value("demo-slo"));
    }

    @Test
    void apiUiSloNotFound() throws Exception {
        when(provisionUiService.getSlo("missing"))
                .thenThrow(new ProvisionDocumentNotFoundException("SLO", "missing"));
        mockMvc.perform(get("/api/ui/slos/missing")).andExpect(status().isNotFound());
    }

    @Test
    void apiUiSlisListsRecords() throws Exception {
        when(provisionUiService.listSlis(200))
                .thenReturn(List.of(Map.of("name", "demo-sli", "datasource", "payment-prometheus")));

        mockMvc.perform(get("/api/ui/slis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.slis[0].name").value("demo-sli"));
    }

    @Test
    void apiUiSliReturnsRecord() throws Exception {
        when(provisionUiService.getSli("demo-sli"))
                .thenReturn(Map.of("name", "demo-sli", "good_query", "sum(1)"));

        mockMvc.perform(get("/api/ui/slis/demo-sli"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sli.name").value("demo-sli"));
    }

    @Test
    void uiSloDetailServesHtml() throws Exception {
        mockMvc.perform(get("/ui/slos/demo-slo")).andExpect(status().isOk());
    }

    @Test
    void uiSliDetailServesHtml() throws Exception {
        mockMvc.perform(get("/ui/slis/demo-sli")).andExpect(status().isOk());
    }

    @Test
    void apiUiSliNotFound() throws Exception {
        when(provisionUiService.getSli("missing"))
                .thenThrow(new ProvisionDocumentNotFoundException("SLI", "missing"));
        mockMvc.perform(get("/api/ui/slis/missing")).andExpect(status().isNotFound());
    }
}
