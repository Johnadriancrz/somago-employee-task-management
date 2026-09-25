package com.workos.workos_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.Session;

public interface SessionRepository extends JpaRepository<Session, String> {

    /** Used to revoke a person's other active sessions on password change, keeping the current one alive. */
    void deleteByPersonIdAndTokenNot(String personId, String token);
}
