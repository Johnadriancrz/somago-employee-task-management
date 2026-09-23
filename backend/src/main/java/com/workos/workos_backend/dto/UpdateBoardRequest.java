package com.workos.workos_backend.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH /api/boards/:boardId body: {@code Partial<Omit<BoardMeta, "id">>}
 * (BACKEND.md) — a merge-patch. Any field left out of the request JSON is
 * deserialized as {@code null} here and left unchanged by the service; there
 * is no supported way to explicitly blank out {@code name} or {@code icon}
 * (matching the frontend, which never sends them as empty/null), but
 * {@code description} may be patched to an empty string.
 */
public record UpdateBoardRequest(

        @Size(max = 255, message = "name must be 255 characters or fewer")
        String name,

        @Size(max = 1000, message = "description must be 1000 characters or fewer")
        String description,

        String icon) {
}
