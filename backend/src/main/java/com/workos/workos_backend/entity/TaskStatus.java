package com.workos.workos_backend.entity;

/**
 * Mirrors the frontend's {@code Status} union type exactly
 * (workos-app/src/lib/types.ts). Named {@code TaskStatus} here (not
 * {@code Status}) to avoid a confusing near-collision with the many
 * {@code org.springframework.http.HttpStatus} imports throughout the
 * controller layer. The wire/storage value is the lowercase string on the
 * right — see {@link TaskStatusConverter}.
 */
public enum TaskStatus {

    NOT_STARTED("not-started"),
    WORKING("working"),
    STUCK("stuck"),
    DONE("done");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TaskStatus fromValue(String value) {
        for (TaskStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown task status: " + value);
    }
}
