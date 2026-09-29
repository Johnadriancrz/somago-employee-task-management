package com.workos.workos_backend.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.workos.workos_backend.config.WebSocketConfig;
import com.workos.workos_backend.dto.CreateTaskRequest;
import com.workos.workos_backend.dto.NotificationResponse;
import com.workos.workos_backend.dto.UpdateTaskRequest;
import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.Notification;
import com.workos.workos_backend.entity.NotificationEventType;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Task;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.entity.OvertimeRequest;
import com.workos.workos_backend.repository.NotificationPreferenceRepository;
import com.workos.workos_backend.repository.NotificationRepository;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.scheduler.ClockOutReminderScheduler;
import com.workos.workos_backend.service.AccountService;
import com.workos.workos_backend.service.AuthService;
import com.workos.workos_backend.service.BoardService;
import com.workos.workos_backend.service.ChatService;
import com.workos.workos_backend.service.NotificationService;
import com.workos.workos_backend.service.OvertimeRequestService;
import com.workos.workos_backend.service.TaskService;
import com.workos.workos_backend.service.TimeEntryService;
import com.workos.workos_backend.service.WorkspaceService;

/**
 * End-to-end tests for real-time Notifications delivery (spec sections 4-8,
 * 13), driven over a real embedded server exactly like {@link
 * ChatWebSocketIntegrationTest} — a real WebSocket/STOMP upgrade, not
 * MockMvc, since subscribe-time authorization can't be exercised through
 * mocked requests. No {@code @Transactional}: {@link NotificationService}
 * publishes after its own transaction commits, which a test-managed
 * transaction would roll back before that ever happens, so every test mints
 * its own uniquely-named accounts instead of relying on rollback between
 * methods.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-dev")
class NotificationWebSocketIntegrationTest {

    private static final String PASSWORD = "Passw0rd-Test-123";

    /** How long a legitimate, authorized subscriber waits for a message that should arrive. */
    private static final long RECEIVE_TIMEOUT_SECONDS = 3;

    /** How long an unauthorized/absent subscriber waits to prove nothing arrives. */
    private static final long NO_RECEIVE_TIMEOUT_SECONDS = 2;

    @LocalServerPort
    private int port;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AuthService authService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private TimeEntryService timeEntryService;

    @Autowired
    private BoardService boardService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private OvertimeRequestService overtimeRequestService;

    @Autowired
    private ClockOutReminderScheduler clockOutReminderScheduler;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private WebSocketStompClient stompClient;
    private final List<StompSession> openSessions = new ArrayList<>();

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

    // ---- Per-recipient delivery ----

    @Test
    void recipientReceivesTheirOwnNotificationOverItsOwnTopic() throws Exception {
        TestAccount employee = createAccountAndLogin("IT");
        StompSession session = connect(employee.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, employee.person().getId());
        settle();

        notificationService.notify(employee.person().getId(), NotificationEventType.CLOCK_IN,
                employee.person().getName() + " clocked in.");

        NotificationResponse received = queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.actorId()).isEqualTo(employee.person().getId());
        assertThat(received.eventType()).isEqualTo("CLOCK_IN");
    }

    @Test
    void ceoReceivesTheNotificationOverItsOwnTopic() throws Exception {
        TestAccount ceo = createAccountAndLogin("CEO");
        TestAccount employee = createAccountAndLogin("IT");
        StompSession session = connect(ceo.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, ceo.person().getId());
        settle();

        notificationService.notify(employee.person().getId(), NotificationEventType.CLOCK_IN,
                employee.person().getName() + " clocked in.");

        assertThat(queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void hrReceivesTheNotificationOverItsOwnTopic() throws Exception {
        TestAccount hr = createAccountAndLogin("HR");
        TestAccount employee = createAccountAndLogin("IT");
        StompSession session = connect(hr.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, hr.person().getId());
        settle();

        notificationService.notify(employee.person().getId(), NotificationEventType.CLOCK_IN,
                employee.person().getName() + " clocked in.");

        assertThat(queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void operationManagerReceivesTheNotificationOverItsOwnTopic() throws Exception {
        TestAccount om = createAccountAndLogin("Operation Manager");
        TestAccount employee = createAccountAndLogin("IT");
        StompSession session = connect(om.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, om.person().getId());
        settle();

        notificationService.notify(employee.person().getId(), NotificationEventType.CLOCK_IN,
                employee.person().getName() + " clocked in.");

        assertThat(queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void actorWhoIsAlsoManagementReceivesExactlyOneNotificationOverTheSocket() throws Exception {
        TestAccount ceo = createAccountAndLogin("CEO");
        StompSession session = connect(ceo.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, ceo.person().getId());
        settle();

        notificationService.notify(ceo.person().getId(), NotificationEventType.CLOCK_IN, "CEO clocked in.");

        assertThat(queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        assertThat(queue.poll(NO_RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("actor who is also management must receive exactly one notification, not a second copy")
                .isNull();
    }

    // ---- Authorization ----

    @Test
    void userCannotSubscribeToAnotherPersonsNotificationTopic() throws Exception {
        TestAccount outsider = createAccountAndLogin("IT");
        TestAccount victim = createAccountAndLogin("HR");

        StompSession session = connect(outsider.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, victim.person().getId());
        settle();

        notificationService.notify(victim.person().getId(), NotificationEventType.CLOCK_IN,
                victim.person().getName() + " clocked in.");

        assertThat(queue.poll(NO_RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("a caller must never receive another person's notifications")
                .isNull();
    }

    // ---- Event coverage: WebSocket publication fires for every notification-worthy action ----

    @Test
    void chatMessageEventIsPublishedOverWebSocketWithoutTheMessageText() throws Exception {
        TestAccount sender = createAccountAndLogin("IT");
        StompSession session = connect(sender.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, sender.person().getId());
        settle();

        chatService.sendMessage(sender.person().getId(), ChatService.GENERAL_CHANNEL_ID, "the private chat text");

        NotificationResponse received = queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("CHAT_MESSAGE");
        assertThat(received.message()).doesNotContain("the private chat text");
    }

    @Test
    void clockInEventIsPublishedOverWebSocket() throws Exception {
        TestAccount employee = createAccountAndLogin("IT");
        StompSession session = connect(employee.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, employee.person().getId());
        settle();

        timeEntryService.clockIn(employee.person().getId());

        NotificationResponse received = queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("CLOCK_IN");
    }

    @Test
    void clockOutEventIsPublishedOverWebSocket() throws Exception {
        TestAccount employee = createAccountAndLogin("IT");
        timeEntryService.clockIn(employee.person().getId());
        StompSession session = connect(employee.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, employee.person().getId());
        settle();

        timeEntryService.clockOut(employee.person().getId());

        NotificationResponse received = queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("CLOCK_OUT");
    }

    @Test
    void boardCreatedEventIsPublishedOverWebSocket() throws Exception {
        TestAccount employee = createAccountAndLogin("IT");
        Workspace workspace = workspaceService.createWorkspace(employee.person().getId(), "WS " + UUID.randomUUID(), null);
        StompSession session = connect(employee.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, employee.person().getId());
        settle();

        boardService.createBoard(employee.person().getId(), workspace.getId(), "Board", "", null);

        NotificationResponse received = queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("BOARD_CREATED");
    }

    @Test
    void taskWorkingEventIsPublishedOverWebSocket() throws Exception {
        assertTaskStatusTransitionPublishes("working", "TASK_WORKING");
    }

    @Test
    void taskStuckEventIsPublishedOverWebSocket() throws Exception {
        assertTaskStatusTransitionPublishes("stuck", "TASK_STUCK");
    }

    @Test
    void taskDoneEventIsPublishedOverWebSocket() throws Exception {
        assertTaskStatusTransitionPublishes("done", "TASK_DONE");
    }

    private void assertTaskStatusTransitionPublishes(String newStatus, String expectedEventType) throws Exception {
        TestAccount employee = createAccountAndLogin("IT");
        Workspace workspace = workspaceService.createWorkspace(employee.person().getId(), "WS " + UUID.randomUUID(), null);
        BoardMeta board = boardService.createBoard(employee.person().getId(), workspace.getId(), "Board", "", null);
        Task task = taskService.createTask(employee.person().getId(), new CreateTaskRequest(
                board.getId(), "Task", "this-week", "not-started", null, null, null, 1,
                "2026-01-01", "2026-01-01", "2026-01-01", 0, null, null, null, null, null));

        StompSession session = connect(employee.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, employee.person().getId());
        settle();

        taskService.updateTask(employee.person().getId(), task.getId(), new UpdateTaskRequest(
                null, null, newStatus, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null));

        NotificationResponse received = queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo(expectedEventType);
    }

    // ---- TASK_ASSIGNED: targeted delivery (spec section S2) ----

    @Test
    void taskAssignedEventIsPublishedOnlyToTheNewOwner() throws Exception {
        TestAccount om = createAccountAndLogin("Operation Manager");
        TestAccount newOwner = createAccountAndLogin("IT");
        Workspace workspace =
                workspaceService.createWorkspace(om.person().getId(), "WS " + UUID.randomUUID(), null);
        workspaceService.addMember(om.person().getId(), workspace.getId(), newOwner.person().getId());
        BoardMeta board = boardService.createBoard(om.person().getId(), workspace.getId(), "Board", "", null);
        Task task = taskService.createTask(om.person().getId(), new CreateTaskRequest(
                board.getId(), "Task", "this-week", "not-started", null, null, null, 1,
                "2026-01-01", "2026-01-01", "2026-01-01", 0, null, null, null, null, null));

        StompSession ownerSession = connect(newOwner.token());
        BlockingQueue<NotificationResponse> ownerQueue = subscribe(ownerSession, newOwner.person().getId());
        StompSession omSession = connect(om.token());
        BlockingQueue<NotificationResponse> omQueue = subscribe(omSession, om.person().getId());
        settle();

        taskService.updateTask(om.person().getId(), task.getId(), new UpdateTaskRequest(
                null, null, null, newOwner.person().getId(), null, null, null, null, null, null, null, null, null,
                null, null, null, null));

        NotificationResponse received = ownerQueue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("TASK_ASSIGNED");

        assertThat(omQueue.poll(NO_RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("the actor who reassigned the task must not receive a TASK_ASSIGNED notification")
                .isNull();
    }

    // ---- Time Clock + Overtime: targeted delivery ----

    @Test
    void overtimeRequestedEventIsPublishedOnlyToCeoHr() throws Exception {
        TestAccount ceo = createAccountAndLogin("CEO");
        TestAccount employee = createAccountAndLogin("IT");
        StompSession ceoSession = connect(ceo.token());
        BlockingQueue<NotificationResponse> ceoQueue = subscribe(ceoSession, ceo.person().getId());
        StompSession employeeSession = connect(employee.token());
        BlockingQueue<NotificationResponse> employeeQueue = subscribe(employeeSession, employee.person().getId());
        settle();

        overtimeRequestService.createRequest(employee.person().getId(), "2026-01-05", 2.0, "Deadline push");

        NotificationResponse received = ceoQueue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("OVERTIME_REQUESTED");

        assertThat(employeeQueue.poll(NO_RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("the requester must not receive their own OVERTIME_REQUESTED notification")
                .isNull();
    }

    @Test
    void overtimeApprovedEventIsPublishedOnlyToTheRequester() throws Exception {
        TestAccount ceo = createAccountAndLogin("CEO");
        TestAccount employee = createAccountAndLogin("IT");
        OvertimeRequest request = overtimeRequestService.createRequest(
                employee.person().getId(), "2026-01-05", 2.0, "Deadline push");

        StompSession employeeSession = connect(employee.token());
        BlockingQueue<NotificationResponse> employeeQueue = subscribe(employeeSession, employee.person().getId());
        StompSession ceoSession = connect(ceo.token());
        BlockingQueue<NotificationResponse> ceoQueue = subscribe(ceoSession, ceo.person().getId());
        settle();

        overtimeRequestService.approve(ceo.person().getId(), request.getId(), null);

        NotificationResponse received = employeeQueue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("OVERTIME_APPROVED");

        assertThat(ceoQueue.poll(NO_RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("the reviewer must not receive a copy of the requester's OVERTIME_APPROVED notification")
                .isNull();
    }

    @Test
    void overtimeRejectedEventIsPublishedOnlyToTheRequester() throws Exception {
        TestAccount hr = createAccountAndLogin("HR");
        TestAccount employee = createAccountAndLogin("IT");
        OvertimeRequest request = overtimeRequestService.createRequest(
                employee.person().getId(), "2026-01-05", 2.0, "Deadline push");

        StompSession employeeSession = connect(employee.token());
        BlockingQueue<NotificationResponse> employeeQueue = subscribe(employeeSession, employee.person().getId());
        settle();

        overtimeRequestService.reject(hr.person().getId(), request.getId(), "Not justified");

        NotificationResponse received = employeeQueue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("OVERTIME_REJECTED");
    }

    @Test
    void clockOutReminderEventIsPublishedOverWebSocket() throws Exception {
        TestAccount employee = createAccountAndLogin("IT");
        timeEntryService.clockIn(employee.person().getId());
        StompSession session = connect(employee.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, employee.person().getId());
        settle();

        clockOutReminderScheduler.remindOpenClockIns();

        NotificationResponse received = queue.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("CLOCK_OUT_REMINDER");
    }

    // ---- Transaction/commit ordering (spec sections 7, 10) ----

    @Test
    void notificationIsNeitherPersistedNorPublishedIfTheEnclosingTransactionRollsBack() throws Exception {
        TestAccount employee = createAccountAndLogin("IT");
        StompSession session = connect(employee.token());
        BlockingQueue<NotificationResponse> queue = subscribe(session, employee.person().getId());
        settle();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        List<String> createdId = new ArrayList<>();
        transactionTemplate.executeWithoutResult(status -> {
            List<Notification> created = notificationService.notifyRecipients(employee.person().getId(),
                    List.of(employee.person().getId()), NotificationEventType.TASK_ASSIGNED, "Assigned to you.");
            createdId.add(created.get(0).getId());
            status.setRollbackOnly();
        });

        assertThat(queue.poll(NO_RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("a rolled-back transaction must never publish its notification")
                .isNull();
        assertThat(notificationRepository.findById(createdId.get(0)))
                .as("a rolled-back transaction must never persist its notification")
                .isEmpty();
    }

    @Test
    void webSocketDeliveryFailureDoesNotRollBackTheAlreadyPersistedNotification() {
        TestAccount employee = createAccountAndLogin("IT");
        MessageChannel throwingChannel = (message, timeout) -> {
            throw new RuntimeException("simulated broker failure");
        };
        NotificationService serviceWithFailingSocket = new NotificationService(
                notificationRepository, notificationPreferenceRepository, personRepository,
                new SimpMessagingTemplate(throwingChannel));

        List<Notification> created = serviceWithFailingSocket.notify(
                employee.person().getId(), NotificationEventType.CLOCK_IN, "Clocked in.");

        assertThat(created).isNotEmpty();
        assertThat(notificationRepository.findById(created.get(0).getId())).isPresent();
    }

    private record TestAccount(Person person, String token) {
    }

    private TestAccount createAccountAndLogin(String accessRole) {
        String unique = UUID.randomUUID().toString();
        Person person = accountService.createAccount(
                "WS Notif Test " + unique, unique + "@workos.test", PASSWORD, accessRole);
        AuthService.IssuedSession issued = authService.login(person.getEmail(), PASSWORD);
        return new TestAccount(person, issued.session().getToken());
    }

    private StompSession connect(String token) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", AuthService.COOKIE_NAME + "=" + token);
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + WebSocketConfig.STOMP_ENDPOINT, headers,
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
        openSessions.add(session);
        return session;
    }

    private BlockingQueue<NotificationResponse> subscribe(StompSession session, String personId) {
        BlockingQueue<NotificationResponse> queue = new LinkedBlockingQueue<>();
        StompHeaders headers = new StompHeaders();
        headers.setDestination(NotificationService.STOMP_DESTINATION_PREFIX + personId);
        session.subscribe(headers, new QueueingFrameHandler(queue));
        return queue;
    }

    /**
     * Gives the server a brief moment to finish processing a just-sent
     * SUBSCRIBE frame before the test publishes a notification — there is no
     * synchronous ack for a simple-broker subscription to wait on instead.
     */
    private static void settle() throws InterruptedException {
        Thread.sleep(300);
    }

    private static final class QueueingFrameHandler implements StompFrameHandler {
        private final BlockingQueue<NotificationResponse> queue;

        private QueueingFrameHandler(BlockingQueue<NotificationResponse> queue) {
            this.queue = queue;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return NotificationResponse.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            queue.add((NotificationResponse) payload);
        }
    }
}
