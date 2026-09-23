package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Slice test for the API foundation: the health endpoint and the global
 * error-handling convention. Deliberately does not load the full
 * application context (no datasource needed).
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void healthEndpointReturnsUp() {
        MvcTestResult result = mvc.get().uri("/api/health").exchange();
        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void unmappedRouteReturnsFrontendErrorConvention() {
        MvcTestResult result = mvc.get().uri("/api/does-not-exist").exchange();
        assertThat(result)
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.error")
                .isEqualTo("Resource not found");
    }
}
