package com.workos.workos_backend.dto;

import com.workos.workos_backend.entity.TimeEntry;

/**
 * Matches the frontend's {@code TimeEntry} type exactly
 * (workos-app/src/lib/types.ts). {@code clockIn}/{@code clockOut} are
 * serialized via {@code Instant.toString()} (ISO-8601, e.g.
 * {@code "2025-09-19T10:15:30.123Z"}), which {@code new Date(...)} on the
 * frontend parses the same as the existing stub's {@code Date.toISOString()}.
 */
public record TimeEntryResponse(String id, String personId, String clockIn, String clockOut) {

    public static TimeEntryResponse from(TimeEntry entry) {
        return new TimeEntryResponse(
                entry.getId(),
                entry.getPerson().getId(),
                entry.getClockIn().toString(),
                entry.getClockOut() != null ? entry.getClockOut().toString() : null);
    }
}
