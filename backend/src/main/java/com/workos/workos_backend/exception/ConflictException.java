package com.workos.workos_backend.exception;

/**
 * Thrown when a request conflicts with the resource's current state (e.g.
 * clocking in while already clocked in, or clocking out while not clocked
 * in). Mapped to 409 by {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
