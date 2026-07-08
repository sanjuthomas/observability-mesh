package com.observabilitymesh.sloauthor.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UiControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UiController()).build();
    }

    @Test
    void servesIndexHtml() throws Exception {
        mockMvc.perform(get("/ui/"))
            .andExpect(status().isOk());
    }

    @Test
    void servesIndexWithoutTrailingSlash() throws Exception {
        mockMvc.perform(get("/ui"))
            .andExpect(status().isOk());
    }
}
