import type {
  BoardId,
  BoardMeta,
  ChatMessage,
  CompletedTaskReport,
  ConversationId,
  NewBoardInput,
  NewTaskInput,
  NewWorkspaceInput,
  Notification,
  NotificationPreferences,
  Person,
  Task,
  TimeEntry,
  Workspace,
} from "./types";

/**
 * The only module that knows the API is HTTP. Everything else (the store,
 * the auth context) calls these functions and gets back plain typed
 * data/promises — so the transport (fetch to Next.js route handlers today,
 * anything else later) can change without touching a single component.
 */

/**
 * Base URL for the Spring Boot backend (People/Workspaces/Boards/Tasks).
 * Must be NEXT_PUBLIC_-prefixed since this module runs in the browser.
 * Falls back to the local dev server so `npm run dev` works with no .env
 * file present — see .env.example to override it.
 */
export const SPRING_API_BASE_URL = process.env.NEXT_PUBLIC_SPRING_API_BASE_URL ?? "http://localhost:8080";

let recoveringSession = false;

/**
 * The session cookie can outlive the server's in-memory session it points
 * to (e.g. a dev server restart) — proxy.ts only checks the cookie is
 * present, not that it's still valid, so a stale one gets past it and only
 * fails once a route handler actually looks it up. When that happens, clear
 * the dead cookie and send the user back to login instead of leaving the
 * page stuck with failed fetches.
 */
function handleStaleSession() {
  if (recoveringSession || typeof window === "undefined") return;
  recoveringSession = true;
  fetch(`${SPRING_API_BASE_URL}/api/auth/logout`, { method: "POST", credentials: "include" }).finally(() => {
    // A hard navigation, not router.push — this module has no router access
    // (it's not a component), and a full reload is what we actually want
    // here: it guarantees AuthProvider/BoardProvider/ClockProvider remount
    // clean instead of a client-side transition risking stale context state.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.href = "/login";
  });
}

/** Same idea as handleStaleSession, but for the fully separate admin session/cookie. */
let recoveringAdminSession = false;
function handleStaleAdminSession() {
  if (recoveringAdminSession || typeof window === "undefined") return;
  recoveringAdminSession = true;
  fetch(`${SPRING_API_BASE_URL}/api/admin/auth/logout`, { method: "POST", credentials: "include" }).finally(() => {
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.href = "/admin/login";
  });
}

/**
 * @param path Relative Next.js API path (e.g. "/api/tasks"), used as-is for
 * request identity (401 routing, error messages) regardless of `baseUrl`.
 * @param baseUrl Prefixed onto `path` to build the actual fetch URL. Empty
 * (the default) targets the Next.js stub at a relative path; a real origin
 * targets a separate HTTP service instead — see `springRequest`.
 */
async function request<T>(path: string, init?: RequestInit, baseUrl = ""): Promise<T> {
  const res = await fetch(`${baseUrl}${path}`, {
    ...init,
    // Required for the browser to send/receive the Spring session cookie on
    // springRequest's cross-origin (different-port) calls — see
    // CorsConfig.allowCredentials(true) on the backend. A no-op for
    // same-origin relative `request()` calls (Chat/Reset/legacy admin),
    // which already send cookies by default.
    credentials: "include",
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  const isAdminGatedPath =
    path === "/api/accounts" || (path.startsWith("/api/admin/") && !path.startsWith("/api/admin/auth/"));
  if (res.status === 401 && isAdminGatedPath) {
    handleStaleAdminSession();
  } else if (res.status === 401 && !path.startsWith("/api/auth/") && !path.startsWith("/api/admin/")) {
    handleStaleSession();
  }
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.error ?? `${init?.method ?? "GET"} ${path} failed: ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

/**
 * Same request/error handling as `request`, but against the Spring Boot
 * backend's absolute base URL instead of a relative Next.js route. Used by
 * `fetchPeople`, the Workspace functions, the Board functions, and the Task
 * functions below.
 */
export function springRequest<T>(path: string, init?: RequestInit): Promise<T> {
  return request(path, init, SPRING_API_BASE_URL);
}

// Tasks

/** Goes straight to the Spring Boot backend — see BACKEND.md's Tasks section. */
export function fetchAllTasks(): Promise<Record<BoardId, Task[]>> {
  return springRequest("/api/tasks");
}

export function createTaskRequest(boardId: BoardId, input: NewTaskInput): Promise<Task> {
  return springRequest("/api/tasks", {
    method: "POST",
    body: JSON.stringify({ boardId, ...input }),
  });
}

export function updateTaskRequest(taskId: string, patch: Partial<Task>): Promise<Task> {
  return springRequest(`/api/tasks/${taskId}`, {
    method: "PATCH",
    body: JSON.stringify(patch),
  });
}

export function deleteTaskRequest(taskId: string): Promise<void> {
  return springRequest(`/api/tasks/${taskId}`, { method: "DELETE" });
}

// Workspaces

/** Goes straight to the Spring Boot backend — see BACKEND.md's Workspaces section. */
export function fetchWorkspaces(): Promise<Workspace[]> {
  return springRequest("/api/workspaces");
}

export function createWorkspaceRequest(input: NewWorkspaceInput): Promise<Workspace> {
  return springRequest("/api/workspaces", { method: "POST", body: JSON.stringify(input) });
}

export function deleteWorkspaceRequest(workspaceId: string): Promise<void> {
  return springRequest(`/api/workspaces/${workspaceId}`, { method: "DELETE" });
}

export function addWorkspaceMemberRequest(workspaceId: string, personId: string): Promise<Workspace> {
  return springRequest(`/api/workspaces/${workspaceId}/members`, {
    method: "POST",
    body: JSON.stringify({ personId }),
  });
}

export function removeWorkspaceMemberRequest(workspaceId: string, personId: string): Promise<Workspace> {
  return springRequest(`/api/workspaces/${workspaceId}/members/${personId}`, { method: "DELETE" });
}

// Boards

/** Goes straight to the Spring Boot backend — see BACKEND.md's Boards section. */
export function fetchBoards(): Promise<BoardMeta[]> {
  return springRequest("/api/boards");
}

export function createBoardRequest(input: NewBoardInput): Promise<BoardMeta> {
  return springRequest("/api/boards", { method: "POST", body: JSON.stringify(input) });
}

export function updateBoardRequest(boardId: BoardId, patch: Partial<NewBoardInput>): Promise<BoardMeta> {
  return springRequest(`/api/boards/${boardId}`, { method: "PATCH", body: JSON.stringify(patch) });
}

export function deleteBoardRequest(boardId: BoardId): Promise<void> {
  return springRequest(`/api/boards/${boardId}`, { method: "DELETE" });
}

// People

/** Goes straight to the Spring Boot backend — see BACKEND.md's People section. */
export function fetchPeople(): Promise<Person[]> {
  return springRequest("/api/people");
}

/**
 * Chat's DM directory only — every Person with a real WorkOS login account
 * (non-null accessRole), so Chat never offers a seeded/demo workspace-member
 * Person who can't log in to read a DM. Scoped to Chat; every other
 * Board/task/workspace consumer keeps using `fetchPeople()`.
 */
export function fetchChatDirectory(): Promise<Person[]> {
  return springRequest("/api/people/chat-directory");
}

/**
 * Time Clock's employee list only — every Person with a real employee
 * account (non-null accessRole), matching the Admin "Employee accounts"
 * page's population. Excludes seeded/demo Person rows that have no login.
 * Scoped to Time Clock; every other Board/task/workspace consumer keeps
 * using `fetchPeople()`.
 */
export function fetchEmployeeDirectory(): Promise<Person[]> {
  return springRequest("/api/people/employee-directory");
}

// Combined reset

/**
 * Goes straight to the Spring Boot backend's `ResetController` (BACKEND.md's
 * Reset table) — the Next.js `/api/reset` stub only ever reset its own
 * in-memory data, never the real, persisted state. `local-dev`-profile only,
 * like the rest of the Spring reset machinery.
 */
export function resetAllDataRequest(): Promise<{
  workspaces: Workspace[];
  boards: BoardMeta[];
  tasksByBoard: Record<BoardId, Task[]>;
  people: Person[];
}> {
  return springRequest("/api/reset", { method: "POST" });
}

// Auth

/**
 * Backend-owned authentication (spec section 6) — goes straight to Spring,
 * same as People/Workspaces/Boards/Tasks/Time-clock. The response includes
 * the authenticated Person's persisted `accessRole`, never a client-
 * supplied one. The Next.js `/api/auth/*` routes and their in-memory
 * session store are no longer called from here; Chat's own identity
 * resolution (`requireSessionPersonId`) now validates against this same
 * Spring session instead (see `lib/server/require-session.ts`), so it
 * keeps working unchanged.
 */
export function loginRequest(email: string, password: string): Promise<Person> {
  return springRequest("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
}

export function logoutRequest(): Promise<void> {
  return springRequest("/api/auth/logout", { method: "POST" });
}

export function fetchMe(): Promise<Person> {
  return springRequest("/api/auth/me");
}

/**
 * Goes straight to the Spring Boot backend's `AuthController.changePassword`
 * — the caller's identity comes from the Spring session cookie (`credentials:
 * "include"`, same as every other `springRequest` call), never from a
 * client-supplied id. Requires the caller's own current password.
 */
export function changePasswordRequest(currentPassword: string, newPassword: string): Promise<void> {
  return springRequest("/api/auth/change-password", {
    method: "POST",
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

// Chat
//
// Phase 2 of the WebSocket migration (see the Chat WebSocket audit) cuts
// history/send over to the Spring Chat backend, so that sending a message
// actually reaches ChatMessageBroadcaster and gets published to STOMP —
// the Next.js in-memory Chat routes below never received traffic from either
// of these functions in the first place (this is genuinely empty per-process
// demo data, not persisted history), so nothing is lost by moving off them.

export function fetchMessages(conversationId: ConversationId): Promise<ChatMessage[]> {
  return springRequest(`/api/chat/messages?conversationId=${encodeURIComponent(conversationId)}`);
}

export function sendMessageRequest(conversationId: ConversationId, text: string): Promise<ChatMessage> {
  return springRequest("/api/chat/messages", {
    method: "POST",
    body: JSON.stringify({ conversationId, text }),
  });
}

/**
 * Opens a live SSE connection against the Next.js Chat stub. Retained,
 * untouched, as the Phase 2 migration's fallback/rollback path (see BACKEND.md
 * and the Chat WebSocket audit) — not currently called from the Chat page,
 * which now gets live delivery from the Spring STOMP socket instead (see
 * `lib/chat-socket.ts`). Since `fetchMessages`/`sendMessageRequest` above now
 * target Spring, re-enabling this would need pointing it at Spring's own
 * `GET /api/chat/stream` instead of the Next.js route below, which no longer
 * observes any traffic.
 */
export function openMessageStream(
  conversationId: ConversationId,
  onMessage: (message: ChatMessage) => void,
): () => void {
  const source = new EventSource(`/api/chat/stream?conversationId=${encodeURIComponent(conversationId)}`);
  source.onmessage = (event) => {
    onMessage(JSON.parse(event.data) as ChatMessage);
  };
  return () => source.close();
}

// Time clock

/** Goes straight to the Spring Boot backend — see BACKEND.md's Time clock section. */
export function fetchClockStatus(): Promise<TimeEntry | null> {
  return springRequest("/api/time/status");
}

export function clockInRequest(): Promise<TimeEntry> {
  return springRequest("/api/time/clock-in", { method: "POST" });
}

export function clockOutRequest(): Promise<TimeEntry> {
  return springRequest("/api/time/clock-out", { method: "POST" });
}

export function fetchTimeEntries(personId?: string): Promise<TimeEntry[]> {
  return springRequest(
    personId ? `/api/time/entries?personId=${encodeURIComponent(personId)}` : "/api/time/entries",
  );
}

// Reports

/**
 * Goes straight to the Spring Boot backend — see BACKEND.md's Reports
 * section. The response is already scoped server-side to the signed-in
 * person's visible boards and role (all completed tasks for CEO/Operation
 * Manager, own-only for every other role) — this is the authoritative
 * Reports dataset, not a client-side filter over `useBoard()`'s tasks.
 */
export function fetchCompletedTasks(): Promise<CompletedTaskReport[]> {
  return springRequest("/api/reports/completed-tasks");
}

// Notifications
//
// Goes straight to the Spring Boot backend (NotificationController, spec
// section 18) — the recipient is always the server-resolved actor from the
// session cookie, never a client-supplied id. Live delivery is over STOMP
// (see `lib/notification-socket.ts`); these are the REST hydration/reconcile
// side of that same data.

export function fetchNotifications(): Promise<Notification[]> {
  return springRequest("/api/notifications");
}

export function fetchUnreadNotificationCount(): Promise<number> {
  return springRequest<{ count: number }>("/api/notifications/unread-count").then((res) => res.count);
}

export function markNotificationReadRequest(notificationId: string): Promise<Notification> {
  return springRequest(`/api/notifications/${notificationId}/read`, { method: "PATCH" });
}

export function markAllNotificationsReadRequest(): Promise<void> {
  return springRequest("/api/notifications/read-all", { method: "PATCH" });
}

// Notification preferences (Settings Phase S1)
//
// Goes straight to the Spring Boot backend (NotificationPreferenceController)
// — the person whose preferences are read/written is always the
// session-resolved actor, never a client-supplied id. Purely persisted
// toggle state; does not affect notification delivery in this phase.

export function fetchNotificationPreferences(): Promise<NotificationPreferences> {
  return springRequest("/api/notification-preferences");
}

export function updateNotificationPreferencesRequest(
  patch: Partial<NotificationPreferences>,
): Promise<NotificationPreferences> {
  return springRequest("/api/notification-preferences", {
    method: "PATCH",
    body: JSON.stringify(patch),
  });
}

// Admin — a fully separate login/session from the workspace-user auth above.
// Goes straight to the Spring Boot backend (AdminAuthController/AccountController),
// same as the workspace-user auth functions above — see BACKEND.md's Auth section.

export function adminLoginRequest(email: string, password: string): Promise<{ id: string; email: string }> {
  return springRequest("/api/admin/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
}

export function adminLogoutRequest(): Promise<void> {
  return springRequest("/api/admin/auth/logout", { method: "POST" });
}

export function fetchAdminMe(): Promise<{ id: string; email: string }> {
  return springRequest("/api/admin/auth/me");
}

/** POST /api/accounts — Admin-only employee account creation. Body: { name, email, password, accessRole }. */
export function createAccountRequest(input: {
  name: string;
  email: string;
  password: string;
  accessRole: string;
}): Promise<Person> {
  return springRequest("/api/accounts", { method: "POST", body: JSON.stringify(input) });
}

/** GET /api/accounts — Admin-only persisted employee-account roster. */
export function fetchAccountsRequest(): Promise<Person[]> {
  return springRequest("/api/accounts");
}
