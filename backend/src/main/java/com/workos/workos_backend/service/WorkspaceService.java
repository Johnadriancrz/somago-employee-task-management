package com.workos.workos_backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.authorization.AccessRoleChecker;
import com.workos.workos_backend.authorization.AccessRoles;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.exception.ResourceNotFoundException;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.WorkspaceRepository;

/**
 * Business logic for Workspace CRUD and membership, enforcing the rules
 * from BACKEND.md's Workspaces table: visibility is owner-or-member,
 * delete/add-member/remove-member are owner-only, and the owner can never
 * be removed as a member.
 *
 * <p>Spec section 12: CEO may additionally manage membership (add/remove)
 * on any workspace regardless of ownership — a purely additive bypass on
 * top of the existing owner-only gate. Operation Manager's broader
 * "workspaces they are authorized to manage" scope beyond ones they own is
 * an open product decision (spec section 20 item 5) with no schema
 * representation yet, and is intentionally not guessed at here; an
 * Operation Manager who owns a workspace is already covered by the
 * existing owner-only gate, unchanged.
 */
@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final PersonRepository personRepository;
    private final AccessRoleChecker accessRoleChecker;

    public WorkspaceService(WorkspaceRepository workspaceRepository, PersonRepository personRepository,
            AccessRoleChecker accessRoleChecker) {
        this.workspaceRepository = workspaceRepository;
        this.personRepository = personRepository;
        this.accessRoleChecker = accessRoleChecker;
    }

    /**
     * Every workspace the given person owns or is a member of. Since the
     * owner is always persisted as a member too (Workspace's own invariant),
     * membership alone covers both halves of "owns or is a member of".
     */
    @Transactional(readOnly = true)
    public List<Workspace> listVisibleWorkspaces(String actorId) {
        return workspaceRepository.findByMembersId(actorId);
    }

    @Transactional
    public Workspace createWorkspace(String actorId, String name, String initials) {
        Person owner = personRepository.findById(actorId)
                .orElseThrow(() -> new IllegalStateException("Acting person not found: " + actorId));
        String trimmedName = name.trim();
        String resolvedInitials = (initials == null || initials.isBlank())
                ? initialsFrom(trimmedName)
                : initials.trim();
        Workspace workspace = new Workspace(UUID.randomUUID().toString(), trimmedName, resolvedInitials, owner);
        // saveAndFlush (not save): Workspace's assigned-id Persistable.isNew() only
        // flips to false on the @PostPersist callback, which only fires on an actual
        // flush. Without forcing it here, a later delete() in the same transaction
        // would see isNew()==true and Spring Data JPA would silently no-op it
        // (SimpleJpaRepository.delete() skips entities it still considers new).
        return workspaceRepository.saveAndFlush(workspace);
    }

    @Transactional
    public void deleteWorkspace(String actorId, String workspaceId) {
        Workspace workspace = getOrThrow(workspaceId);
        requireOwner(workspace, actorId, "Only the workspace owner can delete this workspace");
        workspaceRepository.delete(workspace);
    }

    @Transactional
    public Workspace addMember(String actorId, String workspaceId, String targetPersonId) {
        Workspace workspace = getOrThrow(workspaceId);
        requireOwnerOrCeo(workspace, actorId, "Only the workspace owner or a CEO can add members");
        Person target = personRepository.findById(targetPersonId)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown person id: " + targetPersonId));
        workspace.addMember(target);
        return workspaceRepository.save(workspace);
    }

    @Transactional
    public Workspace removeMember(String actorId, String workspaceId, String targetPersonId) {
        Workspace workspace = getOrThrow(workspaceId);
        requireOwnerOrCeo(workspace, actorId, "Only the workspace owner or a CEO can remove members");
        if (workspace.getOwner().getId().equals(targetPersonId)) {
            throw new IllegalArgumentException("The workspace owner can't be removed");
        }
        workspace.getMembers().removeIf(member -> member.getId().equals(targetPersonId));
        return workspaceRepository.save(workspace);
    }

    private Workspace getOrThrow(String workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found: " + workspaceId));
    }

    private void requireOwnerOrCeo(Workspace workspace, String actorId, String message) {
        if (workspace.getOwner().getId().equals(actorId)) {
            return;
        }
        if (accessRoleChecker.actorHasAnyRole(actorId, AccessRoles.CEO)) {
            return;
        }
        throw new ForbiddenException(message);
    }

    private void requireOwner(Workspace workspace, String actorId, String message) {
        if (!workspace.getOwner().getId().equals(actorId)) {
            throw new ForbiddenException(message);
        }
    }

    /** Matches the frontend's initialsFrom() exactly (workos-app/src/lib/server/workspace-repository.ts). */
    private static String initialsFrom(String name) {
        String[] words = name.trim().split("\\s+");
        String first = words.length > 0 && !words[0].isEmpty() ? words[0].substring(0, 1) : "";
        String second = words.length > 1 && !words[1].isEmpty() ? words[1].substring(0, 1) : "";
        String initials = first + second;
        if (initials.isEmpty()) {
            initials = name.length() >= 2 ? name.substring(0, 2) : name;
        }
        return initials.toUpperCase();
    }
}
