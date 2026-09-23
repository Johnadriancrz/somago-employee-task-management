package com.workos.workos_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.workos.workos_backend.actor.ActingPersonResolver;
import com.workos.workos_backend.dto.ChatMessageResponse;
import com.workos.workos_backend.dto.SendChatMessageRequest;
import com.workos.workos_backend.entity.ChatMessage;
import com.workos.workos_backend.service.ChatMessageBroadcaster;
import com.workos.workos_backend.service.ChatService;

import jakarta.validation.Valid;

/** Implements the Chat endpoint table from BACKEND.md. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageBroadcaster broadcaster;
    private final ActingPersonResolver actingPersonResolver;

    public ChatController(
            ChatService chatService,
            ChatMessageBroadcaster broadcaster,
            ActingPersonResolver actingPersonResolver) {
        this.chatService = chatService;
        this.broadcaster = broadcaster;
        this.actingPersonResolver = actingPersonResolver;
    }

    @GetMapping("/messages")
    public List<ChatMessageResponse> listMessages(@RequestParam(required = false) String conversationId) {
        requireConversationId(conversationId);
        String actorId = actingPersonResolver.currentPersonId();
        return chatService.listMessages(actorId, conversationId).stream().map(ChatMessageResponse::from).toList();
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse sendMessage(@Valid @RequestBody SendChatMessageRequest request) {
        String actorId = actingPersonResolver.currentPersonId();
        ChatMessage message = chatService.sendMessage(actorId, request.conversationId(), request.text());
        return ChatMessageResponse.from(message);
    }

    /**
     * Server-Sent Events — one {@code ChatMessage} event per message posted
     * to this conversation, matching BACKEND.md's {@code GET
     * /api/chat/stream} contract. Access is validated up front, before the
     * emitter is created, so a caller with no access to the conversation
     * never opens a stream at all.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String conversationId) {
        requireConversationId(conversationId);
        String actorId = actingPersonResolver.currentPersonId();
        chatService.requireAccess(actorId, conversationId);
        return broadcaster.subscribe(conversationId);
    }

    private static void requireConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId is required");
        }
    }
}
