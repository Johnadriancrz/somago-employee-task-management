package com.workos.workos_backend.entity;

/**
 * Mirrors the frontend's {@code TaskGroup} union type exactly
 * (workos-app/src/lib/types.ts). The wire/storage value is the lowercase
 * string on the right — see {@link TaskGroupConverter}.
 */
public enum TaskGroup {

    THIS_WEEK("this-week"),
    NEXT_WEEK("next-week"),
    THIS_MONTH("this-month"),
    NEXT_MONTH("next-month");

    private final String value;

    TaskGroup(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TaskGroup fromValue(String value) {
        for (TaskGroup group : values()) {
            if (group.value.equals(value)) {
                return group;
            }
        }
        throw new IllegalArgumentException("Unknown task group: " + value);
    }
}
