package com.workos.workos_backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Settings Phase S1: one row per person holding the four Settings
 * notification toggles (mentions/task-assigned/due-soon/weekly-digest). This
 * is purely persisted preference state — it does not drive any notification
 * delivery behavior yet (see {@link com.workos.workos_backend.service.NotificationService},
 * unchanged in this phase).
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference extends AssignedIdEntity {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "mentions_enabled", nullable = false)
    private boolean mentionsEnabled;

    @Column(name = "task_assigned_enabled", nullable = false)
    private boolean taskAssignedEnabled;

    @Column(name = "due_soon_enabled", nullable = false)
    private boolean dueSoonEnabled;

    @Column(name = "weekly_digest_enabled", nullable = false)
    private boolean weeklyDigestEnabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
    }

    public NotificationPreference(String id, Person person, boolean mentionsEnabled, boolean taskAssignedEnabled,
            boolean dueSoonEnabled, boolean weeklyDigestEnabled, Instant createdAt) {
        this.id = id;
        this.person = person;
        this.mentionsEnabled = mentionsEnabled;
        this.taskAssignedEnabled = taskAssignedEnabled;
        this.dueSoonEnabled = dueSoonEnabled;
        this.weeklyDigestEnabled = weeklyDigestEnabled;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public boolean isMentionsEnabled() {
        return mentionsEnabled;
    }

    public void setMentionsEnabled(boolean mentionsEnabled) {
        this.mentionsEnabled = mentionsEnabled;
    }

    public boolean isTaskAssignedEnabled() {
        return taskAssignedEnabled;
    }

    public void setTaskAssignedEnabled(boolean taskAssignedEnabled) {
        this.taskAssignedEnabled = taskAssignedEnabled;
    }

    public boolean isDueSoonEnabled() {
        return dueSoonEnabled;
    }

    public void setDueSoonEnabled(boolean dueSoonEnabled) {
        this.dueSoonEnabled = dueSoonEnabled;
    }

    public boolean isWeeklyDigestEnabled() {
        return weeklyDigestEnabled;
    }

    public void setWeeklyDigestEnabled(boolean weeklyDigestEnabled) {
        this.weeklyDigestEnabled = weeklyDigestEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
