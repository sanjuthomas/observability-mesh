package com.observabilitymesh.harness.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UiControllerTest {

    @Test
    void indexForwardsToStaticHtml() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UiController()).build();
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/index.html")).andExpect(status().isOk());
    }
}
