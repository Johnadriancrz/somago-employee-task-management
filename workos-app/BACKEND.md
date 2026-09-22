# Backend handoff

The frontend is fully wired to a real HTTP API and session-based auth — every
read/write goes through `fetch()` calls to the Next.js route handlers under
[src/app/api](src/app/api), and every page except `/login` is gated behind a
signed-in session. Today all of it is backed by in-memory stubs under
[src/lib/server/](src/lib/server/), seeded from the mock data in
[src/lib/data.ts](src/lib/data.ts). They reset whenever the server restarts.

**To swap in a real backend:** replace the bodies of the functions in
`src/lib/server/*-repository.ts` and `session.ts` with real database/auth calls,
keeping their signatures and return shapes. No other file needs to change — the
route handlers, `src/lib/api-client.ts`, `src/proxy.ts`, and every component that
calls `useBoard()`/`useAuth()` are already written against this contract.

If the real backend isn't a Next.js route handler at all (a separate service),
point [src/lib/api-client.ts](src/lib/api-client.ts) at that service's base URL
instead — it's the only file that knows the API is HTTP.

## Data model

See [src/lib/types.ts](src/lib/types.ts) for the canonical types. Summary:

```ts
type Status = "not-started" | "working" | "stuck" | "done";
type WorkspaceId = string; // any id — workspaces are created at runtime

interface Workspace {
  id: WorkspaceId;
  name: string;
  initials: string;
  ownerId: string;     // the Person who created it — the only one who can manage members or delete it
  memberIds: string[]; // Person.id[] who can see/use this workspace — always includes ownerId
}

type BoardId = string; // any id — boards are created/deleted at runtime
type BoardIcon = "table" | "kanban" | "bug" | "milestone" | "generic";

interface BoardMeta {
  id: BoardId;
  workspaceId: WorkspaceId; // which workspace this board belongs to
  name: string;
  description: string;
  icon: BoardIcon;
}

interface Person {
  id: string;
  name: string;
  email: string;
  initials: string;
  role: string;
  chipClass: string; // Tailwind classes for the avatar chip
}

interface Subtask {
  id: string;
  title: string;
  done: boolean;
}

interface Attachment {
  id: string;
  name: string;
  size: number;             // bytes
  type: string;             // MIME type
  dataUrl: string;           // see "File attachments" note below
}

interface Task {
  id: string;
  title: string;
  group: "this-week" | "next-week" | "this-month" | "next-month";
  status: Status;
  ownerId: string;          // Person.id — who created/owns the task
  assigneeIds?: string[];   // Person.id[] — who it's assigned to; separate from ownerId, 0+ people
  tag?: string;
  priority: 1 | 2 | 3 | 4 | 5;
  dueDate: string;          // display label, e.g. "Sep 19"
  start: string;            // ISO date
  end: string;              // ISO date
  progress: number;         // 0-100 — auto-derived from subtasks once a task has any, see the frontend note below
  subtasks?: Subtask[];
  attachments?: Attachment[];
  blocker?: string;
  note?: string;             // shown in the UI as "Remarks"
  dependsOn?: string;       // another Task.id
}

type ConversationId = string; // "general", or "dm:<personIdA>:<personIdB>" (ids sorted)

interface ChatMessage {
  id: string;
  conversationId: ConversationId;
  authorId: string;  // Person.id — always the signed-in sender, never client-supplied
  text: string;
  createdAt: string; // ISO datetime
}

interface TimeEntry {
  id: string;
  personId: string;
  clockIn: string;         // ISO datetime
  clockOut: string | null; // ISO datetime, or null while still clocked in
}
```

## Endpoints

Request/response bodies are JSON. Every endpoint below except `POST
/api/auth/login`, `GET/POST /api/auth/*`, and `GET /api/people` requires the
`workos_session` cookie (see **Auth** below) — `src/proxy.ts` rejects
unauthenticated requests to everything else with 401/redirect before they reach
a route handler.

**Workspaces**

Membership is a real access boundary, not just a UI label: every endpoint
below (and every board/task endpoint further down) is scoped to workspaces
the signed-in person owns or is a member of. A non-member gets a workspace
left out of `GET /api/workspaces` entirely, and 403s if they try to touch its
boards/tasks/members directly by id.

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/workspaces` | — | `Workspace[]` — only workspaces the signed-in person owns or is a member of |
| POST | `/api/workspaces` | `{ name: string; initials?: string }` | created `Workspace` (201) — the signed-in person becomes `ownerId` and its sole initial member; `initials` auto-derived from `name` if omitted |
| DELETE | `/api/workspaces/:workspaceId` | — | `{ ok: true }` — owner only (403 otherwise); also deletes every board and task in it |
| POST | `/api/workspaces/:workspaceId/members` | `{ personId: string }` | updated `Workspace` — owner only (403 otherwise); `personId` must be an existing account (404 otherwise) |
| DELETE | `/api/workspaces/:workspaceId/members/:personId` | — | updated `Workspace` — owner only (403 otherwise); removing the owner itself is rejected (400) |

There's no rename (`PATCH`) yet — nothing in the UI needs it. Add one the
same way `board-repository.ts` does it if that's needed later. There's also
no "invite" flow: only accounts that already exist (created via `/admin`) can
be added to a workspace.

**Boards**

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/boards` | — | `BoardMeta[]` — every board in a workspace the signed-in person belongs to (the client filters further by `workspaceId`) |
| POST | `/api/boards` | `Omit<BoardMeta, "id">` (`workspaceId` required) | created `BoardMeta` (201), 404 if `workspaceId` doesn't exist, 403 if the signed-in person isn't a member of it |
| PATCH | `/api/boards/:boardId` | `Partial<Omit<BoardMeta, "id">>` | updated `BoardMeta`, 403 if the signed-in person isn't a member of its workspace |
| DELETE | `/api/boards/:boardId` | — | `{ ok: true }` — also deletes every task on that board; 403 if the signed-in person isn't a member of its workspace |

**Tasks**

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/tasks` | — | `Record<BoardId, Task[]>` — only tasks on boards in a workspace the signed-in person belongs to |
| POST | `/api/tasks` | `{ boardId: BoardId } & Omit<Task, "id">` | created `Task` (201), 403 if the signed-in person isn't a member of the board's workspace |
| PATCH | `/api/tasks/:taskId` | `Partial<Task>` (merge-patch) | updated `Task`, 403 if the signed-in person isn't a member of the task's workspace |
| DELETE | `/api/tasks/:taskId` | — | `{ ok: true }`, 403 if the signed-in person isn't a member of the task's workspace |

**People** (read-only — nothing creates/edits people yet)

| Method | Path | Returns |
|---|---|---|
| GET | `/api/people` | `Person[]` |

**Auth**

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/auth/login` | `{ email: string; password: string }` | signed-in `Person`, sets the session cookie. 401 with `{ error }` for an unknown email or wrong password |
| POST | `/api/auth/logout` | — | `{ ok: true }`, clears the session cookie |
| GET | `/api/auth/me` | — | signed-in `Person`, or 401 |

**Chat**

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/chat/messages?conversationId=...` | — | `ChatMessage[]`, oldest first |
| POST | `/api/chat/messages` | `{ conversationId, text }` | created `ChatMessage` (201) |
| GET | `/api/chat/stream?conversationId=...` | — | Server-Sent Events: one `ChatMessage` per event as it's posted |

`conversationId` is either `"general"` (the one built-in channel, open to
everyone) or a DM id shaped `dm:<personIdA>:<personIdB>` with the two person
ids sorted — see [src/lib/conversation-id.ts](src/lib/conversation-id.ts).
There's no "create a conversation" step: a DM's id is computed the same way by
both participants, and the first message to it just starts appearing. Reading
or posting to a DM you're not part of gets a 403.

**Time clock**

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/time/clock-in` | — | created `TimeEntry` (201), 409 if already clocked in |
| POST | `/api/time/clock-out` | — | closed `TimeEntry`, 409 if not clocked in |
| GET | `/api/time/status` | — | the signed-in user's open `TimeEntry`, or `null` |
| GET | `/api/time/entries?personId=...` | — | `TimeEntry[]`, workspace-wide unless `personId` filters to one person |

The signed-in user is always taken from the session for clock-in/out — never
from the request body. `GET /api/time/entries` is intentionally open to any
signed-in user, not just the requester's own entries: this app has no
role/permission system yet (see note below), so the HR/finance KPI view on
`/time-clock` just reads everyone's entries directly. A real deployment
should gate that endpoint (and the page) to an HR/finance role once one
exists.

**Reset** (used by the "Reset demo data" button)

| Method | Path | Returns |
|---|---|---|
| POST | `/api/reset` | `{ workspaces: Workspace[]; boards: BoardMeta[]; tasksByBoard: Record<BoardId, Task[]> }` — workspaces, boards, and tasks only; chat messages and time entries are untouched |

Errors return `{ error: string }` with a 4xx status (400 for a malformed body, 401
for no/invalid session, 403 for a DM you're not part of or a workspace/board/task
you're not a member of (or a members action only the owner can do), 404 for an
unknown workspace/board/task id, 409 for a clock-in/out conflict).

## Auth — what's real and what's a demo stand-in

The login page ([src/app/login/page.tsx](src/app/login/page.tsx)) is a real
email + password form, but every seed account shares one password
(`DEMO_PASSWORD` in
[src/lib/server/demo-credentials.ts](src/lib/server/demo-credentials.ts),
shown as a hint on the page) since the mock data has no real per-user
credentials. Everything *around* that check is real: `POST /api/auth/login`
looks the email up via `getPersonByEmail`
([src/lib/server/people-repository.ts](src/lib/server/people-repository.ts)),
compares the password to the shared constant, and on success issues a random
token stored server-side in
[src/lib/server/session.ts](src/lib/server/session.ts) (an in-memory `Map`) as
an httpOnly `workos_session` cookie. `src/proxy.ts` (Next's middleware) checks
that cookie is present before letting a request through, and each route
handler calls `requireSessionPersonId()`
([src/lib/server/require-session.ts](src/lib/server/require-session.ts)) to
resolve it back to a person id and reject if it's missing or stale.

A real backend swaps two things and keeps the rest:
1. `/api/auth/login`'s password check — compare against a real per-user
   password hash (or hand off to an OAuth/SSO provider) instead of the shared
   `DEMO_PASSWORD` constant, which goes away entirely.
2. `session.ts`'s `Map` — swap for real sessions (DB-backed) or JWTs. If you
   switch to a JWT that doesn't need server-side storage, `getSessionPersonId`
   just becomes "verify and decode the token" instead of a map lookup.

If the session cookie ever outlives the session it points to (e.g. this dev
server restarting, which wipes the in-memory `Map`), the client notices on
the next failed request and recovers on its own: `request()` in
[src/lib/api-client.ts](src/lib/api-client.ts) treats any 401 from a
non-auth endpoint as a dead session, clears the cookie, and sends the user
back to `/login`.

## Frontend behavior to know about

- [src/lib/store.tsx](src/lib/store.tsx) applies every task/board mutation to
  local state optimistically, then fires the matching request in the background
  and rolls the local change back if it rejects. A slower or flakier real
  backend will surface through that rollback path, not through new frontend
  code.
- The initial board/task/people lists still render from the seed constants in
  `data.ts` on first paint (so server and client render identically, no
  hydration flash), then an effect swaps in whatever the API returns. If that
  request is slow, users briefly see seed data before the real data appears.
- [src/lib/auth.tsx](src/lib/auth.tsx) (`AuthProvider`/`useAuth()`) is scoped to
  the root layout, above `BoardProvider`, so the login page can call `login()`
  without mounting board/task data it doesn't need. `BoardProvider` and
  [src/lib/clock.tsx](src/lib/clock.tsx) (`ClockProvider`/`useClock()`) wrap
  only the authenticated route group
  ([src/app/(app)/layout.tsx](<src/app/(app)/layout.tsx>)) — `ClockProvider`
  is a single shared clock-in/out state so the TopBar widget and the Time
  Clock page never disagree about whether you're currently clocked in.
- Chat's live updates use `EventSource` (SSE) against `/api/chat/stream`, fed
  by an in-process `EventEmitter`
  ([src/lib/server/chat-events.ts](src/lib/server/chat-events.ts)). That only
  fans out within one server process — running multiple instances behind a
  load balancer needs a shared pub/sub (Redis, etc.) instead, or the
  connection needs to be sticky to one instance. Some hosts/proxies also
  buffer or time out long-lived SSE connections; the route sends a keep-alive
  comment every 25s to help, but double-check your target host's support for
  streaming responses before relying on this in production.
- A task's `progress` is auto-derived from its `subtasks` once it has any
  (`Math.round(done / total * 100)`, computed client-side in
  [TaskDetailPanel.tsx](src/components/panels/TaskDetailPanel.tsx) and sent as
  part of the same `PATCH`) — it's only a free-form manual value while a task
  has zero subtasks. Don't be surprised if `progress` doesn't match a manual
  edit for a task that has subtasks; the UI won't let you set it independently
  in that case.
- File attachments are stored as base64 `data:` URIs directly on the task
  (capped at 5MB client-side in
  [FilesPicker.tsx](src/components/ui/FilesPicker.tsx)), not in real blob
  storage — a real backend should swap this for an actual upload flow (S3,
  etc.) and store a URL in `Attachment.dataUrl`'s place instead. Keeping the
  field named `dataUrl` was a deliberate nudge to future-you: this is not
  where you want real users' files to live long-term.
