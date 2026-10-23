package com.techblog.platform.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techblog.platform.service.HealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

    private MockMvc mockMvc;
    private StubHealthService healthService;

    @BeforeEach
    void setUp() {
        healthService = new StubHealthService();
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService)).build();
    }

    @Test
    void shouldReturnUpWhenDatabaseIsAvailable() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
    }

    @Test
    void shouldReturnServiceUnavailableWithoutLeakingDetails() throws Exception {
        healthService.fail = true;

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.database").value("DOWN"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    private static final class StubHealthService extends HealthService {

        private boolean fail;

        private StubHealthService() {
            super(new JdbcTemplate());
        }

        @Override
        public void verifyDatabaseConnection() {
            if (fail) {
                throw new RuntimeException("secret connection details");
            }
        }
    }
}

