package com.workos.workos_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.AdminSession;

public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {
}
