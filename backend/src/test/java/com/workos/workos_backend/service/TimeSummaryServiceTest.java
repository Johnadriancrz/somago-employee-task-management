package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.dto.TimeSummaryDayResponse;
import com.workos.workos_backend.entity.OvertimeRequest;
import com.workos.workos_backend.entity.OvertimeRequestStatus;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.TimeEntry;
import com.workos.workos_backend.repository.OvertimeRequestRepository;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.TimeEntryRepository;
import com.workos.workos_backend.time.BusinessClock;

/**
 * Exercises TimeSummaryService's regular/approved-OT/unapproved-excess split
 * directly, same approach as TimeEntryServiceTest — entries are built with
 * controlled Instants (via TimeEntryRepository) rather than going through
 * TimeEntryService.clockIn/clockOut, since the hours math needs specific,
 * reproducible durations.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class TimeSummaryServiceTest {

    @Autowired
    private TimeSummaryService timeSummaryService;

    @Autowired
    private TimeEntryRepository timeEntryRepository;

    @Autowired
    private OvertimeRequestRepository overtimeRequestRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private BusinessClock businessClock;

    private String newAccount(String accessRole) {
        Person person = accountService.createAccount(
                "Test " + accessRole, accessRole.toLowerCase().replace(" ", ".") + "-"
                        + UUID.randomUUID() + "@workos.dev",
                "Password123!", accessRole);
        return person.getId();
    }

    private TimeEntry closedEntry(String personId, LocalDate businessDate, double hours) {
        Person person = personRepository.findById(personId).orElseThrow();
        Instant clockIn = businessDate.atTime(9, 0).atZone(businessClock.zoneId()).toInstant();
        Instant clockOut = clockIn.plusSeconds((long) (hours * 3600));
        TimeEntry entry = new TimeEntry(UUID.randomUUID().toString(), person, clockIn);
        entry.setClockOut(clockOut);
        return timeEntryRepository.saveAndFlush(entry);
    }

    private void approveOvertimeRequest(String personId, LocalDate date, double requestedHours) {
        Person person = personRepository.findById(personId).orElseThrow();
        OvertimeRequest request = new OvertimeRequest(
                UUID.randomUUID().toString(), person, date, requestedHours, "Deadline", Instant.now());
        request.setStatus(OvertimeRequestStatus.APPROVED);
        request.setReviewedAt(Instant.now());
        overtimeRequestRepository.saveAndFlush(request);
    }

    @Test
    void actualUpToEightHoursIsAllRegularWithNoOvertime() {
        LocalDate date = LocalDate.of(2026, 1, 5);
        closedEntry("sarah-chen", date, 6.0);

        TimeSummaryDayResponse day = timeSummaryService.summarize("sarah-chen", null, null, null).get(0);

        assertThat(day.actualHours()).isEqualTo(6.0);
        assertThat(day.regularHours()).isEqualTo(6.0);
        assertThat(day.approvedOvertimeHours()).isEqualTo(0.0);
        assertThat(day.unapprovedExcessHours()).isEqualTo(0.0);
    }

    @Test
    void excessBeyondEightHoursIsUnapprovedWithoutAnApprovedRequest() {
        LocalDate date = LocalDate.of(2026, 1, 5);
        closedEntry("sarah-chen", date, 10.0);

        TimeSummaryDayResponse day = timeSummaryService.summarize("sarah-chen", null, null, null).get(0);

        assertThat(day.actualHours()).isEqualTo(10.0);
        assertThat(day.regularHours()).isEqualTo(8.0);
        assertThat(day.approvedOvertimeHours()).isEqualTo(0.0);
        assertThat(day.unapprovedExcessHours()).isEqualTo(2.0);
    }

    @Test
    void approvedOvertimeHoursIsCappedAtActualExcessNotTheRequestedAmount() {
        LocalDate date = LocalDate.of(2026, 1, 5);
        closedEntry("sarah-chen", date, 10.0);
        approveOvertimeRequest("sarah-chen", date, 3.0);

        TimeSummaryDayResponse day = timeSummaryService.summarize("sarah-chen", null, null, null).get(0);

        assertThat(day.approvedOvertimeHours()).isEqualTo(2.0);
        assertThat(day.unapprovedExcessHours()).isEqualTo(0.0);
    }

    @Test
    void approvedOvertimeHoursIsCappedAtTheApprovedRequestAmount() {
        LocalDate date = LocalDate.of(2026, 1, 5);
        closedEntry("sarah-chen", date, 10.0);
        approveOvertimeRequest("sarah-chen", date, 1.0);

        TimeSummaryDayResponse day = timeSummaryService.summarize("sarah-chen", null, null, null).get(0);

        assertThat(day.approvedOvertimeHours()).isEqualTo(1.0);
        assertThat(day.unapprovedExcessHours()).isEqualTo(1.0);
    }

    @Test
    void pendingOvertimeRequestDoesNotContributeApprovedHours() {
        LocalDate date = LocalDate.of(2026, 1, 5);
        closedEntry("sarah-chen", date, 10.0);
        Person person = personRepository.findById("sarah-chen").orElseThrow();
        overtimeRequestRepository.saveAndFlush(new OvertimeRequest(
                UUID.randomUUID().toString(), person, date, 3.0, "Deadline", Instant.now()));

        TimeSummaryDayResponse day = timeSummaryService.summarize("sarah-chen", null, null, null).get(0);

        assertThat(day.approvedOvertimeHours()).isEqualTo(0.0);
        assertThat(day.unapprovedExcessHours()).isEqualTo(2.0);
    }

    @Test
    void multipleClosedEntriesOnTheSameDaySumIntoOneBucket() {
        LocalDate date = LocalDate.of(2026, 1, 5);
        closedEntry("sarah-chen", date, 4.0);
        closedEntry("sarah-chen", date, 5.0);

        List<TimeSummaryDayResponse> summary = timeSummaryService.summarize("sarah-chen", null, null, null);

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).actualHours()).isEqualTo(9.0);
    }

    @Test
    void openEntryIsExcludedFromTheSummary() {
        Person person = personRepository.findById("sarah-chen").orElseThrow();
        TimeEntry openEntry = new TimeEntry(UUID.randomUUID().toString(), person, Instant.now());
        timeEntryRepository.saveAndFlush(openEntry);

        assertThat(timeSummaryService.summarize("sarah-chen", null, null, null)).isEmpty();
    }

    @Test
    void anEntryCrossingMidnightIsBucketedEntirelyUnderItsClockInDate() {
        LocalDate date = LocalDate.of(2026, 1, 5);
        Person person = personRepository.findById("sarah-chen").orElseThrow();
        Instant clockIn = date.atTime(22, 0).atZone(businessClock.zoneId()).toInstant();
        Instant clockOut = clockIn.plusSeconds(4 * 3600); // 02:00 the next business day
        TimeEntry entry = new TimeEntry(UUID.randomUUID().toString(), person, clockIn);
        entry.setClockOut(clockOut);
        timeEntryRepository.saveAndFlush(entry);

        List<TimeSummaryDayResponse> summary = timeSummaryService.summarize("sarah-chen", null, null, null);

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).date()).isEqualTo(date.toString());
        assertThat(summary.get(0).actualHours()).isEqualTo(4.0);
    }

    @Test
    void nonPrivilegedActorOnlySeesOwnSummaryRegardlessOfPersonIdFilter() {
        LocalDate date = LocalDate.of(2026, 1, 5);
        closedEntry("sarah-chen", date, 5.0);
        closedEntry("alex-morgan", date, 5.0);

        List<TimeSummaryDayResponse> summary = timeSummaryService.summarize("sarah-chen", "alex-morgan", null, null);

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).personId()).isEqualTo("sarah-chen");
    }

    @Test
    void ceoCanFilterToAnotherPersonsSummary() {
        LocalDate date = LocalDate.of(2026, 1, 5);
        closedEntry("sarah-chen", date, 5.0);
        closedEntry("alex-morgan", date, 6.0);
        String ceoId = newAccount("CEO");

        List<TimeSummaryDayResponse> summary = timeSummaryService.summarize(ceoId, "alex-morgan", null, null);

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).personId()).isEqualTo("alex-morgan");
        assertThat(summary.get(0).actualHours()).isEqualTo(6.0);
    }

    @Test
    void startAndEndFilterExcludeDatesOutsideRange() {
        closedEntry("sarah-chen", LocalDate.of(2026, 1, 1), 5.0);
        closedEntry("sarah-chen", LocalDate.of(2026, 1, 10), 5.0);

        List<TimeSummaryDayResponse> summary =
                timeSummaryService.summarize("sarah-chen", null, "2026-01-05", "2026-01-15");

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).date()).isEqualTo("2026-01-10");
    }
}
