package com.workos.workos_backend.dto;

/**
 * PATCH /api/notification-preferences body — a merge-patch, same convention
 * as {@link UpdateBoardRequest}: any field left out of the request JSON is
 * deserialized as {@code null} here and left unchanged by the service. The
 * target person is always the session-resolved actor; this body never
 * carries a personId.
 */
public record UpdateNotificationPreferencesRequest(
        Boolean mentionsEnabled, Boolean taskAssignedEnabled, Boolean dueSoonEnabled, Boolean weeklyDigestEnabled) {
}
