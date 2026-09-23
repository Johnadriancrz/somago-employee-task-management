package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Slice test for the API foundation: the health endpoint and the global
 * error-handling convention. Deliberately does not load the full
 * application context (no datasource needed).
 *
 * <p>{@code addFilters = false} skips servlet filters for this slice,
 * including the security filter chain Spring Boot auto-configures by
 * default once {@code spring-boot-starter-security} is on the classpath
 * (this slice doesn't import the app's own permit-all {@code SecurityConfig}
 * bean, so without this, Spring Boot's default deny-by-default security
 * would apply here only, unlike every full-context test elsewhere in the
 * suite).
 */
@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
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
