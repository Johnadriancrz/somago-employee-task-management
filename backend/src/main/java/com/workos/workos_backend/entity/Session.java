package com.workos.workos_backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A persistent, revocable login session (V6 migration). {@code token} is the
 * app-generated, high-entropy session identifier — also the value stored in
 * the session cookie — and doubles as the primary key, matching the schema's
 * single-column design (no separate hash column). Never resolved from
 * client-supplied input other than the cookie itself; see
 * {@link com.workos.workos_backend.actor.ActingPersonResolver}'s Javadoc for
 * the same non-negotiable constraint this entity ultimately backs.
 */
@Entity
@Table(name = "sessions")
public class Session extends AssignedIdEntity {

    @Id
    @Column(length = 128)
    private String token;

    @Column(name = "person_id", nullable = false, length = 64)
    private String personId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected Session() {
    }

    public Session(String token, String personId, Instant createdAt, Instant expiresAt) {
        this.token = token;
        this.personId = personId;
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

    public String getPersonId() {
        return personId;
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
        if (!(o instanceof Session other)) {
            return false;
        }
        return token != null && token.equals(other.token);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
