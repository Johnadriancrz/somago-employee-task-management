package com.workos.workos_backend.dto;

/** {@code GET /api/notifications/unread-count} — per-recipient, never a shared/global count. */
public record UnreadCountResponse(long count) {
}
