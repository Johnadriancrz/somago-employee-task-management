package com.workos.workos_backend.entity;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

/**
 * Base for entities whose id is application-assigned (a frontend-compatible
 * string), not database-generated. Without this, Spring Data JPA's default
 * isNew() check ("id == null") would treat every first save() as an update
 * and route it through EntityManager.merge() instead of persist() — merge
 * copies state onto a separate managed instance, so a @PrePersist callback
 * on the original instance (e.g. Workspace's owner-must-be-member check)
 * would run against an incomplete copy of the object graph.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<String> {

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }
}
