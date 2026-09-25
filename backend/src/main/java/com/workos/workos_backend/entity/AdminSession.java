package com.workos.workos_backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A persistent, revocable login session for an {@link Admin} (V8
 * migration). Mirrors {@link Session}'s design for Person logins, but kept
 * fully separate: an Admin session must never be confused with, or
 * upgraded from, an employee access-role session.
 */
@Entity
@Table(name = "admin_sessions")
public class AdminSession extends AssignedIdEntity {

    @Id
    @Column(length = 128)
    private String token;

    @Column(name = "admin_id", nullable = false, length = 64)
    private String adminId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AdminSession() {
    }

    public AdminSession(String token, String adminId, Instant createdAt, Instant expiresAt) {
        this.token = token;
        this.adminId = adminId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    @Override
    public String getId() {
        return token;
    }

    public String getToken() {
        return token;
    }

    public String getAdminId() {
        return adminId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AdminSession other)) {
            return false;
        }
        return token != null && token.equals(other.token);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
