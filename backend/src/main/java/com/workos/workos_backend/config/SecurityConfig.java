package com.workos.workos_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security is added in this phase for its password-hashing support
 * ({@link PasswordEncoder}) and to take explicit control of the filter
 * chain, not to replace this codebase's existing request-authorization
 * model. Real per-endpoint access-role enforcement (spec section 4/9-12) is
 * a later RBAC phase; until then every request is permitted at this layer,
 * and identity is instead required or rejected deeper in the call, by
 * {@link com.workos.workos_backend.actor.SessionActingPersonResolver} (for
 * every existing controller that already depends on {@code
 * ActingPersonResolver}) and directly by {@link
 * com.workos.workos_backend.controller.AuthController} for {@code
 * GET /api/auth/me}. Leaving this filter chain at "permit all" is what
 * keeps every already-working endpoint from suddenly requiring Spring
 * Security's own default login here.
 *
 * <p>CSRF protection is disabled: this is a JSON API with no
 * server-rendered forms, and the session cookie's {@code SameSite=Lax}
 * attribute (set by whichever controller issues it) already prevents the
 * cookie from being sent on cross-site requests that CSRF protection
 * exists to guard against.
 *
 * <p>Session creation policy is stateless — Spring Security must not start
 * managing its own {@code HttpSession}/{@code JSESSIONID}, since identity
 * here is tracked entirely through the app-owned {@code sessions} table and
 * its own cookie (see {@link com.workos.workos_backend.entity.Session}).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
