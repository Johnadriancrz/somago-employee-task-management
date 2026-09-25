package com.workos.workos_backend.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Admin;
import com.workos.workos_backend.entity.AdminSession;
import com.workos.workos_backend.exception.UnauthorizedException;
import com.workos.workos_backend.repository.AdminRepository;
import com.workos.workos_backend.repository.AdminSessionRepository;

/**
 * Authentication for the separate, system-level Admin privilege (Phase 3's
 * Admin clarification) - kept entirely apart from {@link AuthService}'s
 * Person/access-role sessions: its own cookie ({@link #COOKIE_NAME}), its
 * own session table, its own password. An Admin is never resolved from a
 * Person row or an {@code accessRole} value.
 */
@Service
public class AdminAuthService {

    /** Shared with {@link com.workos.workos_backend.controller.AdminAuthController}. */
    public static final String COOKIE_NAME = "workos_admin_session";

    /**
     * Shorter-lived than the 30-day employee session (spec section 6.3):
     * Admin can create accounts and is the highest-privilege identity in the
     * system, so its session is tightened rather than matched to the
     * employee default.
     */
    public static final Duration SESSION_DURATION = Duration.ofHours(8);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AdminRepository adminRepository;
    private final AdminSessionRepository adminSessionRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthService(AdminRepository adminRepository, AdminSessionRepository adminSessionRepository,
            PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.adminSessionRepository = adminSessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Same generic failure message for unknown email and wrong password - never a distinguishable error. */
    @Transactional
    public IssuedAdminSession login(String email, String password) {
        Admin admin = adminRepository.findByEmail(email).orElse(null);
        if (admin == null || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        Instant now = Instant.now();
        AdminSession session = new AdminSession(generateToken(), admin.getId(), now, now.plus(SESSION_DURATION));
        // saveAndFlush (not save): AdminSession's assigned-id Persistable.isNew() only
        // flips to false on the @PostPersist callback, which only fires on an actual
        // flush (see WorkspaceService.createWorkspace()'s identical note). Without
        // forcing it here, a later deleteById() in the same transaction (e.g. an
        // immediate logout, or a test) would see isNew()==true and Spring Data JPA
        // would silently no-op the delete (SimpleJpaRepository.delete() skips entities
        // it still considers new).
        adminSessionRepository.saveAndFlush(session);
        return new IssuedAdminSession(session, admin);
    }

    /** Idempotent: revoking a token that doesn't exist (already logged out, or none supplied) is a no-op. */
    @Transactional
    public void logout(String token) {
        if (token != null) {
            adminSessionRepository.deleteById(token);
        }
    }

    /**
     * Resolves the authenticated {@link Admin} for a session token,
     * rejecting a missing, unknown, or expired session with
     * {@link UnauthorizedException}. Never accepts a Person/employee
     * session token in its place - the two cookies/tables are entirely
     * separate.
     */
    @Transactional
    public Admin currentAdmin(String token) {
        if (token == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        AdminSession session = adminSessionRepository.findById(token)
                .orElseThrow(() -> new UnauthorizedException("Not authenticated"));
        if (session.isExpired(Instant.now())) {
            adminSessionRepository.deleteById(token);
            throw new UnauthorizedException("Session expired");
        }
        return adminRepository.findById(session.getAdminId())
                .orElseThrow(() -> new UnauthorizedException("Not authenticated"));
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedAdminSession(AdminSession session, Admin admin) {
    }
}
