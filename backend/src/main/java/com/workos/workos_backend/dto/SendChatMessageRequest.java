package com.workos.workos_backend.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/chat/messages body: {@code { conversationId, text } } (BACKEND.md). */
public record SendChatMessageRequest(

        @NotBlank(message = "conversationId is required")
        String conversationId,

        @NotBlank(message = "text is required")
        String text) {
}
