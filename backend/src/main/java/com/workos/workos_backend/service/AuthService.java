package com.workos.workos_backend.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Session;
import com.workos.workos_backend.exception.UnauthorizedException;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.SessionRepository;

/**
 * Backend-owned authentication: password verification and persistent,
 * revocable session issuance/validation (spec section 6.3). Never resolves
 * an actor from anything other than a verified session token — the same
 * non-negotiable constraint documented on {@link
 * com.workos.workos_backend.actor.ActingPersonResolver}.
 */
@Service
public class AuthService {

    /** Shared with {@link com.workos.workos_backend.actor.SessionActingPersonResolver}. */
    public static final String COOKIE_NAME = "workos_session";

    /** Matches the existing (Next.js) {@code workos_session} cookie's 30-day maxAge (spec section 6.3). */
    public static final Duration SESSION_DURATION = Duration.ofDays(30);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PersonRepository personRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(PersonRepository personRepository, SessionRepository sessionRepository,
            PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Verifies credentials and issues a new session. A person with no
     * {@code accessRole} or no {@code passwordHash} yet (every demo person
     * today, per spec section 13.3) is rejected with the same generic
     * message as a wrong password or unknown email — never a
     * distinguishable error, so a caller can't use this endpoint to probe
     * which emails exist or which accounts are usable.
     */
    @Transactional
    public IssuedSession login(String email, String password) {
        Person person = personRepository.findByEmail(email).orElse(null);
        if (person == null
                || person.getAccessRole() == null
                || person.getPasswordHash() == null
                || !passwordEncoder.matches(password, person.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        Instant now = Instant.now();
        Session session = new Session(generateToken(), person.getId(), now, now.plus(SESSION_DURATION));
        sessionRepository.saveAndFlush(session);
        return new IssuedSession(session, person);
    }

    /** Idempotent: revoking a token that doesn't exist (already logged out, or none supplied) is a no-op. */
    @Transactional
    public void logout(String token) {
        if (token != null) {
            sessionRepository.deleteById(token);
        }
    }

    /**
     * Resolves the authenticated {@link Person} for a session token,
     * rejecting a missing, unknown, or expired session with the same
     * {@link UnauthorizedException} used everywhere else identity can't be
     * verified. An expired session is deleted on the way out rather than
     * left to linger.
     */
    @Transactional
    public Person currentPerson(String token) {
        if (token == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        Session session = sessionRepository.findById(token)
                .orElseThrow(() -> new UnauthorizedException("Not authenticated"));
        if (session.isExpired(Instant.now())) {
            // Flush first: deleteById() no-ops on a Persistable whose isNew()
            // flag hasn't yet flipped to false, which only happens once the
            // entity's insert has actually been flushed (see AssignedIdEntity).
            sessionRepository.flush();
            sessionRepository.deleteById(token);
            throw new UnauthorizedException("Session expired");
        }
        return personRepository.findById(session.getPersonId())
                .orElseThrow(() -> new UnauthorizedException("Not authenticated"));
    }

    /**
     * Changes the password of the person identified by {@code token} (never
     * a client-supplied personId). Requires the correct current password —
     * verified the same way {@link #login} verifies it, including rejecting
     * an account with no password hash yet — then re-hashes and persists
     * the new one with the same {@link PasswordEncoder} used everywhere
     * else. Every other session belonging to this person is revoked so a
     * stolen/shared session can't outlive the password that granted it;
     * the session tied to {@code token} itself is left alone since it just
     * proved possession of the current password.
     */
    @Transactional
    public void changePassword(String token, String currentPassword, String newPassword) {
        Person person = currentPerson(token);
        if (person.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, person.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }

        person.setPasswordHash(passwordEncoder.encode(newPassword));
        personRepository.saveAndFlush(person);
        sessionRepository.deleteByPersonIdAndTokenNot(person.getId(), token);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedSession(Session session, Person person) {
    }
}
