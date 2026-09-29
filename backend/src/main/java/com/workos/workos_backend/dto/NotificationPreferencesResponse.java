package com.workos.workos_backend.dto;

import com.workos.workos_backend.entity.NotificationPreference;

/** GET/PATCH /api/notification-preferences response shape. */
public record NotificationPreferencesResponse(
        boolean mentionsEnabled, boolean taskAssignedEnabled, boolean dueSoonEnabled, boolean weeklyDigestEnabled) {

    public static NotificationPreferencesResponse from(NotificationPreference preference) {
        return new NotificationPreferencesResponse(
                preference.isMentionsEnabled(),
                preference.isTaskAssignedEnabled(),
                preference.isDueSoonEnabled(),
                preference.isWeeklyDigestEnabled());
    }
}
