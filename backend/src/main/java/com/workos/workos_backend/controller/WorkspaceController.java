package com.workos.workos_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.workos.workos_backend.actor.ActingPersonResolver;
import com.workos.workos_backend.dto.AddWorkspaceMemberRequest;
import com.workos.workos_backend.dto.CreateWorkspaceRequest;
import com.workos.workos_backend.dto.OkResponse;
import com.workos.workos_backend.dto.WorkspaceResponse;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.service.WorkspaceService;

import jakarta.validation.Valid;

/** Implements the Workspaces + Workspace Members endpoint tables from BACKEND.md. */
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final ActingPersonResolver actingPersonResolver;

    public WorkspaceController(WorkspaceService workspaceService, ActingPersonResolver actingPersonResolver) {
        this.workspaceService = workspaceService;
        this.actingPersonResolver = actingPersonResolver;
    }

    @GetMapping
    public List<WorkspaceResponse> list() {
        String actorId = actingPersonResolver.currentPersonId();
        return workspaceService.listVisibleWorkspaces(actorId).stream().map(WorkspaceResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(@Valid @RequestBody CreateWorkspaceRequest request) {
        String actorId = actingPersonResolver.currentPersonId();
        Workspace workspace = workspaceService.createWorkspace(actorId, request.name(), request.initials());
        return WorkspaceResponse.from(workspace);
    }

    @DeleteMapping("/{workspaceId}")
    public OkResponse delete(@PathVariable String workspaceId) {
        String actorId = actingPersonResolver.currentPersonId();
        workspaceService.deleteWorkspace(actorId, workspaceId);
        return OkResponse.OK;
    }

    @PostMapping("/{workspaceId}/members")
    public WorkspaceResponse addMember(
            @PathVariable String workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request) {
        String actorId = actingPersonResolver.currentPersonId();
        Workspace workspace = workspaceService.addMember(actorId, workspaceId, request.personId());
        return WorkspaceResponse.from(workspace);
    }

    @DeleteMapping("/{workspaceId}/members/{personId}")
    public WorkspaceResponse removeMember(@PathVariable String workspaceId, @PathVariable String personId) {
        String actorId = actingPersonResolver.currentPersonId();
        Workspace workspace = workspaceService.removeMember(actorId, workspaceId, personId);
        return WorkspaceResponse.from(workspace);
    }
}
