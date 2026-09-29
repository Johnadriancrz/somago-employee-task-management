package com.workos.workos_backend.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * An employee's request to have hours worked beyond the normal 8-hour
 * workday on {@link #workDate} counted as overtime. Time beyond 8 hours is
 * never automatically overtime (Time Clock + Overtime spec) — it only
 * becomes {@code approvedOvertimeHours} in {@code TimeSummaryService} once a
 * CEO/HR reviewer sets this request's status to {@link
 * OvertimeRequestStatus#APPROVED}, and even then it's capped at whatever was
 * actually worked that day.
 *
 * <p>{@code requestedHours} is the amount the requester is asking to have
 * approved; approval is binary (grants the full amount) or rejection (grants
 * none) — there is no partial-approval concept.
 */
@Entity
@Table(name = "overtime_requests")
public class OvertimeRequest extends AssignedIdEntity {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "requested_hours", nullable = false)
    private double requestedHours;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OvertimeRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Person reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OvertimeRequest() {
    }

    public OvertimeRequest(String id, Person person, LocalDate workDate, double requestedHours, String reason,
            Instant createdAt) {
        this.id = id;
        this.person = person;
        this.workDate = workDate;
        this.requestedHours = requestedHours;
        this.reason = reason;
        this.status = OvertimeRequestStatus.PENDING;
        this.createdAt = createdAt;
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

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public double getRequestedHours() {
        return requestedHours;
    }

    public void setRequestedHours(double requestedHours) {
        this.requestedHours = requestedHours;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OvertimeRequestStatus getStatus() {
        return status;
    }

    public void setStatus(OvertimeRequestStatus status) {
        this.status = status;
    }

    public Person getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Person reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
