package com.workos.workos_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the Admin roster CORS fix: {@code /api/accounts} (and the API in
 * general) must return {@code Access-Control-Allow-Origin} for the allowed
 * frontend origin on every kind of response — a successful preflight, a
 * clean 401 from a controller, and (the actual bug) any response that
 * doesn't go through a cleanly-resolved controller method. Origins outside
 * the configured allowlist must get no such header, and the header must
 * never be a wildcard given credentials are enabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CorsConfigurationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvcTester mvc;

    @Test
    void preflightForGetAccountsFromTheFrontendOriginSucceedsWithCredentialedHeaders() {
        MvcTestResult result = mvc.options().uri("/api/accounts")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange();

        assertThat(result).hasStatus2xxSuccessful();
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(ALLOWED_ORIGIN);
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .isEqualTo("true");
    }

    @Test
    void credentialedResponsesNeverUseAWildcardOrigin() {
        MvcTestResult result = mvc.options().uri("/api/accounts")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange();

        String allowOrigin = result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat(allowOrigin).isNotEqualTo("*");
    }

    @Test
    void unauthenticatedGetAccountsIsRejectedWith401AndStillCarriesCorsHeaders() {
        MvcTestResult result = mvc.get().uri("/api/accounts")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange();

        assertThat(result).hasStatus(401);
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(ALLOWED_ORIGIN);
    }

    @Test
    void anInvalidAdminSessionOnGetAccountsIsRejectedWith401AndStillCarriesCorsHeaders() {
        MvcTestResult result = mvc.get().uri("/api/accounts")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .cookie(new jakarta.servlet.http.Cookie("workos_admin_session", "not-a-real-admin-token"))
                .exchange();

        assertThat(result).hasStatus(401);
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(ALLOWED_ORIGIN);
    }

    @Test
    void aDisallowedOriginGetsNoAccessControlAllowOriginHeader() {
        MvcTestResult result = mvc.get().uri("/api/accounts")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .exchange();

        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void corsDoesNotBypassAdminAuthenticationEvenWithAValidOrigin() {
        MvcTestResult result = mvc.get().uri("/api/accounts")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.error").isNotNull();
    }
}
