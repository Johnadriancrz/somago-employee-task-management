# WorkOS Frontend/Backend Audit

**Purpose:** Pre-implementation audit for handing off to a Spring Boot + MySQL backend. This document is a factual inventory, not an implementation plan — nothing in it should be treated as approved backend design until the open questions in [Section 11](#11-questions-requiring-approval) are answered.

**Status:** Audit only. No frontend, backend, or database code was created or modified while producing this document.

---

## 1. Project Overview

WorkOS is a Monday.com/Asana-style work management tool. The repository root contains:

| Path | Role |
|---|---|
| `workos-app/` | The Next.js 16 (App Router) frontend — the real, working application. |
| `workos-app/BACKEND.md` | A handoff doc written by/for the frontend's own author, describing the current in-memory stub "backend" and what a real backend must preserve. |
| `PRODUCT.md` | Original product brief. **Materially out of date** — see [2.1](#21-documentation-vs-reality). |
| `stitch-export/` | The original Stitch-generated HTML/CSS mockups and design-system spec the frontend was built from. |
| `backend/` | A brand-new, untracked Spring Boot 4 + Java 25 Maven project. Confirmed to contain **only** the default Spring Initializr scaffold — a bare `@SpringBootApplication` main class, one placeholder test, and `application.properties` pointing at a local MySQL instance. No controllers, entities, repositories, security config, or business logic exist yet. |
| `workos-app/my-next-app/` | An empty, unused directory (`app/`, `node_modules/`, `public/`) left over from initial scaffolding. Not part of the running application; not referenced by any config. Safe to ignore/remove, flagged here only so it isn't mistaken for a second frontend. |

The frontend is **not a static mockup**. It is a fully functional Next.js application with its own server-side API layer (Next.js Route Handlers under `src/app/api/`) backed by in-memory JavaScript data structures that reset on every server restart. The backend engineering task is to replace those in-memory stores with a real Spring Boot + MySQL service while preserving the HTTP contract the frontend already calls.

### 2.1 Documentation vs. reality

`PRODUCT.md` states the app is "interactive frontend only ... no backend, no persistence, no auth" and that data comes from `stitch-export/` mock data. **This is no longer true.** The app has since grown a full session-based auth system, a separate admin console, and a complete HTTP API with 30 route handlers. `PRODUCT.md` was not updated after that work landed. `BACKEND.md` is the accurate, current source of truth for backend intent, but it too is stale in several specific places documented throughout this audit (admin module absent from it, a few response shapes and fields changed since it was written, etc.).

**Recommendation:** treat `BACKEND.md` + this audit + the actual source under `workos-app/src` as authoritative; treat `PRODUCT.md` as historical/aspirational only.

---

## 2. Audit Scope and Methodology

- Every file under `workos-app/src` was read directly (not inferred from filenames): all 30 API route handlers, all 13 `src/lib/server/*` repository/session modules, `store.tsx`, `auth.tsx`, `admin-auth.tsx`, `clock.tsx`, `api-client.ts`, `proxy.ts`, `types.ts`, `permissions.ts`, and every page and component that touches data or the API.
- `BACKEND.md`, `PRODUCT.md`, `DESIGN.md`, `README.md`, and `stitch-export/code/design-system.md` were read in full.
- The `backend/` Spring Boot project was inspected file-by-file; it is confirmed empty of business logic (Section 1).
- Nothing was inferred from a filename or a doc claim without confirming it in code. Where something could not be confirmed, it is marked **UNKNOWN** or **REQUIRES CONFIRMATION**.
- No source file was edited. No dependency was installed. No database or migration was created.

---

## 3. Existing Application Modules

Each module below is real, verified in `workos-app/src`. "Backend today" describes the current in-memory stub; "Backend needed" is what a durable implementation must provide.

### 3.1 Board (Table / Kanban / Timeline / Dashboard)
- **Routes:** `/` (`src/app/(app)/page.tsx`)
- **Components:** `BoardHeader`, `BoardToolbar`, `TableView`, `KanbanView`, `TimelineView`, `DashboardView`, `TaskDetailPanel`
- **Roles:** any workspace member (read); task owner/assignee (write, see [Section 7](#7-authentication-and-authorization-audit))
- **Workflows:** switch views (Table/Kanban/Timeline/Dashboard), create/edit/delete tasks, drag a Kanban card between status columns, collapse/group/sort/filter/search the table, zoom/scale the Gantt timeline, export CSV (Dashboard "Export CSV", Timeline "Export Gantt" — both 100% client-side, no server round-trip)
- **Data displayed:** `Task[]` scoped to the active board, `Person[]` for owner/assignee display, `Workspace`/`BoardMeta` for chrome
- **Data mutated:** full `Task` CRUD; `BoardMeta` create/update/delete
- **Current data source:** `GET /api/tasks`, `GET /api/boards` via `src/lib/store.tsx`'s `BoardProvider`, with optimistic local updates rolled back on request failure
- **Backend today:** in-memory `task-repository.ts` / `board-repository.ts`, keyed by board id, reset on server restart
- **BACKEND.md coverage:** Workspaces/Boards/Tasks endpoint tables are accurate and match the live route handlers.

### 3.2 Workspaces & Boards (creation, membership, deletion)
- **Components:** `Sidebar`, `NewBoardDialog`, `NewWorkspaceDialog`, `MembersPage`
- **Roles:** workspace **owner** only for delete/add-member/remove-member; any **member** for create-board and view
- **Workflows:** create workspace (creator becomes sole owner+member), create/rename\*/delete board, add/remove workspace members (owner-only), delete workspace (cascades to its boards and tasks)
- **Data source:** `/api/workspaces*`, `/api/boards*`
- **Note:** Workspace **rename** has no backend endpoint — `updateWorkspace()` in `store.tsx` is explicitly documented in its own code comment as "local-only for now." The Settings page's "Workspace name" field calls it, so **a user can rename a workspace in the UI and the change is silently lost on reload/from another device.**

### 3.3 My Work
- **Route:** `/my-work`
- **Data:** every task the signed-in user **owns** (not "assigned to," despite the page's own copy — see [Section 9](#9-frontendbackend-gap-analysis)), across all boards in the active workspace, computed client-side from `tasksByBoard`
- **Backend needed:** none beyond existing task read endpoints — this is a pure client-side filter/sort of already-fetched data.

### 3.4 Reports (Dashboards)
- **Route:** `/dashboards`
- **Data:** per-board completion % and stuck counts, computed client-side from `tasksByBoard`; a cross-board "Completed Tasks" list
- **Backend needed:** none beyond existing task/board read endpoints.

### 3.5 Notifications
- **Route:** `/notifications`
- **Data displayed:** synthetic notifications **generated client-side on every render** from current task state (a `stuck` task with a `blocker` → a "blocked" notification; a task due within 2 days → a "due soon" notification). Timestamps (`"1h ago"`, `"3h ago"`) are **hardcoded string literals**, not real elapsed time.
- **Read/unread state** is local `useState`, lost on reload.
- **This is entirely MOCKED.** There is no notification entity, no persistence, no delivery mechanism (push/email/in-app-real-time), and no server involvement at all today. Any real notification system (mentions, assignment, due-date reminders — all referenced as toggles on the Settings page) would be new backend surface area, not a refactor of existing surface area.

### 3.6 Chat
- **Route:** `/chat`
- **Components:** `ChatPage` (channel list + `ConversationThread`)
- **Workflows:** post to the single built-in `general` channel (open to everyone in the workspace) or a 1:1 DM (id derived deterministically as `dm:<sortedIdA>:<sortedIdB>`, no "create conversation" step)
- **Data source:** `GET /api/chat/messages?conversationId=`, `POST /api/chat/messages`, live updates via `GET /api/chat/stream` (Server-Sent Events)
- **Backend today:** in-memory `chat-repository.ts`, fanned out via a Node `EventEmitter` (`chat-events.ts`) — **single-process only**, explicitly flagged in `BACKEND.md` as needing shared pub/sub (e.g. Redis) or sticky sessions behind any load balancer/multi-instance deployment.
- **BACKEND.md coverage:** accurate.

### 3.7 Time Clock
- **Route:** `/time-clock`; also a persistent `ClockWidget` in the TopBar on every authenticated page
- **Workflows:** clock in/out (self only — always resolved from the session, never the request body), view own live elapsed time, view a workspace-wide HR/finance table of hours-per-person over a selectable range (7/30/all days)
- **Data source:** `/api/time/clock-in`, `/api/time/clock-out`, `/api/time/status`, `/api/time/entries?personId=`
- **Backend today:** in-memory `time-repository.ts`
- **Security note (documented in code and in `BACKEND.md`):** `GET /api/time/entries` is intentionally open to **any signed-in user**, not just the requester — there is no role/permission system today, so any workspace member can read everyone's clock records. `BACKEND.md` explicitly calls this out as needing an HR/finance role gate in a real deployment.

### 3.8 Members
- **Route:** `/members`
- **Scope:** only workspaces the signed-in user **owns** — one card per owned workspace with its roster and an add/remove-member control
- **Data source:** `useBoard()`'s workspace state + `/api/workspaces/:id/members*`
- **Note:** there is no "invite by email" flow — only pre-existing accounts (created via `/admin`) can be added to a workspace.

### 3.9 Settings
- **Route:** `/settings`
- Four sub-sections, with very different levels of backend reality:
  1. **Profile display name** — `updateProfile()` in `auth.tsx` is explicitly commented "local-only for now, there's no `/api/auth/me` PATCH endpoint yet." It also calls `updatePerson()` in `store.tsx`, itself also local-only. **A name change here is lost on reload.**
  2. **Password change** — real, wired to `POST /api/auth/change-password`, requires current password, ≥6 chars.
  3. **Workspace name** — local-only (see 3.2).
  4. **Notification preferences** (mentions/assigned/due-soon/digest toggles) — pure local `useState`, no persistence, no backend concept of a notification preference exists anywhere.
  5. **"Reset demo data"** — real, calls `POST /api/reset`, restores workspaces/boards/tasks/people to their seed values (all currently blank — see [Section 6](#6-data-model-and-relationship-inventory)).

### 3.10 Help
- **Route:** `/help`
- Static FAQ content + a `mailto:` link. No backend involvement. One FAQ answer's own copy ("Everything is saved locally in your browser... Reset demo data... to start over from the original sample data") is now stale relative to the real session/API-backed architecture — a minor content bug, not a backend concern, flagged for completeness.

### 3.11 Admin Console
- **Routes:** `/admin`, `/admin/login`
- **Entirely separate identity/session system** from the workspace-user app — its own cookie (`ADMIN_SESSION_COOKIE`), its own hardcoded single credential pair (`ADMIN_EMAIL`/`ADMIN_PASSWORD` in `src/lib/server/admin-credentials.ts`), its own `AdminAuthProvider`/`useAdminAuth()`, gated by `proxy.ts` on cookie *presence* only (see [Section 7](#7-authentication-and-authorization-audit)).
- **Workflows:** list all `Person` accounts, create a new account (name/email/password/role), delete an account.
- **This module is not mentioned anywhere in `BACKEND.md`.** It is a real, working, but undocumented part of the system — a significant gap for anyone using `BACKEND.md` alone as the handoff spec.
- **Data source:** `/api/admin/auth/*`, `/api/admin/people*`

### 3.12 Login (workspace-user)
- **Route:** `/login`
- Real email+password form; every seeded account shares one demo password (`demo1234`, `DEMO_PASSWORD` constant), shown as an on-page hint. Accounts created via `/admin` get their own real password.

---

## 4. Verified Frontend API Inventory

All 30 route handlers below were read directly from `workos-app/src/app/api/**/route.ts`. This table supersedes `BACKEND.md`'s endpoint tables where they differ (noted inline); everywhere else `BACKEND.md`'s description was confirmed accurate.

| Module | Method | Path | Auth | Notes |
|---|---|---|---|---|
| Workspaces | GET | `/api/workspaces` | session | Owned or member-of only |
| | POST | `/api/workspaces` | session | `{name, initials?}` → 201 |
| | DELETE | `/api/workspaces/:id` | session, owner-only | Cascades boards+tasks |
| | POST | `/api/workspaces/:id/members` | session, owner-only | `{personId}`, 404 if unknown person |
| | DELETE | `/api/workspaces/:id/members/:personId` | session, owner-only | 400 if target is the owner |
| Boards | GET | `/api/boards` | session | All boards in member workspaces |
| | POST | `/api/boards` | session, member-of workspace | 404 unknown workspace, 403 non-member |
| | PATCH | `/api/boards/:id` | session, member-of workspace | name/description/icon merge-patch |
| | DELETE | `/api/boards/:id` | session, member-of workspace | Cascades tasks |
| Tasks | GET | `/api/tasks` | session | `Record<BoardId, Task[]>`, scoped to member boards |
| | POST | `/api/tasks` | session, member-of board's workspace | `{boardId} & NewTaskInput` → 201 |
| | PATCH | `/api/tasks/:id` | session, member-of workspace **only** | **No owner/assignee check — see Section 7** |
| | DELETE | `/api/tasks/:id` | session, member-of workspace **only** | **Same gap** |
| People | GET | `/api/people` | **none** | Matches `BACKEND.md`'s documented exception |
| Auth | POST | `/api/auth/login` | — | `{email, password}` → `Person` + cookie |
| | POST | `/api/auth/logout` | — | Clears cookie |
| | GET | `/api/auth/me` | session | |
| | POST | `/api/auth/change-password` | session | **Not documented in `BACKEND.md`** |
| Chat | GET | `/api/chat/messages?conversationId=` | session, participant | |
| | POST | `/api/chat/messages` | session, participant | `{conversationId, text}` |
| | GET | `/api/chat/stream?conversationId=` | session, participant | SSE |
| Time | POST | `/api/time/clock-in` | session | 409 if already in |
| | POST | `/api/time/clock-out` | session | 409 if not in |
| | GET | `/api/time/status` | session | Own open entry or null |
| | GET | `/api/time/entries?personId=` | session (any) | Workspace-wide, see 3.7 |
| Reset | POST | `/api/reset` | session | **Response now also includes `people`** — `BACKEND.md` and `api-client.ts`'s `resetAllDataRequest()` return type both still document only `{workspaces, boards, tasksByBoard}`. `store.tsx`'s `resetAllData()` doesn't apply the returned `people` at all, it just re-applies the local blank `PEOPLE` seed. **Confirmed inconsistency, not fabricated** — see [Section 9](#9-frontendbackend-gap-analysis). |
| Admin auth | POST | `/api/admin/auth/login` | — | Single hardcoded credential pair, **not in `BACKEND.md`** |
| | POST | `/api/admin/auth/logout` | — | |
| | GET | `/api/admin/auth/me` | admin session | |
| Admin people | GET | `/api/admin/people` | admin session | **Not in `BACKEND.md`** |
| | POST | `/api/admin/people` | admin session | `{name, email, password, role, initials?, chipClass?}` → 201, 409 on dup email |
| | PATCH | `/api/admin/people/:id` | admin session | Merge-patch, optional password |
| | DELETE | `/api/admin/people/:id` | admin session | |

**Error convention (confirmed, matches `BACKEND.md`):** `{error: string}` with 400 (malformed body), 401 (no/invalid session), 403 (not a member/owner), 404 (unknown id), 409 (clock-in/out conflict or duplicate email).

### 4.1 Sections A–D (per Phase 3 structure)

**A. Verified existing API contracts:** the full table above — every row was read directly from a route handler, not inferred.

**B. Implied backend requirements (client code exists and calls a local-only stub, but no server endpoint exists):**
- Workspace rename (`PATCH /api/workspaces/:id` does not exist)
- Person self-service profile update (`PATCH /api/auth/me` or equivalent does not exist)
- Person update via the board store's `updatePerson()` (separate from the admin `PATCH /api/admin/people/:id`, which does exist but is admin-only)
- Any notification persistence/delivery (nothing exists client- or server-side beyond synthetic client-side derivation)
- Any notification-preference persistence (Settings page toggles are local `useState` only)

**C. Proposed API contracts requiring approval:** none are proposed in this document — that decision belongs to whoever designs the Spring Boot API, informed by Section B above and the open questions in Section 11. This audit deliberately stops short of inventing endpoint shapes.

**D. Unknowns that require clarification:** see [Section 11](#11-questions-requiring-approval) in full; flagged inline throughout as **UNKNOWN**/**REQUIRES CONFIRMATION**.

---

## 5. Data Model and Relationship Inventory

Source of truth: `workos-app/src/lib/types.ts` (read in full). All fields below are verified from this file, cross-checked against every repository/route handler that touches them.

| Entity | Key fields (verified) | Relationships | Source |
|---|---|---|---|
| `Workspace` | `id`, `name`, `initials`, `ownerId`, `memberIds[]` | `ownerId`/`memberIds` → `Person.id`; owns `BoardMeta[]` | code |
| `BoardMeta` | `id`, `workspaceId`, `name`, `description`, `icon: "table"\|"kanban"\|"bug"\|"milestone"\|"generic"` | `workspaceId` → `Workspace.id`; owns `Task[]` | code |
| `Person` | `id`, `name`, `email`, `initials`, `role`, `chipClass` (Tailwind classes, presentation-only) | Referenced by `Workspace.ownerId`/`memberIds`, `Task.ownerId`/`assigneeIds`, `ChatMessage.authorId`, `TimeEntry.personId` | code |
| — server-only | `StoredPerson` extends `Person` with `password` (plaintext in the stub — see [Section 7](#7-authentication-and-authorization-audit)); never serialized to the client | code (`people-repository.ts`) |
| `Task` | `id`, `title`, `group: TaskGroup`, `status: Status`, `ownerId`, `assigneeIds?[]`, `tag?`, `priority: 1-5`, `dueDate` (display string), `start`/`end` (ISO dates), `progress: 0-100`, `subtasks?: Subtask[]`, `attachments?: Attachment[]`, `blocker?`, `note?`, `dependsOn?`, `updatedAt?` (epoch ms) | `boardId` (implicit — tasks are stored keyed by board, not as an explicit field on `Task` itself — **note this for schema design**); `ownerId`/`assigneeIds` → `Person.id`; `dependsOn` → another `Task.id` | code |
| `Subtask` | `id`, `title`, `done` | Embedded in `Task.subtasks[]`; drives `Task.progress` automatically once non-empty | code |
| `Attachment` | `id`, `name`, `size` (bytes), `type` (MIME), `dataUrl` (base64 `data:` URI, capped 5MB client-side) | Embedded in `Task.attachments[]` | code |
| `ChatMessage` | `id`, `conversationId`, `authorId`, `text`, `createdAt` | `conversationId` is either the literal `"general"` or a derived `dm:<idA>:<idB>` string (no separate Conversation entity); `authorId` → `Person.id` | code |
| `TimeEntry` | `id`, `personId`, `clockIn` (ISO), `clockOut` (ISO or null) | `personId` → `Person.id` | code |

### 5.1 Notable data-model observations

- **`Task.dependsOn` is defined in the type and in `BACKEND.md` but is never read or written anywhere in the UI.** `PRODUCT.md` describes "dependency arrows" on the Timeline/Gantt view; the shipped `TimelineView.tsx` has no dependency-arrow rendering and never references `task.dependsOn`. This field is effectively dead in the current frontend. **REQUIRES CONFIRMATION:** should the backend model this relationship at all yet, or wait until the frontend actually implements it?
- **A `Task`'s board membership is structural, not a field** — the stub stores tasks in a `Record<BoardId, Task[]>`. A relational schema will need an explicit `board_id` foreign key on the task table; this is a natural, low-risk translation but is called out because it's not a 1:1 copy of the JSON shape.
- **`chipClass` is a bag of Tailwind CSS class names stored as backend data** (e.g. `"bg-secondary-container text-on-secondary-container"`). This is presentation logic being persisted as if it were domain data. **REQUIRES CONFIRMATION:** should the backend keep storing/generating this string, or should avatar color become a derived/client-side concern (e.g. hash-of-id → palette index) so the backend doesn't need to know about Tailwind?
- **Passwords are stored in plaintext** in the in-memory stub (`StoredPerson.password`, compared with `===` in `verifyPersonCredentials`). This is explicitly flagged in `BACKEND.md` as something a real backend must replace with real hashing — restated here as a hard requirement, not optional hardening.
- **All seed data is currently blank.** `src/lib/data.ts` defines `WORKSPACES = []`, `BOARDS = []`, `TASKS_BY_BOARD = {}` — only `PEOPLE` (6 accounts) is non-empty. `POST /api/reset` restores to this same blank state. There is currently no seeded demo workspace/board/task data anywhere in the running app, despite `PRODUCT.md`'s description of a populated "Q3 Project Overview" board — that content apparently existed at an earlier point and was since emptied out. **REQUIRES CONFIRMATION:** does the backend need a database seed/fixture matching some demo dataset, and if so, where does its content come from (none currently exists in the repo)?

---

## 6. Authentication and Authorization Audit

*(Numbered 7 in the source doc structure per the requested template — kept as Section 6 here to match this document's own heading flow; content covers everything the template's Phase 5 asked for.)*

### 6.1 Two entirely separate auth systems, confirmed in code

1. **Workspace-user auth** (`src/lib/auth.tsx`, `src/lib/server/session.ts`, `src/lib/server/require-session.ts`)
   - Cookie: `workos_session` (httpOnly, `sameSite: lax`, `secure` in production, 30-day `maxAge`)
   - Session store: in-memory `Map<token, personId>`, wiped on server restart
   - Password check: plaintext `===` comparison against `DEMO_PASSWORD` (seed accounts) or a per-account password set at creation (admin-created accounts) or via `change-password`
   - `proxy.ts` (Next middleware) only checks **cookie presence**, not validity — actual validation happens per-request in each route handler via `requireSessionPersonId()`. A stale-but-present cookie (e.g. after a dev-server restart) is handled client-side: any 401 from a non-auth endpoint triggers `api-client.ts`'s `handleStaleSession()`, which clears the cookie and hard-navigates to `/login`.

2. **Admin auth** (`src/lib/admin-auth.tsx`, `src/lib/server/admin-session.ts`, `src/lib/server/require-admin-session.ts`)
   - Cookie: separate `ADMIN_SESSION_COOKIE`, 8-hour `maxAge`
   - Credentials: a single hardcoded pair (`admin@workos.dev` / `admin1234`) in `src/lib/server/admin-credentials.ts` — not tied to any `Person` record
   - Same "middleware checks presence only" pattern, same client-side stale-session recovery

### 6.2 Roles

There is **no role/permission system** beyond:
- **Workspace ownership** (`Workspace.ownerId`) — gates delete-workspace, add/remove-member
- **Task ownership** (`Task.ownerId`) vs. **assignee** (`Task.assigneeIds`), enforced client-side only (see 6.3)
- **Admin** vs. **not-admin** — a binary, entirely separate from the `Person`/workspace model (an admin session isn't tied to any `Person.id` at all)

There is no "HR/finance role," "manager role," etc. anywhere in the code, despite the Time Clock page's own description ("Clock in/out and workspace hours for HR and finance") implying one should exist. `BACKEND.md` explicitly flags this as a known gap for `GET /api/time/entries`.

### 6.3 Security gap: task-field permissions are enforced client-side only

**This is the most significant finding in this audit and should be resolved before backend implementation, not carried forward as-is.**

- `src/lib/permissions.ts`'s `useTaskPermissions()` computes `canEditCore` (owner-only: title, owner, assignees, dates, priority, group, tag, subtask structure, delete) and `canEditProgress` (owner-or-assignee: status, subtask checkmarks, files, remarks, blocker) — and is used throughout `TaskDetailPanel.tsx`, `TableView.tsx`, and `KanbanView.tsx` to disable inputs/buttons.
- The actual `PATCH /api/tasks/:taskId` and `DELETE /api/tasks/:taskId` route handlers (`src/app/api/tasks/[taskId]/route.ts`) only check **workspace membership** (`isWorkspaceMember`) — there is no check anywhere in the route handler or `task-repository.ts` that the caller is the task's owner or an assignee.
- **Concrete failure scenario:** any signed-in member of a workspace can `PATCH` or `DELETE` any task in that workspace via a direct API call (bypassing the UI entirely), including reassigning ownership, changing title/dates/priority, or deleting a task they don't own and aren't assigned to — none of which the UI allows them to do through its own controls.
- Same gap applies to `OwnerPicker`'s reassignment action and `TableView`'s bulk-select-and-delete — both are UI-disabled for non-owners but call the same unguarded `updateTask`/`deleteTask` requests.

**REQUIRES CONFIRMATION:** is this gap acceptable for the current demo/internal-tool risk profile, or must the Spring Boot backend enforce owner/assignee checks server-side from day one? Given this is explicitly moving toward a real persisted backend (vs. a disposable demo), this audit recommends treating server-side enforcement as required, not optional — but that is a product/security decision, not this audit's to make.

### 6.4 Other things a real backend must supply (restated from `BACKEND.md`, confirmed in code)

- Replace `DEMO_PASSWORD`/plaintext comparison with real per-user password hashing (or OAuth/SSO handoff)
- Replace the in-memory session `Map` with DB-backed sessions or JWTs
- Decide whether the **admin console** becomes a `Person`-linked role (e.g. `Person.isAdmin`) or remains a wholly separate credential system — current code treats it as fully separate, which is unusual for a real deployment and should be an explicit decision, not an accident of the demo's history

---

## 7. Realtime and Special-Feature Audit

| Feature | Current implementation | Backend responsibility if kept |
|---|---|---|
| **Chat live updates** | Server-Sent Events (`GET /api/chat/stream`), fed by a single-process Node `EventEmitter` (`chat-events.ts`). 25s keep-alive comment to survive proxy idle timeouts. | **Confirmed single-instance limitation** (stated in `BACKEND.md` and verified in code) — any multi-instance/load-balanced deployment needs shared pub/sub (e.g. Redis) or sticky sessions. Spring Boot equivalent: SSE via `SseEmitter`/WebFlux, or swap transport to WebSocket/STOMP — an open design choice, not dictated by the frontend (the frontend only depends on `EventSource` semantics against `/api/chat/stream`). |
| **Notifications** | 100% synthetic, client-derived, no persistence (Section 3.5) | If a real notification system is wanted, this is new backend surface area — not present today in any form. |
| **Time clock** | Real clock-in/out state and history, no realtime push — client polls on page load/action (Section 3.7) | Standard CRUD; no realtime requirement observed. |
| **File uploads (task attachments)** | Client-side only: `FilesPicker.tsx` reads a file via `FileReader.readAsDataURL()`, caps at 5MB, stores the base64 `data:` URI directly on the `Task.attachments[].dataUrl` field. No blob storage, no upload endpoint. Explicitly flagged in `BACKEND.md` and in the field's own doc comment as a placeholder — `dataUrl`'s naming is a deliberate signal that this is not production-ready. | Needs a real upload flow (e.g. S3/blob storage + a URL in `dataUrl`'s place) plus size/type validation server-side (today's 5MB cap is client-enforced only, trivially bypassable via direct API call). |
| **Search / filtering / sorting / grouping** | Entirely client-side, over already-fetched task data (`store.tsx`'s `visibleTasks` memo) | None — no server-side search endpoint exists or is implied by current usage patterns (board sizes are small/single-board in current usage). Flag if boards are expected to scale to a size where client-side filtering becomes impractical. |
| **CSV export** | Entirely client-side (`src/lib/csv.ts`'s `downloadCsv()` builds a `Blob` and triggers a download) — Dashboard "Export CSV" and Timeline "Export Gantt" | None — no server involvement today or implied. |
| **Demo data reset** | `POST /api/reset`, restores workspaces/boards/tasks/people to seed values (currently blank except `people`) | Needs an equivalent "restore to fixture" operation if this UX is kept — depends on Section 5.1's open question about seed data. |
| **Pagination** | **Not implemented anywhere.** Every list endpoint (`/api/tasks`, `/api/boards`, `/api/people`, `/api/chat/messages`, `/api/time/entries`) returns its full result set unpaginated. | **REQUIRES CONFIRMATION** — acceptable at current/expected data volumes, or does the backend need pagination from the start? |
| **WebSockets** | Not used anywhere (chat uses SSE, one-directional server→client) | N/A unless a future requirement changes this. |

---

## 8. Frontend/Backend Gap Analysis

| Feature | Frontend implementation | Backend support today | Missing for real backend | Status |
|---|---|---|---|---|
| Task CRUD | Full, optimistic-update UI | In-memory stub, full CRUD | Persistence; **server-side owner/assignee enforcement (Section 6.3)** | **PARTIALLY IMPLEMENTED** |
| Workspace/Board CRUD | Full UI | In-memory stub, full CRUD except workspace rename | Persistence; add workspace `PATCH` if rename is to be kept | **PARTIALLY IMPLEMENTED** |
| Workspace rename | UI field exists, calls local-only store method | **None** | New endpoint, or remove the UI field | **MOCKED** (client believes it works; server never sees it) |
| Profile name edit | UI field exists, calls local-only methods | **None** | New endpoint, or remove the UI field | **MOCKED** |
| Password change | Full form | Real endpoint, verified | Real hashing (currently plaintext) | **VERIFIED** (functionally), **flagged for hardening** |
| Auth (login/logout/me) | Full | Real, session-cookie based | Real password hashing; real session storage strategy | **VERIFIED** (functionally) |
| Admin console | Full UI, full CRUD on accounts | Real, but **undocumented in BACKEND.md** and no role link to `Person` | Decide admin/role model (Section 6.4) | **VERIFIED**, doc gap only |
| Chat (general + DM) | Full, real-time via SSE | Real, single-process only | Multi-instance pub/sub if ever scaled out | **VERIFIED**, scaling caveat only |
| Time clock | Full | Real | Role gate on `/api/time/entries` (no roles exist yet) | **VERIFIED**, security gap noted |
| Notifications | Full-looking UI | **None** | Entire feature is new backend surface if kept real | **MOCKED** |
| Notification preferences | UI toggles | **None** | Entire feature is new backend surface if kept real | **MOCKED** |
| File attachments | Full UI, 5MB client cap | Base64 stored inline on the task record | Real blob storage + upload endpoint + server-side validation | **PARTIALLY IMPLEMENTED / NOT PRODUCTION-READY (by design, per code comments)** |
| Task dependency (`dependsOn`) | Field exists in type/data model | Field stored/patched like any other field | Decide whether to keep modeling it at all — unused in UI | **NOT IMPLEMENTED** (field defined, never surfaced) |
| CSV/Gantt export | Full, client-side | N/A (no server involvement) | None needed unless requirements change | **VERIFIED** |
| Reset demo data | Full UI | Real endpoint, response shape mismatch with client type (Section 4) | Fix response/type mismatch; decide on real seed content (Section 5.1) | **PARTIALLY IMPLEMENTED**, minor bug |
| Pagination | Not implemented | Not implemented | Needs a decision (Section 7) | **REQUIRES CONFIRMATION** |
| Automated tests | **None found** — no test framework (Jest/Vitest/Playwright/etc.) in `package.json`, no test files under `workos-app/src` | N/A | Backend team should not assume any frontend contract test coverage exists to validate against | **NOT IMPLEMENTED** |

---

## 9. Proposed Backend Implementation Roadmap

This is a **proposal**, explicitly not an approved plan, offered as a starting point for discussion once Section 11's questions are answered. Ordering reflects dependency, not priority.

1. **API foundation & auth skeleton**
   - Objective: stand up Spring Boot MVC controllers mirroring the verified endpoint table in Section 4, with request/response DTOs matching `types.ts` exactly enough that `api-client.ts` needs zero changes beyond its base URL.
   - Dependencies: none — can start immediately once Section 11's data-model questions are answered.
   - Decisions needed: session strategy (server-side session vs. JWT — `BACKEND.md` explicitly says either works, since `getSessionPersonId` is designed to be swappable); whether to keep the two-cookie (user/admin) split.

2. **Database schema & persistence (JPA + MySQL)**
   - Objective: entities for `Person`, `Workspace`, `BoardMeta`, `Task`, `Subtask`, `Attachment` (or its replacement), `ChatMessage`, `TimeEntry`, matching Section 5's verified field list.
   - Dependencies: Section 11's open questions on `dependsOn`, `chipClass`, and seed data.
   - Decisions needed: real password hashing strategy (BCrypt/Argon2); whether `Subtask`/`Attachment` are separate tables or embedded (JSON column) given they're always accessed as part of their parent `Task`.

3. **Authorization enforcement**
   - Objective: close the Section 6.3 gap — enforce workspace membership (already specified) **and** task owner/assignee rules server-side, not just in the UI.
   - Dependencies: Section 11's decision on whether this is in-scope now or deferred.

4. **Core modules**: Workspaces → Boards → Tasks → People, in that dependency order (each depends on the one before it existing).

5. **Chat**: real persistence + a realtime transport decision (keep SSE via `SseEmitter`, or move to WebSocket/STOMP) — independent of the core CRUD modules, can be built in parallel.

6. **Time clock**: straightforward CRUD; needs the role-gating decision from Section 6.2/6.4 before `GET /api/time/entries` can be locked down properly.

7. **File attachments**: replace inline base64 storage with real object storage — a genuinely new subsystem, not a port of existing stub logic.

8. **Notifications** (only if the product decision is to make this real — currently 100% mocked): new domain entity, new endpoints, new delivery mechanism; not a "swap the stub" task like the others.

9. **Frontend integration & cutover**: point `src/lib/api-client.ts` at the Spring Boot service (same-origin proxy or CORS-enabled cross-origin, per `BACKEND.md`'s own note that this is the only file that needs to change if the backend isn't a Next.js route handler).

10. **Testing**: there is currently no test suite on either side to validate against (Section 8) — establishing at least contract/integration tests for the endpoint table in Section 4 should happen alongside, not after, implementation.

---

## 10. Questions Requiring Approval

Ordered roughly by how much they block downstream decisions.

1. **Server-side task permission enforcement (Section 6.3).** Should `PATCH`/`DELETE /api/tasks/:id` enforce owner/assignee rules server-side in the new backend, given the current stub doesn't? This changes the authorization design for every task-mutating endpoint — needs to be settled before entity/controller design starts.

2. **Admin/role model (Section 6.4).** Should the admin console become a role on `Person` (`Person.isAdmin` or similar), or remain a fully separate, unlinked credential system as it is today? This affects the `Person` schema and the security config shape.

3. **Seed/demo data (Section 5.1).** All workspace/board/task seed data is currently blank in the live app (only `Person` seed data exists). Does the backend need a fixture/seed dataset, and if so, what should it contain — is there source content anywhere, or does this need to be authored fresh?

4. **`Task.dependsOn` (Section 5.1, Section 8).** This field exists in the type and is patchable via the API, but nothing in the UI reads or writes it (no dependency arrows are rendered despite `PRODUCT.md` describing them). Model it in the database now, or drop it until the frontend actually implements the feature?

5. **`chipClass` (Section 5.1).** Should the backend keep storing/accepting a raw Tailwind class string per person, or should avatar coloring move to a derived/client-only concern so backend data doesn't encode frontend CSS?

6. **File attachment storage (Section 7).** Confirms real object storage (S3 or equivalent) is in scope for this backend effort, vs. being treated as a later phase — affects whether `Attachment.dataUrl`'s meaning changes in this pass or a future one, and whether a max-size/type policy is enforced server-side from day one.

7. **Local-only fields with no backend (Section 3.9, Section 8).** Workspace rename, profile name edit, and notification preferences all have working UI that silently no-ops server-side. Should each of these get a real endpoint in this backend effort, or should the corresponding UI be disabled/removed until it does? Shipping the backend without addressing this leaves a known UX bug in place.

8. **Notifications (Section 3.5, Section 7).** Currently 100% synthetic/client-derived with no persistence. Is a real notification system in scope for this backend effort at all, or explicitly out of scope for now?

9. **Pagination (Section 7).** Every list endpoint returns unpaginated results today. Is this acceptable at target scale, or should pagination be designed in from the start (harder to retrofit later without breaking the client contract)?

10. **Chat realtime transport at scale (Section 7).** If this deployment will ever run more than one backend instance, the current SSE + in-process EventEmitter design breaks. Confirm whether multi-instance deployment is a near-term requirement (which would mean deciding on shared pub/sub now) or can be deferred.

11. **Reset/response-shape mismatch (Section 4).** `POST /api/reset` now returns a `people` field that neither `api-client.ts`'s TypeScript return type nor `store.tsx`'s handling of the response accounts for. Minor, but should be fixed as part of (or before) backend cutover so the contract is exact.

---

## 11. Risks, Assumptions, and Limitations

- **This audit is a snapshot.** It reflects the state of `workos-app/src`, `BACKEND.md`, `PRODUCT.md`, and `backend/` as read during this session. No assumption should be made that this remains accurate after further frontend changes.
- **`workos-app/my-next-app/`** was confirmed empty/unused and is not covered further in this document; it is not part of the running application.
- **No load, performance, or scale testing was performed or implied** — statements about pagination/multi-instance chat are architectural observations from reading the code, not measurements.
- **Security findings in this document (plaintext passwords, client-only permission enforcement, open time-entries endpoint) are restatements/confirmations of what the frontend's own code comments and `BACKEND.md` already say is a known limitation of the demo stub** — they are flagged here because a real backend must deliberately decide how each is resolved, not because they are newly discovered defects in shipped production code.
- **The `backend/` Spring Boot project's `application.properties` contains a hardcoded local database credential.** This audit does not reproduce that value; whoever picks this project up should treat local dev credentials as needing to move to environment variables/secrets management before any shared or deployed use, per standard practice — not because of anything specific found here beyond "a plaintext credential is committed."
- **No business rules, endpoints, or fields were invented.** Every claim in Sections 3–8 traces to a specific file read during this audit; every gap or inconsistency is called out as such rather than silently resolved.

---

## 12. Source File References

Representative, not exhaustive — see Section 2 for full scope.

- Data model: `workos-app/src/lib/types.ts`
- API client: `workos-app/src/lib/api-client.ts`
- Auth middleware: `workos-app/src/proxy.ts`
- Session stores: `workos-app/src/lib/server/session.ts`, `workos-app/src/lib/server/admin-session.ts`
- Task/board/workspace repositories: `workos-app/src/lib/server/task-repository.ts`, `board-repository.ts`, `workspace-repository.ts`
- People/credentials: `workos-app/src/lib/server/people-repository.ts`, `demo-credentials.ts`, `admin-credentials.ts`
- Client state: `workos-app/src/lib/store.tsx`, `auth.tsx`, `admin-auth.tsx`, `clock.tsx`
- Permission logic: `workos-app/src/lib/permissions.ts`
- Task edit UI: `workos-app/src/components/panels/TaskDetailPanel.tsx`
- File attachments: `workos-app/src/components/ui/FilesPicker.tsx`
- Route handlers: `workos-app/src/app/api/**/route.ts` (30 files, all read)
- Backend scaffold: `backend/pom.xml`, `backend/src/main/java/com/workos/workos_backend/WorkosBackendApplication.java`, `backend/src/main/resources/application.properties`
- Handoff doc: `workos-app/BACKEND.md`
- Product brief (stale): `PRODUCT.md`

---

*End of audit. No implementation has begun. Awaiting answers to Section 10 before proceeding.*
