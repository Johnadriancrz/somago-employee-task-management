package com.workos.workos_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One entry of {@code Task.attachments} in a create/update request body,
 * matching the frontend's {@code Attachment} type exactly
 * (workos-app/src/lib/types.ts). {@code dataUrl} is the base64 {@code data:}
 * URI the frontend's FilesPicker already produces client-side — this phase
 * keeps that contract unchanged (see BACKEND.md / the audit); TaskService
 * re-enforces the 5MB cap server-side since FilesPicker's own cap is
 * client-only and trivially bypassable via a direct API call.
 */
public record AttachmentInput(

        @NotBlank(message = "attachment id is required")
        String id,

        @NotBlank(message = "attachment name is required")
        @Size(max = 255, message = "attachment name must be 255 characters or fewer")
        String name,

        @Min(value = 0, message = "attachment size cannot be negative")
        long size,

        @NotBlank(message = "attachment type is required")
        @Size(max = 150, message = "attachment type must be 150 characters or fewer")
        String type,

        @NotBlank(message = "attachment dataUrl is required")
        String dataUrl) {
}
