package com.workos.workos_backend.scheduler;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.workos.workos_backend.authorization.AccessRoleChecker;
import com.workos.workos_backend.authorization.AccessRoles;
import com.workos.workos_backend.entity.NotificationEventType;
import com.workos.workos_backend.entity.TimeEntry;
import com.workos.workos_backend.repository.TimeEntryRepository;
import com.workos.workos_backend.service.NotificationService;

/**
 * Fires daily at 17:30 business time (see {@code
 * com.workos.workos_backend.time.BusinessClock} / {@code
 * app.business.zone-id}) and reminds anyone still clocked in to clock out.
 *
 * <p>There is no working-day/business-calendar concept in this codebase, so
 * this deliberately does not invent a Monday-Friday restriction — it simply
 * fires every day and notifies whoever has an open {@link TimeEntry} at that
 * moment, which is a no-op on any day nobody is clocked in.
 *
 * <p>Person ids are deduplicated before notifying, so a person is reminded
 * at most once per run even if they somehow have more than one open entry.
 * CEO/HR are always excluded (they're exempt from personal clock-in to begin
 * with — see {@code TimeEntryService.clockIn} — so this is a defensive
 * no-op, not the primary enforcement point).
 *
 * <p>{@link #remindOpenClockIns()} is public and callable directly (not only
 * via the cron trigger) so tests can exercise it without waiting on a real
 * clock.
 */
@Component
public class ClockOutReminderScheduler {

    private final TimeEntryRepository timeEntryRepository;
    private final AccessRoleChecker accessRoleChecker;
    private final NotificationService notificationService;

    public ClockOutReminderScheduler(TimeEntryRepository timeEntryRepository, AccessRoleChecker accessRoleChecker,
            NotificationService notificationService) {
        this.timeEntryRepository = timeEntryRepository;
        this.accessRoleChecker = accessRoleChecker;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 30 17 * * *", zone = "${app.business.zone-id}")
    public void remindOpenClockIns() {
        List<TimeEntry> open = timeEntryRepository.findAllByClockOutIsNull();

        Set<String> personIds = new LinkedHashSet<>();
        for (TimeEntry entry : open) {
            personIds.add(entry.getPerson().getId());
        }

        for (String personId : personIds) {
            if (accessRoleChecker.actorHasAnyRole(personId, AccessRoles.CEO, AccessRoles.HR)) {
                continue;
            }
            notificationService.notifyRecipients(personId, List.of(personId), NotificationEventType.CLOCK_OUT_REMINDER,
                    "You're still clocked in — don't forget to clock out.");
        }
    }
}
