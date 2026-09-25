package com.workos.workos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/auth/change-password body: {@code { currentPassword, newPassword } }.
 * The authenticated identity comes solely from the caller's session cookie
 * (see {@link com.workos.workos_backend.controller.AuthController}) — this
 * body never carries a personId. {@code newPassword}'s minimum length
 * matches {@link CreateAccountRequest}'s existing policy.
 */
public record ChangePasswordRequest(

        @NotBlank(message = "currentPassword is required")
        String currentPassword,

        @NotBlank(message = "newPassword is required")
        @Size(min = 8, message = "newPassword must be at least 8 characters")
        String newPassword) {
}
