package com.workos.workos_backend.dto;

import com.workos.workos_backend.entity.ChatMessage;

/**
 * Matches the frontend's {@code ChatMessage} type exactly
 * (workos-app/src/lib/types.ts). {@code createdAt} is serialized via
 * {@code Instant.toString()} (ISO-8601), same convention as
 * {@link TimeEntryResponse}.
 */
public record ChatMessageResponse(String id, String conversationId, String authorId, String text, String createdAt) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getAuthor().getId(),
                message.getText(),
                message.getCreatedAt().toString());
    }
}
