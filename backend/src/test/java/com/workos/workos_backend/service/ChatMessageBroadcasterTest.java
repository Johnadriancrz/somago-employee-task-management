package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitterTestSupport;

import com.workos.workos_backend.dto.ChatMessageResponse;

/**
 * Plain unit test (no Spring context) for the in-process SSE registry
 * backing {@code GET /api/chat/stream}: subscriber bookkeeping, cleanup on
 * completion, and per-conversation scoping — the properties BACKEND.md's
 * SSE requirements care about ("avoid leaking messages across conversations
 * or users", "ensure clients can disconnect cleanly"). Calling
 * {@code emitter.complete()} directly is a no-op until a request handler is
 * installed, so {@link SseEmitterTestSupport} (deliberately placed in
 * Spring's own package) installs one and hands back the completion callback
 * a real async request would invoke — that's what the cleanup tests below
 * call to simulate the client disconnecting, without needing a full
 * round-trip through MockMvc's async dispatch (which, for an emitter that
 * intentionally never times out, would risk hanging the test run).
 * ChatServiceTest and ChatControllerTest cover the permission checks that
 * gate this endpoint.
 */
class ChatMessageBroadcasterTest {

    private final ChatMessageBroadcaster broadcaster = new ChatMessageBroadcaster();

    private static ChatMessageResponse sampleMessage(String conversationId) {
        return new ChatMessageResponse("msg-1", conversationId, "sarah-chen", "hi", Instant.now().toString());
    }

    @Test
    void subscribeRegistersAnEmitterForThatConversation() {
        broadcaster.subscribe("general");

        assertThat(broadcaster.activeSubscriberCount("general")).isEqualTo(1);
    }

    @Test
    void subscribersAreScopedPerConversation() {
        broadcaster.subscribe("general");
        broadcaster.subscribe("dm:alex-morgan:sarah-chen");

        assertThat(broadcaster.activeSubscriberCount("general")).isEqualTo(1);
        assertThat(broadcaster.activeSubscriberCount("dm:alex-morgan:sarah-chen")).isEqualTo(1);
        assertThat(broadcaster.activeSubscriberCount("dm:someone-else:another-person")).isEqualTo(0);
    }

    @Test
    void requestCompletionUnregistersTheEmitter() throws Exception {
        SseEmitter emitter = broadcaster.subscribe("general");
        Runnable onRequestCompleted = SseEmitterTestSupport.captureCompletionCallback(emitter);

        onRequestCompleted.run();

        assertThat(broadcaster.activeSubscriberCount("general")).isEqualTo(0);
    }

    @Test
    void oneSubscribersRequestCompletingDoesNotAffectAnother() throws Exception {
        SseEmitter first = broadcaster.subscribe("general");
        Runnable firstCompleted = SseEmitterTestSupport.captureCompletionCallback(first);
        broadcaster.subscribe("general");

        firstCompleted.run();

        assertThat(broadcaster.activeSubscriberCount("general")).isEqualTo(1);
    }

    @Test
    void publishToAConversationWithNoSubscribersDoesNotThrow() {
        assertThatCode(() -> broadcaster.publish("general", sampleMessage("general")))
                .doesNotThrowAnyException();
    }

    @Test
    void publishToASubscribedConversationDoesNotThrow() {
        broadcaster.subscribe("general");

        assertThatCode(() -> broadcaster.publish("general", sampleMessage("general")))
                .doesNotThrowAnyException();
    }
}
