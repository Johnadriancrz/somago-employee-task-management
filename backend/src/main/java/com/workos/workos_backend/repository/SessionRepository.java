package com.workos.workos_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.Session;

public interface SessionRepository extends JpaRepository<Session, String> {
}
