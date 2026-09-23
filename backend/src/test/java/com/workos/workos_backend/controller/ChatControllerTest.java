package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.service.ChatService;

/**
 * HTTP-level tests for the Chat message endpoints, verifying status codes
 * and the exact JSON contract from BACKEND.md. Runs as the fixed local-dev
 * actor (sarah-chen); a DM the actor isn't part of is set up directly
 * through the service (same approach as TaskControllerTest) since the
 * local-dev actor is fixed for the whole Spring context.
 *
 * <p>{@code GET /api/chat/stream} is exercised by calling the controller
 * method directly rather than through MockMvc — its SseEmitter never times
 * out on its own, and driving that through MockMvc's async dispatch would
 * risk hanging the test run for no extra coverage: the method body is
 * synchronous (validate access, then hand back an emitter), so a direct
 * call exercises the exact same production code path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class ChatControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ChatController chatController;

    @Autowired
    private ChatService chatService;

    @Test
    void listMessagesRequiresConversationId() {
        MvcTestResult result = mvc.get().uri("/api/chat/messages").exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void listMessagesReturnsEmptyArrayForAConversationWithNoHistory() {
        MvcTestResult result = mvc.get().uri("/api/chat/messages?conversationId=general").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void listMessagesRejectsADmTheActorIsNotPartOfWith403() {
        chatService.sendMessage("alex-morgan", "dm:alex-morgan:priya-patel", "hi priya");

        MvcTestResult result =
                mvc.get().uri("/api/chat/messages?conversationId=dm:alex-morgan:priya-patel").exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void sendMessageReturns201WithDocumentedShapeAndServerDerivedAuthor() {
        MvcTestResult result = mvc.post().uri("/api/chat/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\": \"general\", \"text\": \"hello team\"}")
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.authorId").isEqualTo("sarah-chen");
        assertThat(result).bodyJson().extractingPath("$.conversationId").isEqualTo("general");
        assertThat(result).bodyJson().extractingPath("$.text").isEqualTo("hello team");
        assertThat(result).bodyJson().extractingPath("$.id").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.createdAt").isNotNull();
    }

    @Test
    void sendMessageRejectsBlankTextWith400() {
        MvcTestResult result = mvc.post().uri("/api/chat/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\": \"general\", \"text\": \"\"}")
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void sendMessageRejectsADmTheActorIsNotPartOfWith403() {
        MvcTestResult result = mvc.post().uri("/api/chat/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\": \"dm:alex-morgan:priya-patel\", \"text\": \"hi\"}")
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void sentMessagesShowUpInSubsequentHistory() {
        mvc.post().uri("/api/chat/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\": \"general\", \"text\": \"hello team\"}")
                .exchange();

        MvcTestResult result = mvc.get().uri("/api/chat/messages?conversationId=general").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$[0].text").isEqualTo("hello team");
    }

    @Test
    void streamRequiresConversationId() {
        assertThatThrownBy(() -> chatController.stream(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void streamReturnsAnEmitterForAnAuthorizedConversation() {
        SseEmitter emitter = chatController.stream("general");

        assertThat(emitter).isNotNull();
    }

    @Test
    void streamRejectsADmTheActorIsNotPartOfWith403() {
        assertThatThrownBy(() -> chatController.stream("dm:alex-morgan:priya-patel"))
                .isInstanceOf(ForbiddenException.class);
    }
}
