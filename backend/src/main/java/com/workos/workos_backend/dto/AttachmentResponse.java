package com.workos.workos_backend.dto;

import com.workos.workos_backend.entity.Attachment;

/** Matches the frontend's {@code Attachment} type exactly (workos-app/src/lib/types.ts). */
public record AttachmentResponse(String id, String name, long size, String type, String dataUrl) {

    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(), attachment.getName(), attachment.getSize(),
                attachment.getType(), attachment.getDataUrl());
    }
}
