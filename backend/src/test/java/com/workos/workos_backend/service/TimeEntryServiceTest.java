package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.TimeEntry;
import com.workos.workos_backend.exception.ConflictException;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.repository.TimeEntryRepository;

import jakarta.persistence.EntityManager;

/**
 * Exercises TimeEntryService's business rules directly, same approach as
 * TaskServiceTest — actor ids are passed explicitly so multiple people's
 * clock-in/out scenarios can be tested against the seeded local-dev people
 * without touching the fixed HTTP actor. The seeded local-dev people all
 * have a null accessRole (spec section 13.3), so role-scoped {@code
 * listEntries} scenarios mint a fresh CEO/HR account via AccountService
 * where a privileged actor is needed (spec section 9).
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
    private AccountService accountService;

    @Autowired
    private EntityManager entityManager;

    private String newAccount(String accessRole) {
        Person person = accountService.createAccount(
                "Test " + accessRole, accessRole.toLowerCase().replace(" ", ".") + "-"
                        + java.util.UUID.randomUUID() + "@workos.dev",
                "Password123!", accessRole);
        return person.getId();
    }

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
    void listEntriesForCeoWithNoFilterReturnsEveryPersonsEntries() {
        timeEntryService.clockIn("sarah-chen");
        timeEntryService.clockIn("alex-morgan");
        String ceoId = newAccount("CEO");

        List<TimeEntry> all = timeEntryService.listEntries(ceoId, null);

        assertThat(all).hasSize(2);
    }

    @Test
    void listEntriesForHrWithNoFilterReturnsEveryPersonsEntries() {
        timeEntryService.clockIn("sarah-chen");
        timeEntryService.clockIn("alex-morgan");
        String hrId = newAccount("HR");

        List<TimeEntry> all = timeEntryService.listEntries(hrId, null);

        assertThat(all).hasSize(2);
    }

    @Test
    void listEntriesForCeoWithPersonIdFilterScopesToThatPerson() {
        timeEntryService.clockIn("sarah-chen");
        timeEntryService.clockIn("alex-morgan");
        String ceoId = newAccount("CEO");

        List<TimeEntry> scoped = timeEntryService.listEntries(ceoId, "sarah-chen");

        assertThat(scoped).hasSize(1);
        assertThat(scoped.get(0).getPerson().getId()).isEqualTo("sarah-chen");
    }

    @Test
    void listEntriesForCeoWithUnknownPersonIdReturnsEmpty() {
        timeEntryService.clockIn("sarah-chen");
        String ceoId = newAccount("CEO");

        assertThat(timeEntryService.listEntries(ceoId, "does-not-exist")).isEmpty();
    }

    @Test
    void listEntriesForNonPrivilegedActorWithNoFilterReturnsOnlyOwnEntries() {
        timeEntryService.clockIn("sarah-chen");
        timeEntryService.clockIn("alex-morgan");

        List<TimeEntry> scoped = timeEntryService.listEntries("sarah-chen", null);

        assertThat(scoped).hasSize(1);
        assertThat(scoped.get(0).getPerson().getId()).isEqualTo("sarah-chen");
    }

    @Test
    void listEntriesForNonPrivilegedActorIgnoresPersonIdFilterAndStaysScopedToSelf() {
        timeEntryService.clockIn("sarah-chen");
        timeEntryService.clockIn("alex-morgan");

        // sarah-chen (no accessRole) tries to read alex-morgan's entries by
        // manipulating personIdFilter directly — must still only see her own.
        List<TimeEntry> scoped = timeEntryService.listEntries("sarah-chen", "alex-morgan");

        assertThat(scoped).hasSize(1);
        assertThat(scoped.get(0).getPerson().getId()).isEqualTo("sarah-chen");
    }

    @Test
    void clockInRejectsCeoWithForbidden() {
        String ceoId = newAccount("CEO");

        assertThatThrownBy(() -> timeEntryService.clockIn(ceoId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void clockInRejectsHrWithForbidden() {
        String hrId = newAccount("HR");

        assertThatThrownBy(() -> timeEntryService.clockIn(hrId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listEntriesForOperationManagerAlsoStaysScopedToSelf() {
        timeEntryService.clockIn("sarah-chen");
        timeEntryService.clockIn("alex-morgan");
        String omId = newAccount("Operation Manager");
        timeEntryService.clockIn(omId);

        // Operation Manager has all-employee Reports access (spec section 10)
        // but only own-record Time Clock access (spec section 9) — must not
        // be confused with CEO/HR here.
        List<TimeEntry> scoped = timeEntryService.listEntries(omId, "sarah-chen");

        assertThat(scoped).hasSize(1);
        assertThat(scoped.get(0).getPerson().getId()).isEqualTo(omId);
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
