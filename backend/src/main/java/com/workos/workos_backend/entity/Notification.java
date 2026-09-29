package com.workos.workos_backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One notification row per recipient (spec section 18.3: every recipient has
 * their own read state, never a shared broadcast row — mirrors
 * {@link ChatMessage}'s per-row modeling elsewhere in this codebase).
 *
 * <p>{@code eventId} groups every row created by a single {@link
 * com.workos.workos_backend.service.NotificationService#notify} call (one
 * real-world action, e.g. "John clocked in") — it is not the primary key,
 * since each recipient still needs their own id/read-state row, but a unique
 * constraint on {@code (event_id, recipient_id)} (V9 migration) enforces
 * spec section 18.2's "never create duplicate notification rows for the same
 * notification event + recipient" rule at the DB level, not just in
 * application code.
 */
@Entity
@Table(name = "notifications")
public class Notification extends AssignedIdEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Person recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private Person actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private NotificationEventType eventType;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(String id, String eventId, Person recipient, Person actor,
            NotificationEventType eventType, String message, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.recipient = recipient;
        this.actor = actor;
        this.eventType = eventType;
        this.message = message;
        this.read = false;
        this.createdAt = createdAt;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Person getRecipient() {
        return recipient;
    }

    public void setRecipient(Person recipient) {
        this.recipient = recipient;
    }

    public Person getActor() {
        return actor;
    }

    public void setActor(Person actor) {
        this.actor = actor;
    }

    public NotificationEventType getEventType() {
        return eventType;
    }

    public void setEventType(NotificationEventType eventType) {
        this.eventType = eventType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
