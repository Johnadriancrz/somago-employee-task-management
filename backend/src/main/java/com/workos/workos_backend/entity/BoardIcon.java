package com.workos.workos_backend.entity;

/**
 * Mirrors the frontend's {@code BoardIcon} union type exactly
 * (workos-app/src/lib/types.ts). The wire/storage value is the lowercase
 * string on the right — see {@link BoardIconConverter}.
 */
public enum BoardIcon {

    TABLE("table"),
    KANBAN("kanban"),
    BUG("bug"),
    MILESTONE("milestone"),
    GENERIC("generic");

    private final String value;

    BoardIcon(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static BoardIcon fromValue(String value) {
        for (BoardIcon icon : values()) {
            if (icon.value.equals(value)) {
                return icon;
            }
        }
        throw new IllegalArgumentException("Unknown board icon: " + value);
    }
}
