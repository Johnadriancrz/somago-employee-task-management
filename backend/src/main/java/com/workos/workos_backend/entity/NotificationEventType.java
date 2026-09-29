package com.workos.workos_backend.entity;

/**
 * The notification-worthy actions covered by the self-notification rule
 * (spec section 18): every one of these events notifies the management
 * recipient set (CEO, HR, Operation Manager) plus the actor themselves, see
 * {@link com.workos.workos_backend.service.NotificationService#notify}.
 */
public enum NotificationEventType {
    CHAT_MESSAGE,
    CLOCK_IN,
    CLOCK_OUT,
    TASK_WORKING,
    TASK_STUCK,
    TASK_DONE,
    BOARD_CREATED
}
