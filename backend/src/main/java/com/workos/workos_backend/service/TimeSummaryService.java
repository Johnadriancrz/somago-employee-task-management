package com.workos.workos_backend.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.authorization.AccessRoleChecker;
import com.workos.workos_backend.authorization.AccessRoles;
import com.workos.workos_backend.dto.TimeSummaryDayResponse;
import com.workos.workos_backend.entity.OvertimeRequest;
import com.workos.workos_backend.entity.OvertimeRequestStatus;
import com.workos.workos_backend.entity.TimeEntry;
import com.workos.workos_backend.repository.OvertimeRequestRepository;
import com.workos.workos_backend.repository.TimeEntryRepository;
import com.workos.workos_backend.time.BusinessClock;

/**
 * Computes regular vs. approved-overtime hours per business-calendar workday
 * (Time Clock + Overtime feature, phase A/E). Kept separate from {@link
 * TimeEntryService} rather than grown into it, mirroring how {@code
 * ReportService} is already separate from {@code TaskService} for
 * cross-cutting reporting concerns.
 *
 * <p>Hours are computed on read, every time, from {@link TimeEntry}'s
 * existing {@code clockIn}/{@code clockOut} instants and {@link
 * OvertimeRequest}'s approval state — nothing is persisted onto {@code
 * TimeEntry}, so historical clock records are never rewritten.
 *
 * <p>An entry is bucketed entirely under its {@code clockIn}'s business
 * date. A closed entry whose {@code clockOut} falls on a later business date
 * (an overnight shift) is not split across the two dates — {@code TimeEntry}
 * has no concept of a partial-day duration, and inventing one here would be
 * a payroll rule this feature was never asked to define. This is a known,
 * flagged limitation of the current one-row-per-entry model.
 */
@Service
public class TimeSummaryService {

    private static final double NORMAL_WORKDAY_HOURS = 8.0;

    private final TimeEntryRepository timeEntryRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final AccessRoleChecker accessRoleChecker;
    private final BusinessClock businessClock;

    public TimeSummaryService(TimeEntryRepository timeEntryRepository,
            OvertimeRequestRepository overtimeRequestRepository, AccessRoleChecker accessRoleChecker,
            BusinessClock businessClock) {
        this.timeEntryRepository = timeEntryRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.accessRoleChecker = accessRoleChecker;
        this.businessClock = businessClock;
    }

    /**
     * @param actorId the server-resolved caller, whose accessRole decides whether personIdFilter is honored.
     * @param personIdFilter when non-blank and the actor is CEO/HR, scopes to that one person's entries;
     *                       ignored for every other actor, who is always scoped to their own id instead.
     * @param startDateRaw optional inclusive ISO lower bound (yyyy-MM-dd) on the business date.
     * @param endDateRaw optional inclusive ISO upper bound (yyyy-MM-dd) on the business date.
     */
    @Transactional(readOnly = true)
    public List<TimeSummaryDayResponse> summarize(String actorId, String personIdFilter, String startDateRaw,
            String endDateRaw) {
        LocalDate startDate = parseOptionalDate(startDateRaw, "start");
        LocalDate endDate = parseOptionalDate(endDateRaw, "end");

        List<TimeEntry> entries;
        if (!accessRoleChecker.actorHasAnyRole(actorId, AccessRoles.CEO, AccessRoles.HR)) {
            entries = timeEntryRepository.findByPersonIdOrderByClockInAsc(actorId);
        } else if (personIdFilter != null && !personIdFilter.isBlank()) {
            entries = timeEntryRepository.findByPersonIdOrderByClockInAsc(personIdFilter);
        } else {
            entries = timeEntryRepository.findAllByOrderByClockInAsc();
        }

        // (personId, businessDate) -> total actual seconds worked that day.
        record Bucket(String personId, LocalDate date) {
        }
        Map<Bucket, Double> actualHoursByBucket = new LinkedHashMap<>();
        for (TimeEntry entry : entries) {
            if (entry.getClockOut() == null) {
                continue; // Only closed entries count toward reporting.
            }
            LocalDate businessDate = businessClock.toBusinessDate(entry.getClockIn());
            if (startDate != null && businessDate.isBefore(startDate)) {
                continue;
            }
            if (endDate != null && businessDate.isAfter(endDate)) {
                continue;
            }
            double hours = Duration.between(entry.getClockIn(), entry.getClockOut()).toSeconds() / 3600.0;
            Bucket bucket = new Bucket(entry.getPerson().getId(), businessDate);
            actualHoursByBucket.merge(bucket, hours, Double::sum);
        }

        List<TimeSummaryDayResponse> result = new ArrayList<>();
        for (Map.Entry<Bucket, Double> bucketEntry : actualHoursByBucket.entrySet()) {
            String personId = bucketEntry.getKey().personId();
            LocalDate date = bucketEntry.getKey().date();
            double actualHours = bucketEntry.getValue();

            double regularHours = Math.min(actualHours, NORMAL_WORKDAY_HOURS);
            double excessHours = Math.max(actualHours - NORMAL_WORKDAY_HOURS, 0.0);

            double approvedOvertimeHours = overtimeRequestRepository
                    .findByPersonIdAndWorkDateAndStatus(personId, date, OvertimeRequestStatus.APPROVED)
                    .map(request -> Math.min(excessHours, request.getRequestedHours()))
                    .orElse(0.0);
            double unapprovedExcessHours = excessHours - approvedOvertimeHours;

            result.add(new TimeSummaryDayResponse(
                    personId, date.toString(), actualHours, regularHours, approvedOvertimeHours,
                    unapprovedExcessHours));
        }
        return result;
    }

    private static LocalDate parseOptionalDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid ISO date (yyyy-MM-dd)");
        }
    }
}
