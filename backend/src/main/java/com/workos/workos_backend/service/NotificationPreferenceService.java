package com.workos.workos_backend.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.dto.UpdateNotificationPreferencesRequest;
import com.workos.workos_backend.entity.NotificationPreference;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.repository.NotificationPreferenceRepository;
import com.workos.workos_backend.repository.PersonRepository;

/**
 * Settings Phase S1: persistence only for the four Settings notification
 * toggles. Never trusts a client-supplied person id — every method takes the
 * server-resolved {@code actorId} and reads/writes only that person's row.
 * Defaults (mentions/task-assigned/due-soon enabled, weekly-digest disabled)
 * mirror the current Settings UI's previous hardcoded local state; a row is
 * created lazily, on first read or write, for a person who doesn't have one
 * yet, rather than backfilled for every existing person up front.
 */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PersonRepository personRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository notificationPreferenceRepository,
            PersonRepository personRepository) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.personRepository = personRepository;
    }

    @Transactional
    public NotificationPreference getPreferences(String actorId) {
        return getOrCreate(actorId);
    }

    @Transactional
    public NotificationPreference updatePreferences(String actorId, UpdateNotificationPreferencesRequest request) {
        NotificationPreference preference = getOrCreate(actorId);
        if (request.mentionsEnabled() != null) {
            preference.setMentionsEnabled(request.mentionsEnabled());
        }
        if (request.taskAssignedEnabled() != null) {
            preference.setTaskAssignedEnabled(request.taskAssignedEnabled());
        }
        if (request.dueSoonEnabled() != null) {
            preference.setDueSoonEnabled(request.dueSoonEnabled());
        }
        if (request.weeklyDigestEnabled() != null) {
            preference.setWeeklyDigestEnabled(request.weeklyDigestEnabled());
        }
        preference.setUpdatedAt(Instant.now());
        return notificationPreferenceRepository.save(preference);
    }

    private NotificationPreference getOrCreate(String actorId) {
        return notificationPreferenceRepository.findByPersonId(actorId).orElseGet(() -> {
            Person person = personRepository.findById(actorId)
                    .orElseThrow(() -> new IllegalStateException("Acting person not found: " + actorId));
            NotificationPreference created = new NotificationPreference(
                    UUID.randomUUID().toString(), person, true, true, true, false, Instant.now());
            return notificationPreferenceRepository.saveAndFlush(created);
        });
    }
}
