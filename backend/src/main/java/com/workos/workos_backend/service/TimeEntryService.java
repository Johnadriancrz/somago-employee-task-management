package com.workos.workos_backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.TimeEntry;
import com.workos.workos_backend.exception.ConflictException;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.TimeEntryRepository;

/**
 * Business logic for the Time Clock endpoints (BACKEND.md's Time clock
 * table). The acting person always comes from {@code ActingPersonResolver}
 * (never a request body/param) for clock-in/out and status, matching
 * BACKEND.md: "The signed-in user is always taken from the session for
 * clock-in/out — never from the request body."
 *
 * <p>{@code GET /api/time/entries} is intentionally open to any actor for
 * any person's entries — this app has no role/permission system yet (see
 * BACKEND.md's Time clock note), so it is not gated beyond being a valid
 * actor.
 */
@Service
public class TimeEntryService {

    private final TimeEntryRepository timeEntryRepository;
    private final PersonRepository personRepository;

    public TimeEntryService(TimeEntryRepository timeEntryRepository, PersonRepository personRepository) {
        this.timeEntryRepository = timeEntryRepository;
        this.personRepository = personRepository;
    }

    @Transactional
    public TimeEntry clockIn(String actorId) {
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

    /** @param personIdFilter when non-blank, scopes to that one person's entries instead of every entry. */
    @Transactional(readOnly = true)
    public List<TimeEntry> listEntries(String personIdFilter) {
        if (personIdFilter != null && !personIdFilter.isBlank()) {
            return timeEntryRepository.findByPersonIdOrderByClockInAsc(personIdFilter);
        }
        return timeEntryRepository.findAllByOrderByClockInAsc();
    }
}
