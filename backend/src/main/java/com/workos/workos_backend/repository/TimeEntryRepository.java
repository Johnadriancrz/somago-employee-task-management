package com.workos.workos_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.TimeEntry;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, String> {

    /** The signed-in user's open (still-clocked-in) entry, if any — backs GET /api/time/status and the clock-in/out conflict checks. */
    Optional<TimeEntry> findByPersonIdAndClockOutIsNull(String personId);

    /** GET /api/time/entries with no ?personId= filter — workspace-wide, oldest first. */
    List<TimeEntry> findAllByOrderByClockInAsc();

    /** GET /api/time/entries?personId=... — one person's entries, oldest first. */
    List<TimeEntry> findByPersonIdOrderByClockInAsc(String personId);
}
