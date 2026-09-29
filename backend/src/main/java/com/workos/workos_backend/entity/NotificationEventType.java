package com.workos.workos_backend.entity;

/**
 * The notification-worthy actions covered by the self-notification rule
 * (spec section 18): every one of these events notifies the management
 * recipient set (CEO, HR, Operation Manager) plus the actor themselves, see
 * {@link com.workos.workos_backend.service.NotificationService#notify}.
 *
 * <p>{@link #TASK_ASSIGNED} (spec section S2) is different: it is a targeted
 * event, delivered only to the new task owner via {@link
 * com.workos.workos_backend.service.NotificationService#notifyRecipients},
 * never to management or the actor.
 *
 * <p>The four Time Clock + Overtime events below are all targeted, not
 * broadcast, via {@code notifyRecipients}: {@link #OVERTIME_REQUESTED} goes
 * to CEO/HR only; {@link #OVERTIME_APPROVED}/{@link #OVERTIME_REJECTED} go
 * to the requester only; {@link #CLOCK_OUT_REMINDER} goes to the
 * still-clocked-in person only.
 */
public enum NotificationEventType {
    CHAT_MESSAGE,
    CLOCK_IN,
    CLOCK_OUT,
    TASK_WORKING,
    TASK_STUCK,
    TASK_DONE,
    BOARD_CREATED,
    TASK_ASSIGNED,
    OVERTIME_REQUESTED,
    OVERTIME_APPROVED,
    OVERTIME_REJECTED,
    CLOCK_OUT_REMINDER
}
