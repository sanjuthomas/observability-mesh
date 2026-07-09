package com.observabilitymesh.ofac.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.ofac.model.OfacScanRequestView;
import com.observabilitymesh.ofac.repo.OfacScanRequestNotFoundException;
import com.observabilitymesh.ofac.repo.OfacScanRequestRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UiControllerTest {

    @Mock OfacScanRequestRepository repository;
    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;
    private final Subject admin = new Subject(
            "admin-001", "Admin", "User", "Platform Admin", "FICC",
            List.of("PLATFORM_ADMIN"), List.of(), null, List.of("FICC"), null, List.of());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UiController(repository, subjectResolver)).build();
        when(subjectResolver.resolveActor(any())).thenReturn(admin);
    }

    @Test
    void uiIndexServesHtml() throws Exception {
        mockMvc.perform(get("/ui/")).andExpect(status().isOk());
    }

    @Test
    void uiScanRequestDetailServesHtml() throws Exception {
        mockMvc.perform(get("/ui/scan-requests/P-1")).andExpect(status().isOk());
    }

    @Test
    void apiUiScanRequestsListsRecords() throws Exception {
        when(repository.listCurrent(null, null, null, 200))
                .thenReturn(List.of(sampleView("P-1", 2)));

        mockMvc.perform(get("/api/ui/scan-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.scan_requests[0].payment_id").value("P-1"));
    }

    @Test
    void apiUiScanRequestsAppliesFilters() throws Exception {
        when(repository.listCurrent("FICC", "OPEN", null, 200))
                .thenReturn(List.of(sampleView("P-1", 2)));

        mockMvc.perform(get("/api/ui/scan-requests")
                        .param("owning_lob", "FICC")
                        .param("lifecycle_status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void apiUiScanRequestNotFound() throws Exception {
        when(repository.getCurrent("missing", 1))
                .thenThrow(new OfacScanRequestNotFoundException("missing", 1));
        mockMvc.perform(get("/api/ui/scan-requests/missing").param("payment_version", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiUiScanRequestReturnsRecord() throws Exception {
        when(repository.getCurrent("P-1", 2)).thenReturn(sampleView("P-1", 2));

        mockMvc.perform(get("/api/ui/scan-requests/P-1").param("payment_version", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scan_request.payment_id").value("P-1"))
                .andExpect(jsonPath("$.scan_request.payment_version").value(2));
    }

    private static OfacScanRequestView sampleView(String paymentId, int paymentVersion) {
        return new OfacScanRequestView(
                paymentId,
                paymentVersion,
                1,
                "I-1",
                "FICC",
                Map.of("account_id", "D-1"),
                Map.of("account_id", "C-1"),
                "Acme",
                List.of(),
                "OPEN",
                null,
                "2026-07-08T12:00:00Z",
                "2026-07-08T12:00:00Z",
                "CURRENT");
    }
}
