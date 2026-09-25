package com.workos.workos_backend.bootstrap;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.workos.workos_backend.entity.Admin;
import com.workos.workos_backend.repository.AdminRepository;

/**
 * Idempotent initial-Admin bootstrap, registered in every Spring profile
 * (unlike {@link com.workos.workos_backend.dev.LocalDevPeopleSeeder}, which
 * is {@code local-dev}-only) - a real deployment still needs one Admin
 * account to be able to create any employee account at all.
 *
 * <p>Admin is a separate, system-level privilege from the 8 employee
 * access roles (Phase 3's Admin clarification): this seeder never touches
 * {@code Person}/{@code accessRole}, never promotes an existing demo
 * account, and never grants Admin based on a Person having the CEO access
 * role. It only ever creates a row in the dedicated {@code admins} table.
 *
 * <p>Idempotency: checks whether <em>any</em> Admin already exists (not a
 * fixed id or the currently-configured email) before inserting, so a
 * restart - even with different {@code APP_INITIAL_ADMIN_EMAIL}/
 * {@code APP_INITIAL_ADMIN_PASSWORD} values than a prior run - never
 * creates a second Admin. The initial password is never logged; only the
 * email (not a secret) appears in the startup log.
 *
 * <p>If no Admin exists yet and the required environment variables are
 * absent, startup fails loudly (matching {@code LocalDevPeopleSeeder}'s
 * "fail loudly rather than silently degrade" pattern) rather than starting
 * with no Admin and no way to ever create an employee account.
 */
@Component
public class AdminBootstrapSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapSeeder.class);

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final String initialAdminEmail;
    private final String initialAdminPassword;

    public AdminBootstrapSeeder(AdminRepository adminRepository, PasswordEncoder passwordEncoder,
            @Value("${app.initial-admin.email:}") String initialAdminEmail,
            @Value("${app.initial-admin.password:}") String initialAdminPassword) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.initialAdminEmail = initialAdminEmail;
        this.initialAdminPassword = initialAdminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminRepository.count() > 0) {
            log.info("Admin bootstrap: an Admin account already exists, skipping.");
            return;
        }
        if (initialAdminEmail == null || initialAdminEmail.isBlank()
                || initialAdminPassword == null || initialAdminPassword.isBlank()) {
            throw new IllegalStateException(
                    "No Admin account exists yet, and APP_INITIAL_ADMIN_EMAIL/APP_INITIAL_ADMIN_PASSWORD "
                            + "are not set. Set both environment variables to bootstrap the initial Admin account.");
        }

        Admin admin = new Admin(UUID.randomUUID().toString(), initialAdminEmail,
                passwordEncoder.encode(initialAdminPassword), Instant.now());
        adminRepository.save(admin);
        log.info("Admin bootstrap: created the initial Admin account for {}.", initialAdminEmail);
    }
}
