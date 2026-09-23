package com.workos.workos_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Mirrors the frontend's {@code BoardMeta} type (workos-app/src/lib/types.ts).
 * Each board belongs to exactly one workspace; deleting the workspace
 * cascades to its boards (see Workspace.boards and the FK's ON DELETE CASCADE
 * in the migration), matching the existing DELETE /api/workspaces/:id contract.
 */
@Entity
@Table(name = "boards")
public class BoardMeta extends AssignedIdEntity {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 20)
    private BoardIcon icon;

    protected BoardMeta() {
    }

    public BoardMeta(String id, Workspace workspace, String name, String description, BoardIcon icon) {
        this.id = id;
        this.workspace = workspace;
        this.name = name;
        this.description = description;
        this.icon = icon;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BoardIcon getIcon() {
        return icon;
    }

    public void setIcon(BoardIcon icon) {
        this.icon = icon;
    }
}
