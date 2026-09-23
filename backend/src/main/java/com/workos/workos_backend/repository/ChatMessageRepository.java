package com.workos.workos_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    /** GET /api/chat/messages?conversationId=... — oldest first (BACKEND.md). */
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);
}
