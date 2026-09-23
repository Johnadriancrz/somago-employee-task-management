package com.workos.workos_backend.dto;

import com.workos.workos_backend.entity.Subtask;

/** Matches the frontend's {@code Subtask} type exactly (workos-app/src/lib/types.ts). */
public record SubtaskResponse(String id, String title, boolean done) {

    public static SubtaskResponse from(Subtask subtask) {
        return new SubtaskResponse(subtask.getId(), subtask.getTitle(), subtask.isDone());
    }
}
