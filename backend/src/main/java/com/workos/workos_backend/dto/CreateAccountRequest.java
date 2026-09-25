package com.workos.workos_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/accounts body: exactly {@code { name, email, password, accessRole } }
 * (spec section 5.2's 4-field form - no Department/Job Title field). Only an
 * authenticated Admin may submit this; identity/Admin privilege is derived
 * from the caller's verified session, never from a field on this body.
 */
public record CreateAccountRequest(

        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password,

        @NotBlank(message = "accessRole is required")
        String accessRole) {
}
