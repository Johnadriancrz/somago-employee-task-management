package com.workos.workos_backend.dto;

import com.workos.workos_backend.entity.BoardMeta;

/** Matches the frontend's {@code BoardMeta} type exactly (workos-app/src/lib/types.ts). */
public record BoardResponse(
        String id,
        String workspaceId,
        String name,
        String description,
        String icon) {

    public static BoardResponse from(BoardMeta board) {
        return new BoardResponse(
                board.getId(),
                board.getWorkspace().getId(),
                board.getName(),
                board.getDescription(),
                board.getIcon().getValue());
    }
}
