package com.workos.workos_backend.dto;

/**
 * One person's hours for one business-calendar workday (GET
 * /api/time/summary). All four fields are always populated — even though an
 * initial frontend may only surface some of them, they form the auditable
 * trail distinguishing actual worked time from regular time, approved
 * overtime, and unapproved excess time.
 *
 * @param actualHours total closed clock-in/out time for this person on this date
 * @param regularHours {@code min(actualHours, 8.0)}
 * @param approvedOvertimeHours the portion of {@code actualHours - 8.0} (if positive) covered by an
 *                              APPROVED OvertimeRequest for this person+date, capped at that request's
 *                              requested hours; 0 if no such request exists
 * @param unapprovedExcessHours {@code max(actualHours - 8.0, 0) - approvedOvertimeHours} — worked but
 *                              neither regular nor approved-overtime
 */
public record TimeSummaryDayResponse(
        String personId,
        String date,
        double actualHours,
        double regularHours,
        double approvedOvertimeHours,
        double unapprovedExcessHours) {
}
