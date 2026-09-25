package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Admin;
import com.workos.workos_backend.entity.AdminSession;
import com.workos.workos_backend.exception.UnauthorizedException;
import com.workos.workos_backend.repository.AdminRepository;
import com.workos.workos_backend.repository.AdminSessionRepository;

/** Exercises AdminAuthService's login/logout/session-resolution rules directly. */
@SpringBootTest
@Transactional
class AdminAuthServiceTest {

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private AdminSessionRepository adminSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Admin createAdmin(String email, String rawPassword) {
        Admin admin = new Admin(UUID.randomUUID().toString(), email, passwordEncoder.encode(rawPassword),
                Instant.now());
        return adminRepository.save(admin);
    }

    @Test
    void loginSucceedsWithCorrectCredentialsAndIssuesASession() {
        createAdmin("root@workos.dev", "correct-horse");

        AdminAuthService.IssuedAdminSession issued = adminAuthService.login("root@workos.dev", "correct-horse");

        assertThat(issued.admin().getEmail()).isEqualTo("root@workos.dev");
        assertThat(issued.session().getToken()).isNotBlank();
        assertThat(adminSessionRepository.findById(issued.session().getToken())).isPresent();
    }

    @Test
    void loginRejectsWrongPassword() {
        createAdmin("root@workos.dev", "correct-horse");

        assertThatThrownBy(() -> adminAuthService.login("root@workos.dev", "wrong-password"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginRejectsUnknownEmail() {
        assertThatThrownBy(() -> adminAuthService.login("nobody@workos.dev", "whatever"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void currentAdminResolvesTheOwnerOfAValidSession() {
        Admin admin = createAdmin("root@workos.dev", "correct-horse");
        AdminAuthService.IssuedAdminSession issued = adminAuthService.login("root@workos.dev", "correct-horse");

        Admin resolved = adminAuthService.currentAdmin(issued.session().getToken());

        assertThat(resolved.getId()).isEqualTo(admin.getId());
    }

    @Test
    void currentAdminRejectsAMissingToken() {
        assertThatThrownBy(() -> adminAuthService.currentAdmin(null)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void currentAdminRejectsAnUnknownToken() {
        assertThatThrownBy(() -> adminAuthService.currentAdmin("not-a-real-token"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void currentAdminRejectsAndDeletesAnExpiredSession() {
        Admin admin = createAdmin("root@workos.dev", "correct-horse");
        Instant past = Instant.now().minusSeconds(60);
        AdminSession expired = new AdminSession("expired-admin-token", admin.getId(), past.minusSeconds(60), past);
        // saveAndFlush: an assigned-id entity's Persistable.isNew() only flips to
        // false on the @PostPersist callback (an actual flush) - otherwise the
        // deleteById() below would see isNew()==true and silently no-op.
        adminSessionRepository.saveAndFlush(expired);

        assertThatThrownBy(() -> adminAuthService.currentAdmin("expired-admin-token"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(adminSessionRepository.findById("expired-admin-token")).isEmpty();
    }

    @Test
    void logoutRevokesTheSessionSoItCanNoLongerBeUsed() {
        createAdmin("root@workos.dev", "correct-horse");
        AdminAuthService.IssuedAdminSession issued = adminAuthService.login("root@workos.dev", "correct-horse");

        adminAuthService.logout(issued.session().getToken());

        assertThat(adminSessionRepository.findById(issued.session().getToken())).isEmpty();
        assertThatThrownBy(() -> adminAuthService.currentAdmin(issued.session().getToken()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logoutWithNoTokenIsANoOp() {
        adminAuthService.logout(null);
    }

    @Test
    void logoutIsIdempotent() {
        createAdmin("root@workos.dev", "correct-horse");
        AdminAuthService.IssuedAdminSession issued = adminAuthService.login("root@workos.dev", "correct-horse");

        adminAuthService.logout(issued.session().getToken());
        adminAuthService.logout(issued.session().getToken());
    }
}
