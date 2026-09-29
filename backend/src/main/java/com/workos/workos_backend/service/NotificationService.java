package com.workos.workos_backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.workos.workos_backend.authorization.AccessRoles;
import com.workos.workos_backend.dto.NotificationResponse;
import com.workos.workos_backend.entity.Notification;
import com.workos.workos_backend.entity.NotificationEventType;
import com.workos.workos_backend.entity.NotificationPreference;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.exception.ResourceNotFoundException;
import com.workos.workos_backend.repository.NotificationPreferenceRepository;
import com.workos.workos_backend.repository.NotificationRepository;
import com.workos.workos_backend.repository.PersonRepository;

/**
 * Fans a single notification-worthy action out to its recipients, per the
 * self-notification rule (spec section 18): the management recipient set
 * (CEO, HR, Operation Manager) plus the actor themselves, always — the actor
 * is never excluded merely because they performed the action (section 18).
 *
 * <p>Recipients are deduplicated by person id before persisting (section
 * 18.1) — e.g. a CEO clocking in is both "the actor" and "a management
 * recipient" but must receive exactly one row for that event, never two.
 * Each recipient still gets their own {@link Notification} row with an
 * independent read state (section 18.3); only the row count per person is
 * deduplicated, not the read state.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /**
     * STOMP destination prefix a recipient's notifications are published to —
     * {@code /topic/notifications/{personId}}, per-recipient rather than a
     * single shared topic, so the broker itself never fans a notification out
     * to anyone but its own recipient. See {@link
     * com.workos.workos_backend.websocket.NotificationChannelInterceptor},
     * which authorizes SUBSCRIBEs to this same prefix.
     */
    public static final String STOMP_DESTINATION_PREFIX = "/topic/notifications/";

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PersonRepository personRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository,
            NotificationPreferenceRepository notificationPreferenceRepository, PersonRepository personRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.personRepository = personRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public List<Notification> notify(String actorId, NotificationEventType eventType, String message) {
        Person actor = personRepository.findById(actorId)
                .orElseThrow(() -> new IllegalStateException("Acting person not found: " + actorId));

        // LinkedHashMap keyed by person id: management recipients first, then
        // the actor — re-inserting the actor's id if already present (e.g.
        // the actor is the CEO) overwrites the map entry rather than adding a
        // second one, which is exactly the deduplication section 18.1 asks for.
        Map<String, Person> recipients = new LinkedHashMap<>();
        for (Person manager : personRepository.findByAccessRoleIn(
                List.of(AccessRoles.CEO, AccessRoles.HR, AccessRoles.OPERATION_MANAGER))) {
            recipients.put(manager.getId(), manager);
        }
        recipients.put(actor.getId(), actor);

        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        List<Notification> created = new ArrayList<>();
        for (Person recipient : recipients.values()) {
            created.add(new Notification(
                    UUID.randomUUID().toString(), eventId, recipient, actor, eventType, message, now));
        }
        List<Notification> saved = notificationRepository.saveAll(created);
        publishAfterCommit(saved);
        return saved;
    }

    /**
     * Targeted-recipient primitive (spec section S2), distinct from {@link
     * #notify}: delivers only to {@code recipientIds}, exactly as given by
     * the business caller — the actor is never added automatically, unlike
     * {@code notify()}'s management-plus-actor fan-out. Used for events with
     * a genuinely narrow audience (e.g. {@code TASK_ASSIGNED} → the new owner
     * alone), never as a replacement for the existing seven events.
     *
     * <p>{@code recipientIds} are resolved through real, trusted {@link
     * Person} records and deduplicated before persisting, same as {@code
     * notify()}'s own recipient map. A recipient id that doesn't resolve to a
     * real person is an internal invariant violation (this method is never
     * meant to be reachable with a client-supplied id) and fails loudly with
     * {@link IllegalStateException}, mirroring how {@code notify()} treats an
     * unresolvable actor id.
     *
     * <p>Per-recipient notification preferences are enforced here (never in
     * {@code notify()}, which must keep unconditionally notifying management
     * plus the actor) — a recipient who has disabled the given event type is
     * silently filtered out rather than given a suppressed/hidden row. If
     * every recipient is filtered out (or {@code recipientIds} was empty to
     * begin with), this is a safe no-op: no event id, no rows, no publish.
     */
    @Transactional
    public List<Notification> notifyRecipients(String actorId, List<String> recipientIds,
            NotificationEventType eventType, String message) {
        Person actor = personRepository.findById(actorId)
                .orElseThrow(() -> new IllegalStateException("Acting person not found: " + actorId));

        // LinkedHashMap keyed by person id, same dedup technique as notify() —
        // a repeated id in the input list collapses to one map entry.
        Map<String, Person> recipients = new LinkedHashMap<>();
        for (String recipientId : recipientIds) {
            Person recipient = personRepository.findById(recipientId)
                    .orElseThrow(() -> new IllegalStateException("Recipient not found: " + recipientId));
            if (isOptedIn(recipient.getId(), eventType)) {
                recipients.put(recipient.getId(), recipient);
            }
        }
        if (recipients.isEmpty()) {
            return Collections.emptyList();
        }

        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        List<Notification> created = new ArrayList<>();
        for (Person recipient : recipients.values()) {
            created.add(new Notification(
                    UUID.randomUUID().toString(), eventId, recipient, actor, eventType, message, now));
        }
        List<Notification> saved = notificationRepository.saveAll(created);
        publishAfterCommit(saved);
        return saved;
    }

    /**
     * Preference enforcement is per-recipient and only defined for event
     * types that actually have a Settings toggle — right now, only {@code
     * TASK_ASSIGNED}/{@code taskAssignedEnabled}. A recipient with no
     * preference row yet defaults to enabled, matching {@link
     * NotificationPreferenceService}'s own defaults, without creating a row
     * as a side effect of sending a notification.
     */
    private boolean isOptedIn(String recipientId, NotificationEventType eventType) {
        if (eventType != NotificationEventType.TASK_ASSIGNED) {
            return true;
        }
        return notificationPreferenceRepository.findByPersonId(recipientId)
                .map(NotificationPreference::isTaskAssignedEnabled)
                .orElse(true);
    }

    /**
     * Defers the WebSocket fan-out until the enclosing transaction has
     * actually committed (spec section 7/10) — a subscriber must never see a
     * notification over the socket before {@code GET /api/notifications}
     * would also be able to return it from MySQL. Falls back to publishing
     * immediately if no transaction is active (shouldn't happen in practice
     * since this method is itself {@code @Transactional}, but is not relied
     * upon).
     */
    private void publishAfterCommit(List<Notification> notifications) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(notifications);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(notifications);
            }
        });
    }

    /**
     * A WebSocket delivery failure must never fail the business operation
     * that triggered it (spec section 7) — the notification is already
     * durably persisted in MySQL by the time this runs, so a broker/send
     * error here is logged and swallowed, not propagated.
     */
    private void publish(List<Notification> notifications) {
        for (Notification notification : notifications) {
            try {
                messagingTemplate.convertAndSend(
                        STOMP_DESTINATION_PREFIX + notification.getRecipient().getId(),
                        NotificationResponse.from(notification));
            } catch (RuntimeException ex) {
                log.warn("Failed to publish notification {} to recipient {}",
                        notification.getId(), notification.getRecipient().getId(), ex);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> listForRecipient(String recipientId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
    }

    /** GET /api/notifications/unread-count — the current actor's own unread count, never a shared/global total. */
    @Transactional(readOnly = true)
    public long unreadCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Transactional
    public Notification markRead(String recipientId, String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (!notification.getRecipient().getId().equals(recipientId)) {
            throw new ForbiddenException("Not the recipient of this notification");
        }
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(String recipientId) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndReadFalse(recipientId);
        for (Notification notification : unread) {
            notification.setRead(true);
        }
        notificationRepository.saveAll(unread);
    }
}
