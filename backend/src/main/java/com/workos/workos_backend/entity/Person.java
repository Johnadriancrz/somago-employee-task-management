package com.workos.workos_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mirrors the frontend's {@code Person} type (workos-app/src/lib/types.ts).
 * {@code chipClass} is persisted as a plain string, matching the frontend
 * field exactly; it is presentation data (Tailwind classes) but the frontend
 * contract requires it on every Person, so it is stored as-is rather than
 * derived, to avoid changing existing API semantics.
 * {@code accessRole} and {@code passwordHash} are a separate, later
 * addition for auth/RBAC (see Docs/Authentication/WORKOS-AUTH-RBAC-SPEC.md);
 * both are nullable with no default — a row with a null accessRole is
 * unauthenticatable by construction. {@code accessRole} is one of the 8
 * fixed roles in that spec's §3, stored as a plain string (no DB-level
 * enum/CHECK — spec §15/§20.8, an open question left for a later phase).
 * {@code passwordHash} must never be exposed via {@link
 * com.workos.workos_backend.dto.PersonResponse}.
 */
@Entity
@Table(name = "people")
public class Person extends AssignedIdEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 8)
    private String initials;

    @Column(nullable = false)
    private String role;

    @Column(name = "chip_class", nullable = false)
    private String chipClass;

    @Column(name = "access_role", length = 32)
    private String accessRole;

    @Column(name = "password_hash")
    private String passwordHash;

    protected Person() {
    }

    public Person(String id, String name, String email, String initials, String role, String chipClass) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.initials = initials;
        this.role = role;
        this.chipClass = chipClass;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getInitials() {
        return initials;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getChipClass() {
        return chipClass;
    }

    public void setChipClass(String chipClass) {
        this.chipClass = chipClass;
    }

    public String getAccessRole() {
        return accessRole;
    }

    public void setAccessRole(String accessRole) {
        this.accessRole = accessRole;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Person other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
