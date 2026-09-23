package com.workos.workos_backend.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/login body: {@code { email: string; password: string } }. */
public record LoginRequest(

        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "password is required")
        String password) {
}
