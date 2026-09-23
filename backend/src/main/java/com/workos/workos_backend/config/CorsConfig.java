package com.workos.workos_backend.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS support for the Next.js frontend. Allowed origins come from the
 * {@code app.cors.allowed-origins} property (env var {@code CORS_ALLOWED_ORIGINS}),
 * a comma-separated list — defaults to the local Next.js dev server only.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // Required for the browser to send/receive the session cookie
                // cross-origin (spec section 2.4/6.3) once the frontend calls
                // this API with `credentials: "include"` — not yet true today
                // (that's Phase 5), but harmless to enable now since it only
                // relaxes credentialed-request handling for the already
                // explicit origin allowlist above, never a wildcard.
                .allowCredentials(true);
    }
}
