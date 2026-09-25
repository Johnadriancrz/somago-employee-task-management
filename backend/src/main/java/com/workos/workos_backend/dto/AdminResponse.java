package com.workos.workos_backend.dto;

import java.time.Instant;

import com.workos.workos_backend.entity.Admin;

/** {@code passwordHash} must never appear here. */
public record AdminResponse(String id, String email, Instant createdAt) {

    public static AdminResponse from(Admin admin) {
        return new AdminResponse(admin.getId(), admin.getEmail(), admin.getCreatedAt());
    }
}
