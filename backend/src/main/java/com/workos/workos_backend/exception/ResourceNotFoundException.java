package com.workos.workos_backend.exception;

/**
 * Thrown by future service-layer code when a requested resource (workspace,
 * board, task, etc.) does not exist. Mapped to 404 by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
