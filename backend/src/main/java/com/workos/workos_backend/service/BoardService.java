package com.workos.workos_backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.BoardIcon;
import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.NotificationEventType;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.exception.ResourceNotFoundException;
import com.workos.workos_backend.repository.BoardMetaRepository;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.WorkspaceRepository;

/**
 * Business logic for Board CRUD, enforcing the rules from BACKEND.md's
 * Boards table: any workspace member (not owner-only) may create, update,
 * or delete a board; listing is scoped to the caller's member workspaces.
 */
@Service
public class BoardService {

    private final BoardMetaRepository boardMetaRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PersonRepository personRepository;
    private final NotificationService notificationService;

    public BoardService(BoardMetaRepository boardMetaRepository, WorkspaceRepository workspaceRepository,
            PersonRepository personRepository, NotificationService notificationService) {
        this.boardMetaRepository = boardMetaRepository;
        this.workspaceRepository = workspaceRepository;
        this.personRepository = personRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<BoardMeta> listVisibleBoards(String actorId) {
        List<String> workspaceIds = workspaceRepository.findByMembersId(actorId).stream()
                .map(Workspace::getId)
                .toList();
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        return boardMetaRepository.findByWorkspaceIdIn(workspaceIds);
    }

    @Transactional
    public BoardMeta createBoard(String actorId, String workspaceId, String name, String description,
            String iconValue) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown workspace id: " + workspaceId));
        requireMember(workspace, actorId);
        BoardIcon icon = (iconValue == null || iconValue.isBlank()) ? BoardIcon.GENERIC : BoardIcon.fromValue(iconValue);
        BoardMeta board = new BoardMeta(
                UUID.randomUUID().toString(),
                workspace,
                name.trim(),
                description == null ? "" : description.trim(),
                icon);
        // saveAndFlush: see the matching comment in WorkspaceService.createWorkspace —
        // BoardMeta has the same assigned-id Persistable.isNew() behavior.
        BoardMeta saved = boardMetaRepository.saveAndFlush(board);
        Person actor = personRepository.findById(actorId)
                .orElseThrow(() -> new IllegalStateException("Acting person not found: " + actorId));
        notificationService.notify(actorId, NotificationEventType.BOARD_CREATED,
                actor.getName() + " created a board \"" + saved.getName() + "\".");
        return saved;
    }

    @Transactional
    public BoardMeta updateBoard(String actorId, String boardId, String name, String description,
            String iconValue) {
        BoardMeta board = getOrThrow(boardId);
        requireMember(board.getWorkspace(), actorId);
        if (name != null && !name.isBlank()) {
            board.setName(name.trim());
        }
        if (description != null) {
            board.setDescription(description.trim());
        }
        if (iconValue != null && !iconValue.isBlank()) {
            board.setIcon(BoardIcon.fromValue(iconValue));
        }
        return boardMetaRepository.save(board);
    }

    @Transactional
    public void deleteBoard(String actorId, String boardId) {
        BoardMeta board = getOrThrow(boardId);
        requireMember(board.getWorkspace(), actorId);
        boardMetaRepository.delete(board);
    }

    private BoardMeta getOrThrow(String boardId) {
        return boardMetaRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Board not found: " + boardId));
    }

    private void requireMember(Workspace workspace, String actorId) {
        boolean isMember = workspace.getMembers().stream().anyMatch(member -> member.getId().equals(actorId));
        if (!isMember) {
            throw new ForbiddenException("Not a member of this workspace");
        }
    }
}
