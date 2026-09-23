package com.workos.workos_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Mirrors the frontend's {@code Subtask} type exactly (workos-app/src/lib/types.ts).
 * A merge-patch that includes {@code Task.subtasks} replaces the whole list
 * (see TaskService.replaceSubtasks) rather than diffing individual entries,
 * matching how the frontend always sends the full array; {@code position}
 * preserves the order the frontend relies on for display and for detecting
 * a pure done/checkmark toggle vs. a structural change (add/remove/rename/
 * reorder) for permission purposes.
 */
@Entity
@Table(name = "task_subtasks")
public class Subtask extends AssignedIdEntity {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private boolean done;

    @Column(nullable = false)
    private int position;

    protected Subtask() {
    }

    public Subtask(String id, String title, boolean done, int position) {
        this.id = id;
        this.title = title;
        this.done = done;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
