package com.workos.workos_backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Notification;
import com.workos.workos_backend.entity.NotificationEventType;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.TimeEntry;
import com.workos.workos_backend.repository.NotificationRepository;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.TimeEntryRepository;
import com.workos.workos_backend.service.AccountService;
import com.workos.workos_backend.service.TimeEntryService;

/**
 * Exercises ClockOutReminderScheduler.remindOpenClockIns() directly (not via
 * the cron trigger — the method is public exactly so tests don't need to
 * wait on a real clock), same directly-call-the-service approach as
 * TimeEntryServiceTest.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class ClockOutReminderSchedulerTest {

    @Autowired
    private ClockOutReminderScheduler scheduler;

    @Autowired
    private TimeEntryService timeEntryService;

    @Autowired
    private TimeEntryRepository timeEntryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private AccountService accountService;

    private String newAccount(String accessRole) {
        Person person = accountService.createAccount(
                "Test " + accessRole, accessRole.toLowerCase().replace(" ", ".") + "-"
                        + UUID.randomUUID() + "@workos.dev",
                "Password123!", accessRole);
        return person.getId();
    }

    private long reminderCountFor(String personId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(personId).stream()
                .filter(n -> n.getEventType() == NotificationEventType.CLOCK_OUT_REMINDER)
                .count();
    }

    @Test
    void personStillClockedInReceivesAReminder() {
        timeEntryService.clockIn("sarah-chen");

        scheduler.remindOpenClockIns();

        assertThat(reminderCountFor("sarah-chen")).isEqualTo(1);
    }

    @Test
    void personNotClockedInReceivesNoReminder() {
        timeEntryService.clockIn("sarah-chen");
        timeEntryService.clockOut("sarah-chen");

        scheduler.remindOpenClockIns();

        assertThat(reminderCountFor("sarah-chen")).isEqualTo(0);
    }

    @Test
    void personWithTwoOpenEntriesReceivesExactlyOneReminder() {
        // Two open entries for the same person shouldn't normally happen (the
        // "at most one open entry" rule lives in TimeEntryService, not a DB
        // constraint — see TimeEntry's class Javadoc), but the scheduler must
        // stay defensive: it dedupes by person id before notifying.
        Person person = personRepository.findById("sarah-chen").orElseThrow();
        timeEntryRepository.saveAndFlush(new TimeEntry(UUID.randomUUID().toString(), person, Instant.now()));
        timeEntryRepository.saveAndFlush(new TimeEntry(UUID.randomUUID().toString(), person, Instant.now()));

        scheduler.remindOpenClockIns();

        assertThat(reminderCountFor("sarah-chen")).isEqualTo(1);
    }

    @Test
    void ceoWithAnOpenEntryIsNeverReminded() {
        String ceoId = newAccount("CEO");
        Person ceo = personRepository.findById(ceoId).orElseThrow();
        // CEO can't clock in via TimeEntryService (exempt) — construct the open
        // entry directly to test the scheduler's own defensive exclusion.
        timeEntryRepository.saveAndFlush(new TimeEntry(UUID.randomUUID().toString(), ceo, Instant.now()));

        scheduler.remindOpenClockIns();

        assertThat(reminderCountFor(ceoId)).isEqualTo(0);
    }

    @Test
    void hrWithAnOpenEntryIsNeverReminded() {
        String hrId = newAccount("HR");
        Person hr = personRepository.findById(hrId).orElseThrow();
        timeEntryRepository.saveAndFlush(new TimeEntry(UUID.randomUUID().toString(), hr, Instant.now()));

        scheduler.remindOpenClockIns();

        assertThat(reminderCountFor(hrId)).isEqualTo(0);
    }

    @Test
    void multiplePeopleClockedInEachReceiveTheirOwnReminder() {
        timeEntryService.clockIn("sarah-chen");
        timeEntryService.clockIn("alex-morgan");

        scheduler.remindOpenClockIns();

        assertThat(reminderCountFor("sarah-chen")).isEqualTo(1);
        assertThat(reminderCountFor("alex-morgan")).isEqualTo(1);
    }

    @Test
    void reminderNotificationCarriesTheRecipientAsTheirOwnActor() {
        timeEntryService.clockIn("sarah-chen");

        scheduler.remindOpenClockIns();

        List<Notification> reminders = notificationRepository.findByRecipientIdOrderByCreatedAtDesc("sarah-chen")
                .stream().filter(n -> n.getEventType() == NotificationEventType.CLOCK_OUT_REMINDER).toList();
        assertThat(reminders).hasSize(1);
        assertThat(reminders.get(0).getActor().getId()).isEqualTo("sarah-chen");
    }
}
