package com.workos.workos_backend.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.workos.workos_backend.config.WebSocketConfig;
import com.workos.workos_backend.dto.ChatMessageResponse;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.service.AccountService;
import com.workos.workos_backend.service.AuthService;
import com.workos.workos_backend.service.ChatMessageBroadcaster;
import com.workos.workos_backend.service.ChatService;

/**
 * End-to-end tests for the Phase 1 WebSocket foundation, driven over a real
 * embedded server (a real WebSocket upgrade, not MockMvc, since STOMP's
 * handshake/subscribe pipeline can't be exercised through mocked requests).
 * No {@code @Transactional} — the server processes each STOMP frame on its
 * own thread, which can't share a test-managed transaction with the test
 * thread — so every test creates its own uniquely-named account(s) and
 * conversation ids instead of relying on rollback between methods.
 *
 * <p>Subscription outcomes are verified behaviorally rather than by
 * inspecting STOMP ERROR frames: a successful, authorized SUBSCRIBE is
 * proven by actually receiving a message published afterward; a rejected
 * one is proven by the same message never arriving within a generous
 * timeout. This is deliberately the smallest reliable signal available —
 * it doesn't depend on exactly how {@link ChatChannelInterceptor}'s
 * exception surfaces on the wire, only on the one property that actually
 * matters: an unauthorized subscriber never receives messages from a
 * conversation it isn't part of.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-dev")
class ChatWebSocketIntegrationTest {

    private static final String PASSWORD = "Passw0rd-Test-123";

    /** How long a legitimate, authorized subscriber waits for a message that should arrive. */
    private static final long RECEIVE_TIMEOUT_SECONDS = 3;

    /** How long an unauthorized/rejected subscriber waits to prove nothing arrives. */
    private static final long NO_RECEIVE_TIMEOUT_SECONDS = 2;

    @LocalServerPort
    private int port;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ChatService chatService;

    private WebSocketStompClient stompClient;
    private final List<StompSession> openSessions = new ArrayList<>();

    static List<String> allEightAccessRoles() {
        return List.copyOf(AccountService.ACCESS_ROLES);
    }

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        for (StompSession session : openSessions) {
            try {
                if (session.isConnected()) {
                    session.disconnect();
                }
            } catch (RuntimeException ignored) {
                // Best-effort cleanup only.
            }
        }
    }

    // ---- 1. WebSocket configuration loads successfully — see WebSocketConfigTest. ----

    // ---- 2. Authenticated employee can establish a WebSocket/STOMP connection. ----
    @Test
    void authenticatedEmployeeCanConnect() throws Exception {
        String token = createAccountAndLogin("IT").token();

        StompSession session = connect(token);

        assertThat(session.isConnected()).isTrue();
    }

    // ---- 3. Unauthenticated connection is rejected. ----
    @Test
    void unauthenticatedConnectionIsRejected() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
    }

    // ---- 4 & 5. Identity comes from workos_session; a client cannot impersonate another Person. ----
    @Test
    void clientCannotImpersonateAnotherPersonBySupplyingADifferentPersonId() throws Exception {
        TestAccount caller = createAccountAndLogin("Sales Assistant");
        TestAccount victimA = createAccountAndLogin("Sales Manager");
        TestAccount victimB = createAccountAndLogin("Marketing");
        String victimsDm = "dm:" + sortedDm(victimA.person().getId(), victimB.person().getId());

        StompSession session = connect(caller.token());
        BlockingQueue<ChatMessageResponse> queue = subscribeWithSpoofedPersonId(session, victimsDm, victimA.person().getId());
        settle();

        chatService.sendMessage(victimA.person().getId(), victimsDm, "should not leak to the impersonator");

        assertThat(queue.poll(NO_RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("a spoofed personId header must never grant access to someone else's DM")
                .isNull();
    }

    // ---- 6. All 8 employee roles remain eligible for Chat. ----
    @ParameterizedTest
    @MethodSource("allEightAccessRoles")
    void everyAccessRoleCanEstablishAWebSocketConnection(String accessRole) throws Exception {
        String token = createAccountAndLogin(accessRole).token();

        StompSession session = connect(token);

        assertThat(session.isConnected()).isTrue();
    }

    // ---- 7. SUBSCRIBE to `general` is allowed for an authenticated employee. ----
    // ---- 13. Sending through the existing Spring Chat service broadcasts over STOMP to the right destination. ----
    @Test
    void subscribingToGeneralReceivesAMessageSentThroughTheExistingChatService() throws Exception {
        TestAccount account = createAccountAndLogin("HR");
        StompSession session = connect(account.token());
        BlockingQueue<ChatMessageResponse> queue = subscribe(session, ChatService.GENERAL_CHANNEL_ID);
        settle();

        chatService.sendMessage(account.person().getId(), ChatService.GENERAL_CHANNEL_ID, "hello over STOMP");

        ChatMessageResponse received = queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.conversationId()).isEqualTo(ChatService.GENERAL_CHANNEL_ID);
        assertThat(received.text()).isEqualTo("hello over STOMP");
        assertThat(received.authorId()).isEqualTo(account.person().getId());
    }

    // ---- 8. A user can subscribe to their own DM conversation. ----
    @Test
    void userCanSubscribeToTheirOwnDmConversation() throws Exception {
        TestAccount a = createAccountAndLogin("Graphics Designer");
        TestAccount b = createAccountAndLogin("Operation Manager");
        String dmId = "dm:" + sortedDm(a.person().getId(), b.person().getId());

        StompSession session = connect(a.token());
        BlockingQueue<ChatMessageResponse> queue = subscribe(session, dmId);
        settle();

        chatService.sendMessage(b.person().getId(), dmId, "hi from the other participant");

        ChatMessageResponse received = queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.text()).isEqualTo("hi from the other participant");
    }

    // ---- 9 & 14. A user cannot subscribe to another user's DM; a broadcast never leaks there. ----
    @Test
    void userCannotSubscribeToAnotherUsersDmConversation() throws Exception {
        TestAccount outsider = createAccountAndLogin("Sales Manager");
        TestAccount participantA = createAccountAndLogin("Sales Assistant");
        TestAccount participantB = createAccountAndLogin("Marketing");
        String otherDm = "dm:" + sortedDm(participantA.person().getId(), participantB.person().getId());

        StompSession session = connect(outsider.token());
        BlockingQueue<ChatMessageResponse> queue = subscribe(session, otherDm);
        settle();

        chatService.sendMessage(participantA.person().getId(), otherDm, "private, not for the outsider");

        assertThat(queue.poll(NO_RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("an outsider subscribed to someone else's DM topic must never receive its messages")
                .isNull();
    }

    // ---- 10, 11, 12: existing REST/persistence/SSE Chat tests are unchanged files, run via the full suite. ----

    private record TestAccount(Person person, String token) {
    }

    private TestAccount createAccountAndLogin(String accessRole) {
        String unique = UUID.randomUUID().toString();
        Person person = accountService.createAccount(
                "WS Test " + unique, unique + "@workos.test", PASSWORD, accessRole);
        AuthService.IssuedSession issued = authService.login(person.getEmail(), PASSWORD);
        return new TestAccount(person, issued.session().getToken());
    }

    private static String sortedDm(String personIdA, String personIdB) {
        return personIdA.compareTo(personIdB) <= 0
                ? personIdA + ":" + personIdB
                : personIdB + ":" + personIdA;
    }

    private StompSession connect(String token) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (token != null) {
            headers.add("Cookie", AuthService.COOKIE_NAME + "=" + token);
        }
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + WebSocketConfig.STOMP_ENDPOINT, headers,
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
        openSessions.add(session);
        return session;
    }

    private BlockingQueue<ChatMessageResponse> subscribe(StompSession session, String conversationId) {
        return subscribeWithSpoofedPersonId(session, conversationId, null);
    }

    private BlockingQueue<ChatMessageResponse> subscribeWithSpoofedPersonId(
            StompSession session, String conversationId, String spoofedPersonId) {
        BlockingQueue<ChatMessageResponse> queue = new LinkedBlockingQueue<>();
        StompHeaders headers = new StompHeaders();
        headers.setDestination(ChatMessageBroadcaster.STOMP_DESTINATION_PREFIX + conversationId);
        if (spoofedPersonId != null) {
            // The backend must never trust this — it only reads the identity
            // established server-side during the handshake.
            headers.add("personId", spoofedPersonId);
        }
        session.subscribe(headers, new QueueingFrameHandler(queue));
        return queue;
    }

    /**
     * Gives the server a brief moment to finish processing a just-sent
     * SUBSCRIBE frame before the test publishes a message — there is no
     * synchronous ack for a simple-broker subscription to wait on instead.
     */
    private static void settle() throws InterruptedException {
        Thread.sleep(300);
    }

    private static final class QueueingFrameHandler implements StompFrameHandler {
        private final BlockingQueue<ChatMessageResponse> queue;

        private QueueingFrameHandler(BlockingQueue<ChatMessageResponse> queue) {
            this.queue = queue;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return ChatMessageResponse.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            queue.add((ChatMessageResponse) payload);
        }
    }
}
