package com.sunil.finintel.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

// The filter is picked up by @WebMvcTest, so this checks the real wiring
@WebMvcTest(HealthController.class)
class RequestIdWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void responseCarriesRequestIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/health").header(RequestIds.HEADER, "req-42"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIds.HEADER, "req-42"));
    }

    @Test
    void errorBodyContainsRequestId() throws Exception {
        mockMvc.perform(post("/api/v1/health").header(RequestIds.HEADER, "req-43"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.requestId").value("req-43"));
    }
}
