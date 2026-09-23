package com.workos.workos_backend.dto;

import java.util.List;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Workspace;

/** Matches the frontend's {@code Workspace} type exactly (workos-app/src/lib/types.ts). */
public record WorkspaceResponse(
        String id,
        String name,
        String initials,
        String ownerId,
        List<String> memberIds) {

    public static WorkspaceResponse from(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getInitials(),
                workspace.getOwner().getId(),
                workspace.getMembers().stream().map(Person::getId).toList());
    }
}
