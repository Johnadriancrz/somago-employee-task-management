package com.workos.workos_backend.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Admin;
import com.workos.workos_backend.repository.AdminRepository;

/**
 * Exercises AdminBootstrapSeeder's idempotency contract directly. The
 * application's own bootstrap bean already ran once at context startup
 * (using the test-only credentials in src/test/resources/application.properties),
 * so several tests here start from "an Admin already exists" - matching
 * real-world reruns.
 */
@SpringBootTest
@Transactional
class AdminBootstrapSeederTest {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void firstTimeBootstrapCreatesTheAdminFromConfiguredCredentials() {
        adminRepository.deleteAll();

        new AdminBootstrapSeeder(adminRepository, passwordEncoder, "founder@workos.dev", "Founder-Passw0rd!")
                .run(null);

        assertThat(adminRepository.count()).isEqualTo(1);
        Admin admin = adminRepository.findByEmail("founder@workos.dev").orElseThrow();
        assertThat(passwordEncoder.matches("Founder-Passw0rd!", admin.getPasswordHash())).isTrue();
    }

    @Test
    void rerunningAfterAnAdminAlreadyExistsIsANoOpAndCreatesNoDuplicate() {
        // The application's own bootstrap bean already created one admin at context startup.
        long before = adminRepository.count();
        assertThat(before).isGreaterThanOrEqualTo(1);

        new AdminBootstrapSeeder(adminRepository, passwordEncoder, "someone-else@workos.dev", "Different-Passw0rd!")
                .run(null);

        assertThat(adminRepository.count()).isEqualTo(before);
        assertThat(adminRepository.findByEmail("someone-else@workos.dev")).isEmpty();
    }

    @Test
    void noOpsEvenWhenTheExistingAdminHasADifferentEmailThanTheConfiguredOne() {
        adminRepository.deleteAll();
        adminRepository.save(new Admin(java.util.UUID.randomUUID().toString(), "already-here@workos.dev",
                passwordEncoder.encode("whatever"), java.time.Instant.now()));

        new AdminBootstrapSeeder(adminRepository, passwordEncoder, "different-config@workos.dev", "Some-Passw0rd!")
                .run(null);

        assertThat(adminRepository.count()).isEqualTo(1);
        assertThat(adminRepository.findByEmail("different-config@workos.dev")).isEmpty();
    }

    @Test
    void failsStartupLoudlyWhenNoAdminExistsAndCredentialsAreMissing() {
        adminRepository.deleteAll();

        AdminBootstrapSeeder seeder = new AdminBootstrapSeeder(adminRepository, passwordEncoder, "", "");

        assertThatThrownBy(() -> seeder.run(null)).isInstanceOf(IllegalStateException.class);
        assertThat(adminRepository.count()).isZero();
    }

    @Test
    void neverPersistsThePlaintextPassword() {
        adminRepository.deleteAll();

        new AdminBootstrapSeeder(adminRepository, passwordEncoder, "founder@workos.dev", "Founder-Passw0rd!")
                .run(null);

        Admin admin = adminRepository.findByEmail("founder@workos.dev").orElseThrow();
        assertThat(admin.getPasswordHash()).isNotEqualTo("Founder-Passw0rd!");
        assertThat(admin.getPasswordHash()).doesNotContain("Founder-Passw0rd!");
    }
}
