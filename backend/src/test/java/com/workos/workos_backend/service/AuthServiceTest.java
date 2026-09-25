package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Session;
import com.workos.workos_backend.exception.UnauthorizedException;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.SessionRepository;

/**
 * Exercises AuthService's login/logout/session-resolution rules directly,
 * against the seeded local-dev people (sarah-chen has no access_role/
 * password_hash by default, matching every real demo account today, so
 * tests that need a login-capable account assign one explicitly first).
 */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Person givePersonACredential(String personId, String rawPassword) {
        Person person = personRepository.findById(personId).orElseThrow();
        person.setAccessRole("IT");
        person.setPasswordHash(passwordEncoder.encode(rawPassword));
        return personRepository.save(person);
    }

    @Test
    void loginSucceedsWithCorrectCredentialsAndIssuesASession() {
        givePersonACredential("sarah-chen", "correct-horse");

        AuthService.IssuedSession issued = authService.login("sarah.chen@workos.dev", "correct-horse");

        assertThat(issued.person().getId()).isEqualTo("sarah-chen");
        assertThat(issued.session().getPersonId()).isEqualTo("sarah-chen");
        assertThat(issued.session().getToken()).isNotBlank();
        assertThat(sessionRepository.findById(issued.session().getToken())).isPresent();
    }

    @Test
    void loginRejectsWrongPassword() {
        givePersonACredential("sarah-chen", "correct-horse");

        assertThatThrownBy(() -> authService.login("sarah.chen@workos.dev", "wrong-password"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginRejectsUnknownEmail() {
        assertThatThrownBy(() -> authService.login("nobody@workos.dev", "whatever"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginRejectsAnAccountWithNoAccessRoleOrPasswordYet() {
        // sarah-chen exists (seeded) but has never been assigned a role/password.
        assertThatThrownBy(() -> authService.login("sarah.chen@workos.dev", "demo1234"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginFailureMessagesDoNotDistinguishUnknownEmailFromWrongPassword() {
        givePersonACredential("sarah-chen", "correct-horse");

        String unknownEmailMessage = catchMessage(() -> authService.login("nobody@workos.dev", "whatever"));
        String wrongPasswordMessage = catchMessage(() -> authService.login("sarah.chen@workos.dev", "wrong"));
        String noAccessRoleMessage = catchMessage(() -> authService.login("alex.morgan@workos.dev", "demo1234"));

        assertThat(unknownEmailMessage).isEqualTo(wrongPasswordMessage).isEqualTo(noAccessRoleMessage);
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnauthorizedException");
        } catch (UnauthorizedException ex) {
            return ex.getMessage();
        }
    }

    @Test
    void currentPersonResolvesTheOwnerOfAValidSession() {
        Person person = givePersonACredential("sarah-chen", "correct-horse");
        AuthService.IssuedSession issued = authService.login("sarah.chen@workos.dev", "correct-horse");

        Person resolved = authService.currentPerson(issued.session().getToken());

        assertThat(resolved.getId()).isEqualTo(person.getId());
    }

    @Test
    void currentPersonRejectsAMissingToken() {
        assertThatThrownBy(() -> authService.currentPerson(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void currentPersonRejectsAnUnknownToken() {
        assertThatThrownBy(() -> authService.currentPerson("not-a-real-token"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void currentPersonRejectsAndDeletesAnExpiredSession() {
        Instant past = Instant.now().minusSeconds(60);
        Session expired = new Session("expired-token", "sarah-chen", past.minusSeconds(60), past);
        sessionRepository.save(expired);

        assertThatThrownBy(() -> authService.currentPerson("expired-token"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(sessionRepository.findById("expired-token")).isEmpty();
    }

    @Test
    void logoutRevokesTheSessionSoItCanNoLongerBeUsed() {
        givePersonACredential("sarah-chen", "correct-horse");
        AuthService.IssuedSession issued = authService.login("sarah.chen@workos.dev", "correct-horse");

        authService.logout(issued.session().getToken());

        assertThat(sessionRepository.findById(issued.session().getToken())).isEmpty();
        assertThatThrownBy(() -> authService.currentPerson(issued.session().getToken()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logoutWithNoTokenIsANoOp() {
        authService.logout(null);
    }

    @Test
    void logoutIsIdempotent() {
        givePersonACredential("sarah-chen", "correct-horse");
        AuthService.IssuedSession issued = authService.login("sarah.chen@workos.dev", "correct-horse");

        authService.logout(issued.session().getToken());
        authService.logout(issued.session().getToken());
    }

    @Test
    void changePasswordSucceedsWithTheCorrectCurrentPasswordAndAllowsLoginWithTheNewOne() {
        givePersonACredential("sarah-chen", "correct-horse");
        AuthService.IssuedSession issued = authService.login("sarah.chen@workos.dev", "correct-horse");

        authService.changePassword(issued.session().getToken(), "correct-horse", "new-password-123");

        assertThatThrownBy(() -> authService.login("sarah.chen@workos.dev", "correct-horse"))
                .isInstanceOf(UnauthorizedException.class);
        AuthService.IssuedSession relogin = authService.login("sarah.chen@workos.dev", "new-password-123");
        assertThat(relogin.person().getId()).isEqualTo("sarah-chen");
    }

    @Test
    void changePasswordRejectsAnIncorrectCurrentPassword() {
        givePersonACredential("sarah-chen", "correct-horse");
        AuthService.IssuedSession issued = authService.login("sarah.chen@workos.dev", "correct-horse");

        assertThatThrownBy(() -> authService.changePassword(issued.session().getToken(), "wrong", "new-password-123"))
                .isInstanceOf(UnauthorizedException.class);
        // Original password still works — the failed attempt made no change.
        authService.login("sarah.chen@workos.dev", "correct-horse");
    }

    @Test
    void changePasswordRejectsAnUnauthenticatedCaller() {
        assertThatThrownBy(() -> authService.changePassword(null, "whatever", "new-password-123"))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> authService.changePassword("not-a-real-token", "whatever", "new-password-123"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void changePasswordRevokesTheCallersOtherSessionsButKeepsTheCurrentOneAlive() {
        givePersonACredential("sarah-chen", "correct-horse");
        AuthService.IssuedSession sessionA = authService.login("sarah.chen@workos.dev", "correct-horse");
        AuthService.IssuedSession sessionB = authService.login("sarah.chen@workos.dev", "correct-horse");

        authService.changePassword(sessionA.session().getToken(), "correct-horse", "new-password-123");

        // The session used to make the change survives.
        assertThat(authService.currentPerson(sessionA.session().getToken()).getId()).isEqualTo("sarah-chen");
        // Every other session for this person is revoked.
        assertThatThrownBy(() -> authService.currentPerson(sessionB.session().getToken()))
                .isInstanceOf(UnauthorizedException.class);
    }
}
