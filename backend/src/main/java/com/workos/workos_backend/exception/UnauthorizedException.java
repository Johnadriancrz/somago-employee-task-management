package com.workos.workos_backend.exception;

/**
 * Thrown when a request has no verified identity at all — missing, invalid,
 * or expired session — as opposed to {@link ForbiddenException}, which is
 * for a known actor lacking permission. Mapped to 401 by
 * {@link GlobalExceptionHandler}.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
