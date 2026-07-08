package com.observabilitymesh.instruction.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.security.SecurityEventRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityEventUiControllerTest {

    @Mock SecurityEventRepository securityEventRepository;
    @Mock RequestSubjectResolver subjectResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityEventUiController controller = new SecurityEventUiController(
                securityEventRepository, subjectResolver, InstructionTestFixtures.properties());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(subjectResolver.resolveActor(any())).thenReturn(InstructionTestFixtures.ADMIN);
    }

    @Test
    void listEventsUsesDefaultLimit() throws Exception {
        when(securityEventRepository.listRecent(200)).thenReturn(List.of(Map.of("event_id", "SE-1")));

        mockMvc.perform(get("/api/ui/security-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void getEventReturns404WhenMissing() throws Exception {
        when(securityEventRepository.findByEventId("missing")).thenReturn(null);

        mockMvc.perform(get("/api/ui/security-events/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void servesSecurityEventsIndex() throws Exception {
        mockMvc.perform(get("/ui/security-events/"))
                .andExpect(status().isOk());
    }

    @Test
    void servesEventDetailHtml() throws Exception {
        mockMvc.perform(get("/ui/security-events/events/SE-1"))
                .andExpect(status().isOk());
    }

    @Test
    void listEventsUsesConfiguredDefaultWhenLimitNonPositive() throws Exception {
        when(securityEventRepository.listRecent(200)).thenReturn(List.of());

        mockMvc.perform(get("/api/ui/security-events").param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void getEventReturnsPayloadWhenFound() throws Exception {
        when(securityEventRepository.findByEventId("SE-1")).thenReturn(Map.of("event_id", "SE-1"));

        mockMvc.perform(get("/api/ui/security-events/SE-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.event_id").value("SE-1"));
    }

    @Test
    void servesSecurityEventsIndexWithoutTrailingSlash() throws Exception {
        mockMvc.perform(get("/ui/security-events"))
                .andExpect(status().isOk());
    }

    @Test
    void listEventsHonorsPositiveLimit() throws Exception {
        when(securityEventRepository.listRecent(50)).thenReturn(List.of());

        mockMvc.perform(get("/api/ui/security-events").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }
}
