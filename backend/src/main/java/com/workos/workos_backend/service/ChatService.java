package com.workos.workos_backend.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.dto.ChatMessageResponse;
import com.workos.workos_backend.entity.ChatMessage;
import com.workos.workos_backend.entity.NotificationEventType;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.repository.ChatMessageRepository;
import com.workos.workos_backend.repository.PersonRepository;

/**
 * Business logic for the Chat endpoints (BACKEND.md's Chat table).
 * {@code conversationId} is a derived string, not a stored entity: it is
 * either {@code "general"} (the one built-in channel, open to everyone) or a
 * DM id shaped {@code dm:<personIdA>:<personIdB>} with the two ids sorted
 * (see the frontend's {@code src/lib/conversation-id.ts}). There is no
 * "create a conversation" step — a DM's id is computed the same way by both
 * participants, and the first message posted to it just starts appearing.
 * Reading or posting to a DM the actor isn't part of is rejected with 403.
 */
@Service
public class ChatService {

    public static final String GENERAL_CHANNEL_ID = "general";
    private static final String DM_PREFIX = "dm:";

    private final ChatMessageRepository chatMessageRepository;
    private final PersonRepository personRepository;
    private final ChatMessageBroadcaster broadcaster;
    private final NotificationService notificationService;

    public ChatService(
            ChatMessageRepository chatMessageRepository,
            PersonRepository personRepository,
            ChatMessageBroadcaster broadcaster,
            NotificationService notificationService) {
        this.chatMessageRepository = chatMessageRepository;
        this.personRepository = personRepository;
        this.broadcaster = broadcaster;
        this.notificationService = notificationService;
    }

    /** Mirrors {@code canAccessConversation} in the frontend's {@code chat-repository.ts} exactly. */
    public boolean canAccess(String personId, String conversationId) {
        if (GENERAL_CHANNEL_ID.equals(conversationId)) {
            return true;
        }
        String[] participants = parseDmParticipants(conversationId);
        return participants != null && (participants[0].equals(personId) || participants[1].equals(personId));
    }

    /** @throws ForbiddenException if the actor may not read/post to this conversation. */
    public void requireAccess(String actorId, String conversationId) {
        if (!canAccess(actorId, conversationId)) {
            throw new ForbiddenException("Not a participant in this conversation");
        }
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> listMessages(String actorId, String conversationId) {
        requireAccess(actorId, conversationId);
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional
    public ChatMessage sendMessage(String actorId, String conversationId, String text) {
        requireAccess(actorId, conversationId);
        Person author = personRepository.findById(actorId)
                .orElseThrow(() -> new IllegalStateException("Acting person not found: " + actorId));
        ChatMessage message = new ChatMessage(
                UUID.randomUUID().toString(), conversationId, author, text.trim(), Instant.now());
        // saveAndFlush: see the matching comment in WorkspaceService.createWorkspace —
        // ChatMessage has the same assigned-id Persistable.isNew() behavior.
        ChatMessage saved = chatMessageRepository.saveAndFlush(message);
        broadcaster.publish(conversationId, ChatMessageResponse.from(saved));
        notificationService.notify(actorId, NotificationEventType.CHAT_MESSAGE, author.getName() + " sent a message.");
        return saved;
    }

    private static String[] parseDmParticipants(String conversationId) {
        if (conversationId == null || !conversationId.startsWith(DM_PREFIX)) {
            return null;
        }
        String[] parts = conversationId.substring(DM_PREFIX.length()).split(":", -1);
        return parts.length == 2 ? parts : null;
    }
}
