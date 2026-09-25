package com.workos.workos_backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.authorization.AccessRoleChecker;
import com.workos.workos_backend.authorization.AccessRoles;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.TimeEntry;
import com.workos.workos_backend.exception.ConflictException;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.TimeEntryRepository;

/**
 * Business logic for the Time Clock endpoints (BACKEND.md's Time clock
 * table). The acting person always comes from {@code ActingPersonResolver}
 * (never a request body/param) for clock-in/out and status, matching
 * BACKEND.md: "The signed-in user is always taken from the session for
 * clock-in/out — never from the request body."
 *
 * <p>{@code GET /api/time/entries} is role-scoped (spec section 9): CEO and
 * HR may see any person's entries (or all entries with no filter); every
 * other actor — including one with no {@code accessRole} yet — always sees
 * only their own, regardless of what {@code personIdFilter} the caller asks
 * for. This prevents an IDOR-style bypass by manipulating the
 * {@code personId} query parameter directly.
 *
 * <p>The CEO is exempt from personal clock-in/out (spec section 9):
 * {@link #clockIn} rejects a CEO actor with {@link ForbiddenException},
 * enforced here rather than left to the frontend hiding its clock-in
 * controls, matching this codebase's convention that frontend gating is
 * UX-only and never the actual enforcement point.
 */
@Service
public class TimeEntryService {

    private final TimeEntryRepository timeEntryRepository;
    private final PersonRepository personRepository;
    private final AccessRoleChecker accessRoleChecker;

    public TimeEntryService(TimeEntryRepository timeEntryRepository, PersonRepository personRepository,
            AccessRoleChecker accessRoleChecker) {
        this.timeEntryRepository = timeEntryRepository;
        this.personRepository = personRepository;
        this.accessRoleChecker = accessRoleChecker;
    }

    @Transactional
    public TimeEntry clockIn(String actorId) {
        if (accessRoleChecker.actorHasAnyRole(actorId, AccessRoles.CEO)) {
            throw new ForbiddenException("CEO is exempt from personal time clock");
        }
        if (timeEntryRepository.findByPersonIdAndClockOutIsNull(actorId).isPresent()) {
            throw new ConflictException("Already clocked in");
        }
        Person person = personRepository.findById(actorId)
                .orElseThrow(() -> new IllegalStateException("Acting person not found: " + actorId));
        TimeEntry entry = new TimeEntry(UUID.randomUUID().toString(), person, Instant.now());
        // saveAndFlush: see the matching comment in WorkspaceService.createWorkspace —
        // TimeEntry has the same assigned-id Persistable.isNew() behavior.
        return timeEntryRepository.saveAndFlush(entry);
    }

    @Transactional
    public TimeEntry clockOut(String actorId) {
        TimeEntry entry = timeEntryRepository.findByPersonIdAndClockOutIsNull(actorId)
                .orElseThrow(() -> new ConflictException("Not clocked in"));
        entry.setClockOut(Instant.now());
        return timeEntryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Optional<TimeEntry> getOpenEntry(String actorId) {
        return timeEntryRepository.findByPersonIdAndClockOutIsNull(actorId);
    }

    /**
     * @param actorId the server-resolved caller, whose accessRole decides whether personIdFilter is honored.
     * @param personIdFilter when non-blank and the actor is CEO/HR, scopes to that one person's entries;
     *                       ignored for every other actor, who is always scoped to their own id instead.
     */
    @Transactional(readOnly = true)
    public List<TimeEntry> listEntries(String actorId, String personIdFilter) {
        if (!accessRoleChecker.actorHasAnyRole(actorId, AccessRoles.CEO, AccessRoles.HR)) {
            return timeEntryRepository.findByPersonIdOrderByClockInAsc(actorId);
        }
        if (personIdFilter != null && !personIdFilter.isBlank()) {
            return timeEntryRepository.findByPersonIdOrderByClockInAsc(personIdFilter);
        }
        return timeEntryRepository.findAllByOrderByClockInAsc();
    }
}
