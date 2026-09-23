package com.workos.workos_backend.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Mirrors the frontend's {@code Task} type exactly (workos-app/src/lib/types.ts).
 * A task's board membership is structural in the frontend (tasks live in a
 * {@code Record<BoardId, Task[]>}), not a field on {@code Task} itself — the
 * relational schema needs an explicit {@code board_id} foreign key, added
 * here as {@link #board}. Deleting a board cascades to its tasks (matching
 * the existing "DELETE /api/boards/:id also deletes every task on that
 * board" contract); deleting a workspace cascades through its boards to
 * their tasks transitively via the same ON DELETE CASCADE chain.
 */
@Entity
@Table(name = "tasks")
public class Task extends AssignedIdEntity {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private BoardMeta board;

    @Column(nullable = false)
    private String title;

    @Column(name = "task_group", nullable = false, length = 20)
    private TaskGroup group;

    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Person owner;

    /**
     * Separate from {@link #owner} — 0+ people, not workspace-scoped. The
     * frontend's OwnerPicker/AssigneesPicker pull from the global people
     * list, not the task's workspace roster, so membership is intentionally
     * not enforced here — only that the referenced Person exists.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "task_assignees",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id"))
    private Set<Person> assignees = new LinkedHashSet<>();

    @Column(length = 100)
    private String tag;

    @Column(nullable = false)
    private int priority;

    /** Display label (e.g. "Sep 19"), not a parseable date — matches the frontend's dueDate contract exactly. */
    @Column(name = "due_date", nullable = false, length = 50)
    private String dueDate;

    @Column(name = "start_date", nullable = false)
    private LocalDate start;

    @Column(name = "end_date", nullable = false)
    private LocalDate end;

    @Column(nullable = false)
    private int progress;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Subtask> subtasks = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Attachment> attachments = new ArrayList<>();

    @Column(length = 2000)
    private String blocker;

    @Column(length = 2000)
    private String note;

    /**
     * Unused by the current frontend (no dependency-arrow rendering exists
     * yet — see the frontend/backend audit, Section 5.1/10.4) but included
     * per the approved field list. {@code ON DELETE SET NULL}: deleting the
     * depended-on task clears the reference rather than blocking the
     * delete or cascading further deletes.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depends_on_task_id")
    private Task dependsOn;

    /** Epoch ms, stamped client-side on every real edit (see store.tsx); accepted as sent, not server-derived. */
    @Column(name = "updated_at")
    private Long updatedAt;

    protected Task() {
    }

    public Task(String id, BoardMeta board, String title, TaskGroup group, TaskStatus status, Person owner,
            String tag, int priority, String dueDate, LocalDate start, LocalDate end, int progress) {
        this.id = id;
        this.board = board;
        this.title = title;
        this.group = group;
        this.status = status;
        this.owner = owner;
        this.tag = tag;
        this.priority = priority;
        this.dueDate = dueDate;
        this.start = start;
        this.end = end;
        this.progress = progress;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BoardMeta getBoard() {
        return board;
    }

    public void setBoard(BoardMeta board) {
        this.board = board;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public TaskGroup getGroup() {
        return group;
    }

    public void setGroup(TaskGroup group) {
        this.group = group;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Person getOwner() {
        return owner;
    }

    public void setOwner(Person owner) {
        this.owner = owner;
    }

    public Set<Person> getAssignees() {
        return assignees;
    }

    public void addAssignee(Person person) {
        assignees.add(person);
    }

    public void clearAssignees() {
        assignees.clear();
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getStart() {
        return start;
    }

    public void setStart(LocalDate start) {
        this.start = start;
    }

    public LocalDate getEnd() {
        return end;
    }

    public void setEnd(LocalDate end) {
        this.end = end;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public List<Subtask> getSubtasks() {
        return subtasks;
    }

    /** Replaces the whole list, matching the frontend always sending the full array on a subtasks-bearing patch. */
    public void replaceSubtasks(List<Subtask> newSubtasks) {
        subtasks.clear();
        for (Subtask subtask : newSubtasks) {
            subtask.setTask(this);
            subtasks.add(subtask);
        }
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    /** Replaces the whole list, matching the frontend always sending the full array on an attachments-bearing patch. */
    public void replaceAttachments(List<Attachment> newAttachments) {
        attachments.clear();
        for (Attachment attachment : newAttachments) {
            attachment.setTask(this);
            attachments.add(attachment);
        }
    }

    public String getBlocker() {
        return blocker;
    }

    public void setBlocker(String blocker) {
        this.blocker = blocker;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Task getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(Task dependsOn) {
        this.dependsOn = dependsOn;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
