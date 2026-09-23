package com.workos.workos_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.Workspace;

public interface WorkspaceRepository extends JpaRepository<Workspace, String> {

    /** Workspaces owned by this person — half of "owns or is a member of" (BACKEND.md, GET /api/workspaces). */
    List<Workspace> findByOwnerId(String ownerId);

    /** Workspaces this person is a member of (the owner is always also a member). */
    List<Workspace> findByMembersId(String personId);
}
