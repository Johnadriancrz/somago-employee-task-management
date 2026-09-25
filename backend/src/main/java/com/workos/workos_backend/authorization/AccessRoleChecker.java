package com.workos.workos_backend.authorization;

import org.springframework.stereotype.Component;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.repository.PersonRepository;

/**
 * Resolves an actor's persisted {@code accessRole} for Phase 4's
 * role-permission checks. Always re-reads the actor's {@link Person} row by
 * the server-resolved actor id (the id {@code ActingPersonResolver} already
 * verified) — never trusts a client-supplied role, mirroring the same
 * non-negotiable identity source every other authorization check in this
 * codebase already relies on.
 *
 * <p>A person with no {@code accessRole} (every carried-forward demo
 * account, spec section 13.3, until a CEO/Admin explicitly assigns one)
 * never matches any role check here — {@code null} never equals a role
 * name, so such an actor is always treated as the least-privileged case,
 * never silently promoted.
 */
@Component
public class AccessRoleChecker {

    private final PersonRepository personRepository;

    public AccessRoleChecker(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    /** True if the actor exists and their persisted accessRole is one of {@code roles}. */
    public boolean actorHasAnyRole(String actorId, String... roles) {
        Person actor = personRepository.findById(actorId).orElse(null);
        if (actor == null || actor.getAccessRole() == null) {
            return false;
        }
        for (String role : roles) {
            if (role.equals(actor.getAccessRole())) {
                return true;
            }
        }
        return false;
    }
}
