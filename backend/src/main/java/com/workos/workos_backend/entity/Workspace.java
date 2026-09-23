package com.workos.workos_backend.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Mirrors the frontend's {@code Workspace} type (workos-app/src/lib/types.ts).
 * {@code memberIds} is modeled as a real many-to-many relationship
 * (workspace_members join table), not a comma-separated column, so
 * membership is queryable and FK-enforced.
 *
 * Business rule enforced here: the owner must always also be a member
 * (BACKEND.md: "the signed-in person becomes ownerId and its sole initial
 * member"; membership never drops below including the owner).
 */
@Entity
@Table(name = "workspaces")
public class Workspace extends AssignedIdEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 8)
    private String initials;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Person owner;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "workspace_members",
            joinColumns = @JoinColumn(name = "workspace_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id"))
    private Set<Person> members = new LinkedHashSet<>();

    @OneToMany(mappedBy = "workspace", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BoardMeta> boards = new ArrayList<>();

    protected Workspace() {
    }

    public Workspace(String id, String name, String initials, Person owner) {
        this.id = id;
        this.name = name;
        this.initials = initials;
        this.owner = owner;
        this.members.add(owner);
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInitials() {
        return initials;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    public Person getOwner() {
        return owner;
    }

    public void setOwner(Person owner) {
        this.owner = owner;
    }

    public Set<Person> getMembers() {
        return members;
    }

    public void addMember(Person person) {
        members.add(person);
    }

    public void removeMember(Person person) {
        members.remove(person);
    }

    public List<BoardMeta> getBoards() {
        return boards;
    }

    @PrePersist
    @PreUpdate
    private void validateOwnerIsMember() {
        if (owner != null && !members.contains(owner)) {
            throw new IllegalStateException("Workspace owner must also be a workspace member");
        }
    }
}
