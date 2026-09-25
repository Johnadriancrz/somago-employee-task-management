package com.workos.workos_backend.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.workos.workos_backend.dto.ChatMessageResponse;

import jakarta.annotation.PreDestroy;

/**
 * In-process pub/sub backing {@code GET /api/chat/stream} (BACKEND.md's SSE
 * endpoint) — same approach as the frontend's own in-memory
 * {@code chat-events.ts} {@code EventEmitter}: subscribers are tracked per
 * conversationId, and a publish only reaches emitters subscribed to that
 * exact conversation, so messages never leak across conversations or to
 * subscribers who aren't part of them. Only fans out within this server
 * process — multiple backend instances behind a load balancer would need a
 * shared pub/sub (Redis, etc.) instead, same caveat BACKEND.md documents for
 * the frontend's stub.
 *
 * <p>Phase 1 of the WebSocket migration (see the Chat WebSocket audit) adds
 * STOMP broadcasting alongside the existing SSE fan-out below, without
 * removing it: every {@link #publish} still reaches SSE subscribers exactly
 * as before, and now also publishes to the STOMP topic
 * {@code /topic/conversations/{conversationId}} via {@link
 * SimpMessagingTemplate}, so both transports observe the same persisted
 * messages from the same send path ({@link com.workos.workos_backend.service.ChatService#sendMessage}).
 */
@Component
public class ChatMessageBroadcaster {

    /** Matches the frontend stream route's own keep-alive cadence (BACKEND.md). */
    private static final long KEEP_ALIVE_INTERVAL_SECONDS = 25;

    /** No emitter timeout — the connection stays open until the client disconnects or the server shuts down. */
    private static final long NO_TIMEOUT = 0L;

    /**
     * STOMP destination prefix a conversation's messages are broadcast to —
     * {@code /topic/conversations/{conversationId}}, per the WebSocket
     * migration's approved conversation-id model (see {@link
     * com.workos.workos_backend.websocket.ChatChannelInterceptor}, which
     * authorizes SUBSCRIBEs to the same prefix).
     */
    public static final String STOMP_DESTINATION_PREFIX = "/topic/conversations/";

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByConversation = new ConcurrentHashMap<>();

    private final ScheduledExecutorService keepAliveScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "chat-sse-keep-alive");
                thread.setDaemon(true);
                return thread;
            });

    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessageBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** Registers a new SSE subscriber for a conversation; the caller must already have validated access. */
    public SseEmitter subscribe(String conversationId) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        CopyOnWriteArrayList<SseEmitter> emitters =
                emittersByConversation.computeIfAbsent(conversationId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        ScheduledFuture<?> keepAlive = keepAliveScheduler.scheduleAtFixedRate(
                () -> sendKeepAlive(emitter),
                KEEP_ALIVE_INTERVAL_SECONDS, KEEP_ALIVE_INTERVAL_SECONDS, TimeUnit.SECONDS);

        Runnable cleanup = () -> {
            keepAlive.cancel(true);
            unregister(conversationId, emitter);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        return emitter;
    }

    /**
     * Delivers a newly-sent message to every SSE subscriber of its
     * conversation (no others receive it), and to the matching STOMP topic
     * ({@code /topic/conversations/{conversationId}}) — the broker itself
     * only fans that out to sessions actually subscribed to it, and {@link
     * com.workos.workos_backend.websocket.ChatChannelInterceptor} already
     * refused any SUBSCRIBE the caller wasn't authorized for, so no
     * additional access check is needed here.
     */
    public void publish(String conversationId, ChatMessageResponse message) {
        List<SseEmitter> emitters = emittersByConversation.get(conversationId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().data(message));
                } catch (IOException | IllegalStateException ex) {
                    emitter.completeWithError(ex);
                }
            }
        }
        messagingTemplate.convertAndSend(STOMP_DESTINATION_PREFIX + conversationId, message);
    }

    /** Number of currently-registered subscribers for a conversation — exposed for tests. */
    int activeSubscriberCount(String conversationId) {
        List<SseEmitter> emitters = emittersByConversation.get(conversationId);
        return emitters == null ? 0 : emitters.size();
    }

    private void sendKeepAlive(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("ping"));
        } catch (IOException | IllegalStateException ex) {
            emitter.completeWithError(ex);
        }
    }

    private void unregister(String conversationId, SseEmitter emitter) {
        emittersByConversation.computeIfPresent(conversationId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    @PreDestroy
    void shutdown() {
        keepAliveScheduler.shutdownNow();
    }
}
