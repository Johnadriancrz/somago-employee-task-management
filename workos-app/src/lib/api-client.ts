import type {
  BoardId,
  BoardMeta,
  ChatMessage,
  ConversationId,
  NewBoardInput,
  NewPersonInputWithPassword,
  NewTaskInput,
  NewWorkspaceInput,
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
const SPRING_API_BASE_URL = process.env.NEXT_PUBLIC_SPRING_API_BASE_URL ?? "http://localhost:8080";

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
  fetch("/api/auth/logout", { method: "POST" }).finally(() => {
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
  fetch("/api/admin/auth/logout", { method: "POST" }).finally(() => {
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
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (res.status === 401 && !path.startsWith("/api/auth/") && !path.startsWith("/api/admin/")) {
    handleStaleSession();
  }
  if (res.status === 401 && path.startsWith("/api/admin/") && !path.startsWith("/api/admin/auth/")) {
    handleStaleAdminSession();
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

// Combined reset

export function resetAllDataRequest(): Promise<{
  workspaces: Workspace[];
  boards: BoardMeta[];
  tasksByBoard: Record<BoardId, Task[]>;
}> {
  return request("/api/reset", { method: "POST" });
}

// Auth

export function loginRequest(email: string, password: string): Promise<Person> {
  return request("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
}

export function logoutRequest(): Promise<void> {
  return request("/api/auth/logout", { method: "POST" });
}

export function fetchMe(): Promise<Person> {
  return request("/api/auth/me");
}

export function changePasswordRequest(currentPassword: string, newPassword: string): Promise<void> {
  return request("/api/auth/change-password", {
    method: "POST",
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

// Chat

export function fetchMessages(conversationId: ConversationId): Promise<ChatMessage[]> {
  return request(`/api/chat/messages?conversationId=${encodeURIComponent(conversationId)}`);
}

export function sendMessageRequest(conversationId: ConversationId, text: string): Promise<ChatMessage> {
  return request("/api/chat/messages", {
    method: "POST",
    body: JSON.stringify({ conversationId, text }),
  });
}

/**
 * Opens a live SSE connection for a conversation. Returns a cleanup function
 * — call it (e.g. from a useEffect return) to close the connection.
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

export function fetchClockStatus(): Promise<TimeEntry | null> {
  return request("/api/time/status");
}

export function clockInRequest(): Promise<TimeEntry> {
  return request("/api/time/clock-in", { method: "POST" });
}

export function clockOutRequest(): Promise<TimeEntry> {
  return request("/api/time/clock-out", { method: "POST" });
}

export function fetchTimeEntries(personId?: string): Promise<TimeEntry[]> {
  return request(personId ? `/api/time/entries?personId=${encodeURIComponent(personId)}` : "/api/time/entries");
}

// Admin — a fully separate login/session from the workspace-user auth above.

export function adminLoginRequest(email: string, password: string): Promise<{ email: string }> {
  return request("/api/admin/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
}

export function adminLogoutRequest(): Promise<void> {
  return request("/api/admin/auth/logout", { method: "POST" });
}

export function fetchAdminMe(): Promise<{ email: string }> {
  return request("/api/admin/auth/me");
}

export function fetchAdminPeople(): Promise<Person[]> {
  return request("/api/admin/people");
}

export function createPersonRequest(input: NewPersonInputWithPassword): Promise<Person> {
  return request("/api/admin/people", { method: "POST", body: JSON.stringify(input) });
}

export function updatePersonRequest(
  personId: string,
  patch: Partial<NewPersonInputWithPassword>,
): Promise<Person> {
  return request(`/api/admin/people/${personId}`, { method: "PATCH", body: JSON.stringify(patch) });
}

export function deletePersonRequest(personId: string): Promise<void> {
  return request(`/api/admin/people/${personId}`, { method: "DELETE" });
}
