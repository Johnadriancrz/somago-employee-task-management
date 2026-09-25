package com.workos.workos_backend.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.exception.ConflictException;
import com.workos.workos_backend.repository.PersonRepository;

/**
 * Employee account creation (Phase 3, spec section 5.2/14). Only reachable
 * through the Admin-gated {@code POST /api/accounts} endpoint - this
 * service itself does not check the caller's identity, that is the
 * controller's job (spec: identity/Admin privilege is never trusted from
 * anything this service receives).
 */
@Service
public class AccountService {

    /**
     * The 8 fixed employee access roles (spec section 3). "Admin" is
     * intentionally not in this set - Admin is a separate, system-level
     * privilege, never one of the 8 roles an employee account can hold.
     */
    public static final Set<String> ACCESS_ROLES = Set.of(
            "CEO", "HR", "IT", "Graphics Designer", "Marketing",
            "Operation Manager", "Sales Assistant", "Sales Manager");

    private static final String DEFAULT_CHIP_CLASS = "bg-surface-container-high text-primary";

    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(PersonRepository personRepository, PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a new employee {@link Person} with a hashed password and the
     * given access role. The form has no Job Title field, so the existing
     * free-text {@code role} column (a separate concept from
     * {@code accessRole}, spec section 2.5) is initialized to the chosen
     * access role's display text as a reasonable default, not a mapping
     * between the two concepts.
     */
    @Transactional
    public Person createAccount(String name, String email, String password, String accessRole) {
        if (!ACCESS_ROLES.contains(accessRole)) {
            throw new IllegalArgumentException(
                    "accessRole must be one of the 8 approved employee access roles");
        }

        String trimmedName = name.trim();
        String trimmedEmail = email.trim();
        if (personRepository.findByEmail(trimmedEmail).isPresent()) {
            throw new ConflictException("An account with this email already exists");
        }

        Person person = new Person(
                UUID.randomUUID().toString(),
                trimmedName,
                trimmedEmail,
                initialsFrom(trimmedName),
                accessRole,
                DEFAULT_CHIP_CLASS);
        person.setAccessRole(accessRole);
        person.setPasswordHash(passwordEncoder.encode(password));
        return personRepository.save(person);
    }

    /**
     * The persisted employee-account roster for the Admin page (Admin-gated
     * by the controller, not here - same convention as {@link
     * #createAccount}). Only {@link Person} rows with a non-null {@code
     * accessRole} qualify - see {@link PersonRepository#findByAccessRoleIsNotNullOrderByNameAsc()}.
     */
    @Transactional(readOnly = true)
    public List<Person> listAccounts() {
        return personRepository.findByAccessRoleIsNotNullOrderByNameAsc();
    }

    /** Matches WorkspaceService's initialsFrom() exactly, for the same "two-letter chip initials" convention. */
    private static String initialsFrom(String name) {
        String[] words = name.trim().split("\\s+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        String initials = first + second;
        if (initials.isEmpty()) {
            initials = name.length() >= 2 ? name.substring(0, 2) : name;
        }
        return initials.toUpperCase();
    }
}
