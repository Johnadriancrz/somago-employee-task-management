package com.workos.workos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * POST /api/overtime/requests body. {@code workDate} is a plain ISO date
 * string ({@code yyyy-MM-dd}), parsed by {@code OvertimeRequestService} the
 * same way {@code TaskService.parseDate} parses {@code Task.start}/{@code
 * end}. The requester is never taken from this body — always the
 * server-resolved acting person, same discipline as every other
 * create-request DTO in this codebase.
 */
public record CreateOvertimeRequestRequest(

        @NotBlank(message = "workDate is required")
        String workDate,

        @NotNull(message = "requestedHours is required")
        @Positive(message = "requestedHours must be greater than 0")
        Double requestedHours,

        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason must be 500 characters or fewer")
        String reason) {
}
