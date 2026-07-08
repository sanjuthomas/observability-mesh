package com.observabilitymesh.sloauthor.web;

import com.observabilitymesh.sloauthor.dto.OpenSloDetailDto;
import com.observabilitymesh.sloauthor.dto.OpenSloSummaryDto;
import com.observabilitymesh.sloauthor.exception.DuplicateOpenSloException;
import com.observabilitymesh.sloauthor.exception.GlobalExceptionHandler;
import com.observabilitymesh.sloauthor.exception.OpenSloNotFoundException;
import com.observabilitymesh.sloauthor.exception.OpenSloValidationException;
import com.observabilitymesh.sloauthor.service.OpenSloService;
import com.observabilitymesh.sloauthor.service.YamlConversionService;
import com.observabilitymesh.sloauthor.support.OpenSloTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OpenSloControllerTest {

    @Mock
    private OpenSloService openSloService;

    @Mock
    private YamlConversionService yamlConversionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OpenSloController controller = new OpenSloController(openSloService, yamlConversionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listsActiveDocuments() throws Exception {
        when(openSloService.listActive()).thenReturn(List.of(
            new OpenSloSummaryDto("id", "openslo/v1/Service/svc", "openslo/v1", "Service", "svc", "Svc", 1, false,
                Instant.parse("2026-01-01T00:00:00Z"), "openslo")
        ));

        mockMvc.perform(get("/api/v1/documents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("svc"));
    }

    @Test
    void createsDocument() throws Exception {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("checkout");
        when(openSloService.create(any())).thenReturn(detail("openslo/v1/Service/checkout", content));

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":{"apiVersion":"openslo/v1","kind":"Service","metadata":{"name":"checkout"},"spec":{}}}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.logicalKey").value("openslo/v1/Service/checkout"));
    }

    @Test
    void updatesDocumentWithEncodedLogicalKey() throws Exception {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("checkout");
        when(openSloService.update(eq("openslo/v1/Service/checkout"), any())).thenReturn(detail("openslo/v1/Service/checkout", content));

        mockMvc.perform(put("/api/v1/documents/openslo~v1~Service~checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":{"apiVersion":"openslo/v1","kind":"Service","metadata":{"name":"checkout"},"spec":{}}}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void validatesDocument() throws Exception {
        when(openSloService.buildLogicalKey(any())).thenReturn("openslo/v1/Service/checkout");

        mockMvc.perform(post("/api/v1/documents/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":{"apiVersion":"openslo/v1","kind":"Service","metadata":{"name":"checkout"},"spec":{}}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void parsesYaml() throws Exception {
        when(yamlConversionService.parseYaml(any())).thenReturn(OpenSloTestSupport.serviceDocument("checkout"));

        mockMvc.perform(post("/api/v1/documents/parse-yaml")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"yaml\":\"kind: Service\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("Service"));
    }

    @Test
    void returnsConflictForDuplicate() throws Exception {
        when(openSloService.create(any())).thenThrow(new DuplicateOpenSloException("openslo/v1/Service/checkout"));

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":{"apiVersion":"openslo/v1","kind":"Service","metadata":{"name":"checkout"},"spec":{}}}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void returnsNotFound() throws Exception {
        when(openSloService.getActiveByLogicalKey("missing")).thenThrow(new OpenSloNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/documents/missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsBadRequestForValidationFailure() throws Exception {
        when(openSloService.create(any())).thenThrow(new OpenSloValidationException("invalid"));

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":{"apiVersion":"openslo/v1","kind":"Service","metadata":{"name":"checkout"},"spec":{}}}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getsDocumentById() throws Exception {
        Map<String, Object> content = OpenSloTestSupport.serviceDocument("checkout");
        when(openSloService.getById("mongo-id")).thenReturn(detail("openslo/v1/Service/checkout", content));

        mockMvc.perform(get("/api/v1/documents/id/mongo-id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("id"));
    }

    @Test
    void checksExistence() throws Exception {
        when(openSloService.existsActive("openslo/v1/Service/checkout")).thenReturn(true);

        mockMvc.perform(get("/api/v1/documents/exists/openslo~v1~Service~checkout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exists").value(true));
    }

    @Test
    void serializesToYaml() throws Exception {
        when(yamlConversionService.toYaml(any())).thenReturn("kind: Service");

        mockMvc.perform(post("/api/v1/documents/to-yaml")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":{"apiVersion":"openslo/v1","kind":"Service","metadata":{"name":"checkout"},"spec":{}}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.yaml").value("kind: Service"));
    }

    @Test
    void listsVersions() throws Exception {
        when(openSloService.listVersions("openslo/v1/Service/checkout")).thenReturn(List.of(
            new OpenSloSummaryDto("id", "openslo/v1/Service/checkout", "openslo/v1", "Service", "checkout", null, 2, false,
                Instant.parse("2026-01-01T00:00:00Z"), "openslo")
        ));

        mockMvc.perform(get("/api/v1/documents/openslo~v1~Service~checkout/versions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].version").value(2));
    }

    private OpenSloDetailDto detail(String logicalKey, Map<String, Object> content) {
        return new OpenSloDetailDto("id", logicalKey, 1, false, content,
            Instant.parse("2026-01-01T00:00:00Z"), "openslo");
    }
}
