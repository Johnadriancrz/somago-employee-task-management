package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Admin;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.repository.AdminRepository;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.service.AccountService;
import com.workos.workos_backend.service.AdminAuthService;
import com.workos.workos_backend.service.AuthService;

import jakarta.servlet.http.Cookie;

/**
 * HTTP-level tests for POST /api/accounts: Admin-only account creation.
 * Uses the seeded local-dev people to exercise "a CEO/other employee access
 * role alone cannot create accounts" and "existing demo accounts are
 * unaffected".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class AccountControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String createAdminAndLogIn(String email, String rawPassword) {
        adminRepository.save(new Admin(UUID.randomUUID().toString(), email, passwordEncoder.encode(rawPassword),
                Instant.now()));
        MvcTestResult result = mvc.post().uri("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + rawPassword + "\"}")
                .exchange();
        return result.getResponse().getCookie(AdminAuthService.COOKIE_NAME).getValue();
    }

    private String logInAsPersonWithAccessRole(String personId, String email, String rawPassword,
            String accessRole) {
        Person person = personRepository.findById(personId).orElseThrow();
        person.setAccessRole(accessRole);
        person.setPasswordHash(passwordEncoder.encode(rawPassword));
        personRepository.save(person);

        MvcTestResult result = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + rawPassword + "\"}")
                .exchange();
        return result.getResponse().getCookie(AuthService.COOKIE_NAME).getValue();
    }

    private static String accountBody(String name, String email, String password, String accessRole) {
        return "{\"name\": \"" + name + "\", \"email\": \"" + email + "\", \"password\": \"" + password
                + "\", \"accessRole\": \"" + accessRole + "\"}";
    }

    @Test
    void adminCanCreateAnAccountForEachOfTheEightApprovedAccessRoles() {
        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");

        int i = 0;
        for (String role : AccountService.ACCESS_ROLES) {
            String email = "person-" + (i++) + "@workos.dev";
            MvcTestResult result = mvc.post().uri("/api/accounts")
                    .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(accountBody("New " + role, email, "Password123!", role))
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.accessRole").isEqualTo(role);
            assertThat(result).bodyJson().extractingPath("$.email").isEqualTo(email);
        }
    }

    @Test
    void createAccountResponseNeverIncludesPasswordOrPasswordHash() throws java.io.UnsupportedEncodingException {
        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");

        MvcTestResult result = mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "Password123!", "IT"))
                .exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("password").doesNotContain("passwordHash");
    }

    @Test
    void passwordIsHashedBeforePersistence() {
        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");

        mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "Password123!", "IT"))
                .exchange();

        Person created = personRepository.findByEmail("new.hire@workos.dev").orElseThrow();
        assertThat(created.getPasswordHash()).isNotEqualTo("Password123!");
        assertThat(passwordEncoder.matches("Password123!", created.getPasswordHash())).isTrue();
    }

    @Test
    void createdAccountAlwaysGetsAServerGeneratedIdNeverClientSupplied() {
        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");

        MvcTestResult result = mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "Password123!", "IT"))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isNotEqualTo("sarah-chen");
        assertThat(result).bodyJson().extractingPath("$.id").asString().hasSize(36);
    }

    @Test
    void ceoAccessRoleAloneCannotCreateAccountsWith403() {
        String ceoToken = logInAsPersonWithAccessRole(
                "alex-morgan", "alex.morgan@workos.dev", "correct-horse", "CEO");

        MvcTestResult result = mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AuthService.COOKIE_NAME, ceoToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "Password123!", "IT"))
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void otherEmployeeAccessRolesCannotCreateAccountsWith403() {
        String hrToken = logInAsPersonWithAccessRole(
                "priya-patel", "priya.patel@workos.dev", "correct-horse", "HR");

        MvcTestResult result = mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AuthService.COOKIE_NAME, hrToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "Password123!", "IT"))
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void unauthenticatedRequestsAreRejectedWith401() {
        MvcTestResult result = mvc.post().uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "Password123!", "IT"))
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void anInvalidSessionCookieOfEitherKindWithNoOtherSessionIsRejectedWith401() {
        MvcTestResult result = mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, "not-a-real-admin-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "Password123!", "IT"))
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void duplicateEmailIsRejectedWith409() {
        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");
        mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("First", "duplicate@workos.dev", "Password123!", "IT"))
                .exchange();

        MvcTestResult result = mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("Second", "duplicate@workos.dev", "Password123!", "HR"))
                .exchange();

        assertThat(result).hasStatus(409);
    }

    @Test
    void alreadySeededDemoAccountEmailIsRejectedAsDuplicate() {
        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");

        MvcTestResult result = mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("Impersonator", "sarah.chen@workos.dev", "Password123!", "IT"))
                .exchange();

        assertThat(result).hasStatus(409);
    }

    @Test
    void invalidAccessRoleIsRejectedWith400() {
        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");

        MvcTestResult result = mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "Password123!", "Astronaut"))
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void adminIsRejectedAsAnEmployeeAccessRoleWith400() {
        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");

        MvcTestResult result = mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "Password123!", "Admin"))
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void tooShortPasswordIsRejectedWith400() {
        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");

        MvcTestResult result = mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "short", "IT"))
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void adminCanListPersistedEmployeeAccounts() {
        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");
        mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "Password123!", "IT"))
                .exchange();

        MvcTestResult result = mvc.get().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[*].email").asArray().contains("new.hire@workos.dev");
    }

    @Test
    void listAccountsExcludesSeededDemoPeopleWithNoAccessRole() {
        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");

        MvcTestResult result = mvc.get().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[*].id").asArray().doesNotContain("sarah-chen");
    }

    @Test
    void listAccountsResponseNeverIncludesPasswordOrPasswordHash() throws java.io.UnsupportedEncodingException {
        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");
        mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "Password123!", "IT"))
                .exchange();

        MvcTestResult result = mvc.get().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("password").doesNotContain("passwordHash");
    }

    @Test
    void unauthenticatedRequestsToListAccountsAreRejectedWith401() {
        MvcTestResult result = mvc.get().uri("/api/accounts").exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void employeeSessionsCannotAccessTheAdminRosterEvenAsCeo() {
        String ceoToken = logInAsPersonWithAccessRole(
                "alex-morgan", "alex.morgan@workos.dev", "correct-horse", "CEO");

        MvcTestResult result = mvc.get().uri("/api/accounts")
                .cookie(new Cookie(AuthService.COOKIE_NAME, ceoToken))
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void existingDemoAccountsRemainUnchangedAfterAccountCreation() {
        Person before = personRepository.findById("sarah-chen").orElseThrow();
        assertThat(before.getAccessRole()).isNull();
        assertThat(before.getPasswordHash()).isNull();

        String adminToken = createAdminAndLogIn("root@workos.dev", "correct-horse");
        mvc.post().uri("/api/accounts")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountBody("New Hire", "new.hire@workos.dev", "Password123!", "IT"))
                .exchange();

        Person after = personRepository.findById("sarah-chen").orElseThrow();
        assertThat(after.getAccessRole()).isNull();
        assertThat(after.getPasswordHash()).isNull();
        assertThat(after.getName()).isEqualTo(before.getName());
        assertThat(after.getEmail()).isEqualTo(before.getEmail());
    }
}
