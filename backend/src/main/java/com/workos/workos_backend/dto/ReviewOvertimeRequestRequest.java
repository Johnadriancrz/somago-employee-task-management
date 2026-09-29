package com.workos.workos_backend.dto;

import jakarta.validation.constraints.Size;

/** POST /api/overtime/requests/{id}/approve|reject body — an optional reviewer note. */
public record ReviewOvertimeRequestRequest(

        @Size(max = 500, message = "note must be 500 characters or fewer")
        String note) {
}
