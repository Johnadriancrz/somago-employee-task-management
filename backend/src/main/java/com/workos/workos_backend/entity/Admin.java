package com.workos.workos_backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A system-level Admin: a privilege deliberately kept separate from
 * {@link Person}/{@code accessRole} (see
 * Docs/Authentication/WORKOS-AUTH-RBAC-SPEC.md and Phase 3's Admin
 * clarification). Admin is not one of the 8 employee access roles, is
 * never inferred from any of them (including CEO), and is not a Person
 * row at all - it has its own table, its own credentials, and its own
 * session mechanism ({@link AdminSession}). {@code passwordHash} must
 * never be exposed via {@link com.workos.workos_backend.dto.AdminResponse}.
 */
@Entity
@Table(name = "admins")
public class Admin extends AssignedIdEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Admin() {
    }

    public Admin(String id, String email, String passwordHash, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Admin other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
