package com.workos.workos_backend.dto;

/**
 * Error body shape shared by the whole API, matching the frontend's existing
 * convention of {@code { "error": "Error message" }}.
 */
public record ErrorResponse(String error) {
}
