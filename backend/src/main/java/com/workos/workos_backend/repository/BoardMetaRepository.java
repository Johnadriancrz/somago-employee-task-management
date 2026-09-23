package com.workos.workos_backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.BoardMeta;

public interface BoardMetaRepository extends JpaRepository<BoardMeta, String> {

    /** All boards in a workspace (GET /api/boards is filtered to member workspaces, then by workspaceId client-side). */
    List<BoardMeta> findByWorkspaceId(String workspaceId);

    /** All boards across a set of workspaces — backs GET /api/boards, scoped to the caller's member workspaces. */
    List<BoardMeta> findByWorkspaceIdIn(Collection<String> workspaceIds);
}
