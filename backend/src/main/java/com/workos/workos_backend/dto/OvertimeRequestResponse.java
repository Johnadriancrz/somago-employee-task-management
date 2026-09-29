package com.workos.workos_backend.dto;

import com.workos.workos_backend.entity.OvertimeRequest;

public record OvertimeRequestResponse(
        String id,
        String personId,
        String personName,
        String workDate,
        double requestedHours,
        String reason,
        String status,
        String reviewedByPersonId,
        String reviewedAt,
        String reviewNote,
        String createdAt) {

    public static OvertimeRequestResponse from(OvertimeRequest request) {
        return new OvertimeRequestResponse(
                request.getId(),
                request.getPerson().getId(),
                request.getPerson().getName(),
                request.getWorkDate().toString(),
                request.getRequestedHours(),
                request.getReason(),
                request.getStatus().name(),
                request.getReviewedBy() != null ? request.getReviewedBy().getId() : null,
                request.getReviewedAt() != null ? request.getReviewedAt().toString() : null,
                request.getReviewNote(),
                request.getCreatedAt().toString());
    }
}
