package com.workos.workos_backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Mirrors the frontend's {@code TimeEntry} type exactly
 * (workos-app/src/lib/types.ts). Not workspace-scoped — clock in/out is a
 * per-person record, matching {@code src/lib/server/time-repository.ts}'s
 * in-memory shape. {@code clockOut} is null while the entry is still open.
 * "At most one open entry per person" is enforced in
 * {@link com.workos.workos_backend.service.TimeEntryService}, not as a DB
 * constraint — same as every other business rule in this codebase.
 */
@Entity
@Table(name = "time_entries")
public class TimeEntry extends AssignedIdEntity {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "clock_in", nullable = false)
    private Instant clockIn;

    @Column(name = "clock_out")
    private Instant clockOut;

    protected TimeEntry() {
    }

    public TimeEntry(String id, Person person, Instant clockIn) {
        this.id = id;
        this.person = person;
        this.clockIn = clockIn;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public Instant getClockIn() {
        return clockIn;
    }

    public void setClockIn(Instant clockIn) {
        this.clockIn = clockIn;
    }

    public Instant getClockOut() {
        return clockOut;
    }

    public void setClockOut(Instant clockOut) {
        this.clockOut = clockOut;
    }
}
