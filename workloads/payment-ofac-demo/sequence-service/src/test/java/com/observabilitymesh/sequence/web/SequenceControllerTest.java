package com.observabilitymesh.sequence.web;

import com.observabilitymesh.sequence.model.NextSecurityEventSequenceRequest;
import com.observabilitymesh.sequence.model.NextSecurityEventSequenceResponse;
import com.observabilitymesh.sequence.model.NextSequenceRequest;
import com.observabilitymesh.sequence.model.NextSequenceResponse;
import com.observabilitymesh.sequence.service.SequenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SequenceController.class)
class SequenceControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SequenceService sequenceService;

    @Test
    void healthReturnsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void nextSequenceEndpointReturnsPayload() throws Exception {
        when(sequenceService.next(any(NextSequenceRequest.class)))
                .thenReturn(new NextSequenceResponse(
                        "20260707-FICC-I-1", "20260707", "FICC", "I", 1L, "20260707-FICC-I"));

        mockMvc.perform(post("/api/v1/sequences/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessDate":"20260707","owningLob":"FICC","entityType":"I"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sequenceId").value("20260707-FICC-I-1"));
    }

    @Test
    void nextSecurityEventEndpointReturnsPayload() throws Exception {
        when(sequenceService.nextSecurityEvent(any(NextSecurityEventSequenceRequest.class)))
                .thenReturn(new NextSecurityEventSequenceResponse(
                        "inst-1-SE-2", "inst-1", 2L, "inst-1-SE"));

        mockMvc.perform(post("/api/v1/sequences/security-events/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":"inst-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sequenceId").value("inst-1-SE-2"));
    }
}
