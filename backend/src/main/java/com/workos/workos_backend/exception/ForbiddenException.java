package com.workos.workos_backend.exception;

/**
 * Thrown when the acting person is known (resolved successfully) but lacks
 * permission for the requested operation on an otherwise-existing resource
 * (e.g. a non-owner trying to delete a workspace). Mapped to 403 by
 * {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
