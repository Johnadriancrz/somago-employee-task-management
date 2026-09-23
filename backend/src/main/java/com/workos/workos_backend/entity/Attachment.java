package com.workos.workos_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Mirrors the frontend's {@code Attachment} type exactly (workos-app/src/lib/types.ts).
 * {@code dataUrl} keeps its current meaning unchanged for this phase — a
 * base64 {@code data:} URI stored inline, exactly as the frontend's
 * FilesPicker produces it (client-side {@code FileReader.readAsDataURL()},
 * capped at 5MB — enforced again server-side in TaskService since the
 * client cap is otherwise trivially bypassable via a direct API call). See
 * BACKEND.md / the frontend-backend audit for why this is a deliberate
 * placeholder, not a real object-storage integration.
 */
@Entity
@Table(name = "task_attachments")
public class Attachment extends AssignedIdEntity {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false, length = 150)
    private String type;

    // No @Lob: Hibernate would then expect a CLOB-family column, but H2's
    // MySQL-compatibility mode resolves the migration's LONGTEXT column to a
    // plain (unbounded) VARCHAR, which fails schema validation against a CLOB
    // expectation. A plain String field (no explicit length, so Hibernate's
    // length check is satisfied by any actual column at least as long as the
    // default) matches both H2's VARCHAR and MySQL's LONGVARCHAR.
    @Column(name = "data_url", nullable = false)
    private String dataUrl;

    @Column(nullable = false)
    private int position;

    protected Attachment() {
    }

    public Attachment(String id, String name, long size, String type, String dataUrl, int position) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.type = type;
        this.dataUrl = dataUrl;
        this.position = position;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDataUrl() {
        return dataUrl;
    }

    public void setDataUrl(String dataUrl) {
        this.dataUrl = dataUrl;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
