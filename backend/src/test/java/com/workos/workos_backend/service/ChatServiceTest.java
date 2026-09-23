package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.ChatMessage;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.repository.ChatMessageRepository;

import jakarta.persistence.EntityManager;

/**
 * Exercises ChatService's business rules directly, same approach as
 * TimeEntryServiceTest — actor ids are passed explicitly so both
 * participants of a DM (and a third, unrelated person) can be tested
 * against the seeded local-dev people without touching the fixed HTTP
 * actor.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void generalChannelIsAccessibleToAnyone() {
        assertThat(chatService.canAccess("sarah-chen", "general")).isTrue();
        assertThat(chatService.canAccess("alex-morgan", "general")).isTrue();
    }

    @Test
    void dmIsAccessibleOnlyToItsTwoParticipants() {
        String dmId = "dm:alex-morgan:sarah-chen";

        assertThat(chatService.canAccess("sarah-chen", dmId)).isTrue();
        assertThat(chatService.canAccess("alex-morgan", dmId)).isTrue();
        assertThat(chatService.canAccess("priya-patel", dmId)).isFalse();
    }

    @Test
    void unrecognizedConversationIdIsNotAccessible() {
        assertThat(chatService.canAccess("sarah-chen", "not-a-real-conversation")).isFalse();
    }

    @Test
    void sendMessagePersistsWithServerDerivedAuthorAndTimestamp() {
        ChatMessage message = chatService.sendMessage("sarah-chen", "general", "hello team");

        assertThat(message.getId()).isNotNull();
        assertThat(message.getAuthor().getId()).isEqualTo("sarah-chen");
        assertThat(message.getConversationId()).isEqualTo("general");
        assertThat(message.getText()).isEqualTo("hello team");
        assertThat(message.getCreatedAt()).isNotNull();
    }

    @Test
    void sendMessageToADmTheActorIsNotPartOfIsForbidden() {
        String dmId = "dm:alex-morgan:priya-patel";

        assertThatThrownBy(() -> chatService.sendMessage("sarah-chen", dmId, "hi"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listMessagesForADmTheActorIsNotPartOfIsForbidden() {
        String dmId = "dm:alex-morgan:priya-patel";
        chatService.sendMessage("alex-morgan", dmId, "hi priya");

        assertThatThrownBy(() -> chatService.listMessages("sarah-chen", dmId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listMessagesReturnsOldestFirst() throws InterruptedException {
        chatService.sendMessage("sarah-chen", "general", "first");
        Thread.sleep(5);
        chatService.sendMessage("alex-morgan", "general", "second");
        Thread.sleep(5);
        chatService.sendMessage("sarah-chen", "general", "third");

        List<ChatMessage> messages = chatService.listMessages("sarah-chen", "general");

        assertThat(messages).extracting(ChatMessage::getText).containsExactly("first", "second", "third");
    }

    @Test
    void listMessagesOnlyReturnsMessagesForThatConversation() {
        chatService.sendMessage("sarah-chen", "general", "in general");
        chatService.sendMessage("sarah-chen", "dm:alex-morgan:sarah-chen", "in dm");

        List<ChatMessage> generalMessages = chatService.listMessages("sarah-chen", "general");

        assertThat(generalMessages).extracting(ChatMessage::getText).containsExactly("in general");
    }

    @Test
    void chatMessagePersistsAcrossAReload() {
        ChatMessage message = chatService.sendMessage("sarah-chen", "general", "persisted?");
        String messageId = message.getId();
        entityManager.flush();
        entityManager.clear();

        ChatMessage reloaded = chatMessageRepository.findById(messageId).orElseThrow();
        assertThat(reloaded.getText()).isEqualTo("persisted?");
        assertThat(reloaded.getAuthor().getId()).isEqualTo("sarah-chen");
    }
}
