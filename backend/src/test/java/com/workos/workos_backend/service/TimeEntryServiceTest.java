package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.TimeEntry;
import com.workos.workos_backend.exception.ConflictException;
import com.workos.workos_backend.repository.TimeEntryRepository;

import jakarta.persistence.EntityManager;

/**
 * Exercises TimeEntryService's business rules directly, same approach as
 * TaskServiceTest — actor ids are passed explicitly so multiple people's
 * clock-in/out scenarios can be tested against the seeded local-dev people
 * without touching the fixed HTTP actor.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class TimeEntryServiceTest {

    @Autowired
    private TimeEntryService timeEntryService;

    @Autowired
    private TimeEntryRepository timeEntryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void clockInCreatesAnOpenEntryForTheActor() {
        TimeEntry entry = timeEntryService.clockIn("sarah-chen");

        assertThat(entry.getPerson().getId()).isEqualTo("sarah-chen");
        assertThat(entry.getClockIn()).isNotNull();
        assertThat(entry.getClockOut()).isNull();
    }

    @Test
    void clockInRejectsASecondClockInWhileAlreadyClockedIn() {
        timeEntryService.clockIn("sarah-chen");

        assertThatThrownBy(() -> timeEntryService.clockIn("sarah-chen"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void clockInForOnePersonDoesNotBlockAnother() {
        timeEntryService.clockIn("sarah-chen");

        TimeEntry entry = timeEntryService.clockIn("alex-morgan");

        assertThat(entry.getPerson().getId()).isEqualTo("alex-morgan");
    }

    @Test
    void clockOutClosesTheOpenEntry() {
        timeEntryService.clockIn("sarah-chen");

        TimeEntry closed = timeEntryService.clockOut("sarah-chen");

        assertThat(closed.getClockOut()).isNotNull();
    }

    @Test
    void clockOutRejectsWhenNotClockedIn() {
        assertThatThrownBy(() -> timeEntryService.clockOut("sarah-chen"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void clockOutAllowsClockingInAgainAfterwards() {
        timeEntryService.clockIn("sarah-chen");
        timeEntryService.clockOut("sarah-chen");

        TimeEntry second = timeEntryService.clockIn("sarah-chen");

        assertThat(second.getClockOut()).isNull();
    }

    @Test
    void getOpenEntryReturnsEmptyWhenNotClockedIn() {
        assertThat(timeEntryService.getOpenEntry("sarah-chen")).isEmpty();
    }

    @Test
    void getOpenEntryReturnsTheOpenEntryWhenClockedIn() {
        timeEntryService.clockIn("sarah-chen");

        assertThat(timeEntryService.getOpenEntry("sarah-chen")).isPresent();
    }

    @Test
    void listEntriesWithNoFilterReturnsEveryPersonsEntries() {
        timeEntryService.clockIn("sarah-chen");
        timeEntryService.clockIn("alex-morgan");

        List<TimeEntry> all = timeEntryService.listEntries(null);

        assertThat(all).hasSize(2);
    }

    @Test
    void listEntriesWithPersonIdFilterScopesToThatPerson() {
        timeEntryService.clockIn("sarah-chen");
        timeEntryService.clockIn("alex-morgan");

        List<TimeEntry> scoped = timeEntryService.listEntries("sarah-chen");

        assertThat(scoped).hasSize(1);
        assertThat(scoped.get(0).getPerson().getId()).isEqualTo("sarah-chen");
    }

    @Test
    void listEntriesWithUnknownPersonIdReturnsEmpty() {
        timeEntryService.clockIn("sarah-chen");

        assertThat(timeEntryService.listEntries("does-not-exist")).isEmpty();
    }

    @Test
    void timeEntryPersistsAcrossAReload() {
        TimeEntry entry = timeEntryService.clockIn("sarah-chen");
        String entryId = entry.getId();
        entityManager.flush();
        entityManager.clear();

        TimeEntry reloaded = timeEntryRepository.findById(entryId).orElseThrow();
        assertThat(reloaded.getPerson().getId()).isEqualTo("sarah-chen");
        assertThat(reloaded.getClockOut()).isNull();
    }
}
