package com.workos.workos_backend.entity;

/**
 * An {@link OvertimeRequest}'s lifecycle: created {@code PENDING}, then
 * transitions at most once, to either {@code APPROVED} or {@code REJECTED}
 * by a CEO/HR reviewer. Only {@code APPROVED} ever contributes to a day's
 * approved-OT hours (see {@code TimeSummaryService}) — time worked beyond 8
 * hours is never automatically treated as overtime.
 */
public enum OvertimeRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
