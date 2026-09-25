package com.workos.workos_backend.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS support for the Next.js frontend. Allowed origins come from the
 * {@code app.cors.allowed-origins} property (env var {@code CORS_ALLOWED_ORIGINS}),
 * a comma-separated list — defaults to the local Next.js dev server only.
 *
 * <p>Exposed as a {@link CorsConfigurationSource} bean, which {@link
 * SecurityConfig} wires into the Spring Security filter chain via {@code
 * .cors(...)}. That registers CORS as an actual {@code CorsFilter} that runs
 * ahead of authentication/authorization and MVC dispatch, so every response —
 * including a 401/403 from a controller, a 404, or an unexpected 500 —
 * reliably carries {@code Access-Control-Allow-Origin}. A prior version of
 * this class used {@code WebMvcConfigurer#addCorsMappings}, which only adds
 * CORS headers once a specific handler method is resolved and invoked; a
 * request that errors out before that point (or via a path that bypasses
 * normal MVC exception handling) came back with no CORS headers at all —
 * which is exactly what the browser surfaces as a CORS failure even though
 * the underlying problem is unrelated to the origin allowlist itself.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Required for the browser to send/receive the session cookies (the
        // employee `workos_session` cookie and the separate Admin
        // `workos_admin_session` cookie) cross-origin once the frontend
        // calls this API with `credentials: "include"`. Only ever paired
        // with the explicit origin allowlist above, never a wildcard —
        // browsers reject allowCredentials(true) combined with "*" anyway.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
