package com.workos.workos_backend.dto;

import com.workos.workos_backend.entity.Notification;

/** {@code createdAt} is serialized via {@code Instant.toString()} (ISO-8601), same convention as {@link ChatMessageResponse}. */
public record NotificationResponse(
        String id, String eventType, String actorId, String actorName, String message, boolean read,
        String createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEventType().name(),
                notification.getActor().getId(),
                notification.getActor().getName(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt().toString());
    }
}
