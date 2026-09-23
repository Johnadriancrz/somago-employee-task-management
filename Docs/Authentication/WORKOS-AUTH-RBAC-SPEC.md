# WorkOS Authentication, Account Management & RBAC — Specification

**Status:** Documentation only. No application code, database schema, or Git history was
modified while producing this document. This is the implementation reference for the
upcoming authentication/RBAC work — it does not itself implement anything.

**Sources inspected to produce this document:** `workos-app/BACKEND.md`,
`Docs/Backend-Setup/workos-frontend-backend-audit.md` (a prior audit — treated as
background, re-verified against current code, not copied blindly since the backend has
changed materially since it was written), `PRODUCT.md`, and the live source under
`workos-app/src/**` and `backend/src/main/**` (controllers, services, entities, DTOs,
Flyway migrations, `pom.xml`, `application*.properties`), read directly, not inferred.

---

## 1. Purpose and Scope

This document specifies the target design for:

1. A single, backend-owned authentication system for WorkOS, replacing the two
   disconnected demo auth systems that exist today.
2. Eight fixed **access roles**, replacing the current no-role-system state.
3. A CEO-only account management flow with an exact 4-field account creation form.
4. A complete role-permission matrix for Time Clock, Reports, Work Assignment,
   Members/Workspace Membership, and Chat.
5. A migration plan for the existing demo `Person` accounts and the existing separate
   admin console.
6. Uniform Chat access for all 8 access roles, and backend sender-identity
   verification for Chat derived from the authenticated session (§4.1, §7.5).

**Out of scope for this document:** writing the code, running migrations, seeding
data, or changing any running behavior. Every recommendation below is a proposal for a
future implementation phase, not a description of something that already exists,
unless explicitly marked **CONFIRMED (current behavior)**.

**Notation used throughout:**
- **CONFIRMED** — verified directly in the current codebase.
- **PROPOSED** — a recommendation for the implementation phases, not yet built or approved.
- **REQUIRES CLARIFICATION** — a decision only the product owner can make; listed again in full in §20.

---

## 2. Current Authentication Architecture (as found)

WorkOS today runs on **two separate backend surfaces**, and neither has a real,
production-grade auth system. This is the single most important fact this document
establishes, because it changes the shape of every phase below.

### 2.1 Two backend surfaces exist side by side — CONFIRMED

| Surface | What it owns today | Persistence |
|---|---|---|
| **Next.js route handlers** (`workos-app/src/app/api/**`) | Auth (login/logout/me/change-password), the separate Admin console, Chat, Reset-demo-data | In-memory JS arrays/maps (`src/lib/server/*-repository.ts`, `session.ts`), wiped on every server restart |
| **Spring Boot + MySQL** (`backend/`) | People (read), Workspaces, Boards, Tasks, Time Entries | Real MySQL tables via JPA, schema owned by Flyway (`backend/src/main/resources/db/migration/V1–V4`) |

`workos-app/src/lib/api-client.ts` already routes People/Workspaces/Boards/Tasks/Time-clock
calls (`springRequest()`) to the Spring service at `NEXT_PUBLIC_SPRING_API_BASE_URL`
(default `http://localhost:8080`), while Auth, Chat, Admin, and Reset still go through
relative Next.js routes (`request()`). This split is **mid-migration, not finished** —
the working tree at the time of writing has an uncommitted change moving the Time Clock
page/`api-client.ts` calls from the Next.js stub to Spring, confirming this cutover is
actively in progress.

### 2.2 The Spring Boot backend has **no authentication at all** — CONFIRMED

This is the most consequential finding in this document.

- `backend/pom.xml` has no `spring-boot-starter-security` and no password-hashing
  dependency of any kind.
- `Person` (`backend/.../entity/Person.java`) has **no password field**. The Flyway
  schema for `people` (`V1__create_person_workspace_board_tables.sql`) has no password
  column either.
- Every controller that needs to know "who is making this request"
  (`WorkspaceController`, `TaskController`, `TimeEntryController`) delegates to
  `ActingPersonResolver.currentPersonId()` — an interface with **exactly one
  implementation**, `LocalDevActingPersonResolver`, which:
  - is registered **only** under the Spring `local-dev` profile,
  - returns a single, fixed `Person.id` read from a config property
    (`app.local-dev.actor-person-id`, default `sarah-chen`), **the same id for every
    request, regardless of who is actually using the app**,
  - is explicitly documented in its own Javadoc as never allowed to read the actor
    from a header/param/body, and never allowed to run outside `local-dev`.
- `application.properties` says outright: *"there is deliberately no production-safe
  fallback yet. This must be replaced by real session/auth-derived identity before any
  non-local deployment."*

**Consequence:** every authorization check that already exists server-side in Spring
(task owner/assignee checks in `TaskService`, workspace-owner checks in
`WorkspaceService`) is currently checking the *fixed dev actor* against the data —
real per-user identity does not reach Spring today in any deployment mode.

### 2.3 The Next.js side has **two separate, demo-grade identity systems** — CONFIRMED

**Workspace-user auth** (`src/lib/auth.tsx`, `src/lib/server/session.ts`,
`src/lib/server/require-session.ts`, `src/proxy.ts`):
- `workos_session` httpOnly cookie, `sameSite: lax`, `secure` in production, 30-day `maxAge`.
- Session store: in-memory `Map<token, personId>` — lost on every restart.
- Every seeded demo `Person` shares one plaintext password, `DEMO_PASSWORD =
  "demo1234"` (`src/lib/server/demo-credentials.ts`), shown as an on-page hint at
  `/login`. Accounts created through `/admin` get a distinct plaintext password,
  compared with `===` (`people-repository.ts`'s `verifyPersonCredentials`).
- `proxy.ts` (Next middleware) only checks **cookie presence**, never validity —
  the real check happens per-request in each route handler via
  `requireSessionPersonId()`. A stale cookie is recovered client-side: any 401 from a
  non-auth endpoint triggers `handleStaleSession()` in `api-client.ts`, which clears
  the cookie and hard-navigates to `/login`.

**Admin auth** (`src/lib/admin-auth.tsx`, `src/lib/server/admin-session.ts`,
`src/lib/server/admin-credentials.ts`, `/admin`, `/admin/login`):
- Entirely separate `ADMIN_SESSION_COOKIE`, 8-hour `maxAge`, its own in-memory session map.
- **One single hardcoded credential pair**, not tied to any `Person` record at all:
  `admin@workos.dev` / `admin1234` (`admin-credentials.ts`).
- Powers `/admin`'s account list/create/delete UI, which calls
  `/api/admin/people*` (Next.js route handlers, still writing to the in-memory
  `people-repository.ts`, **not** to the Spring/MySQL `people` table).
- Same "middleware checks presence only" pattern as workspace-user auth.

**Net effect:** there are today three disconnected identity stores that can disagree
with each other — the Next in-memory `people-repository.ts`, the hardcoded admin
credential pair, and the Spring/MySQL `people` table (seeded separately, only under
`local-dev`, by `LocalDevPeopleSeeder`) — and **zero** role/permission concept beyond
workspace ownership and task ownership.

### 2.4 A cross-origin cookie gap that will bite the first time real sessions are added — PROPOSED finding

`CorsConfig` (Spring) allows the configured origins but does not set
`allowCredentials(true)`, and `api-client.ts`'s `request()`/`springRequest()` do not
pass `credentials: "include"` on `fetch()`. Browsers do not send cookies cross-origin
without both of these. Today this doesn't matter because Spring has no session cookie
to send. It will matter immediately once Spring (or a unified session) is expected to
receive a cookie set from the Next.js origin, or once Spring itself issues the
session cookie and the frontend calls it directly from the browser at a different
port/origin. This is a concrete compatibility risk, not a hypothetical one — flagged
again in §19.

### 2.5 What "role" already means in the data model — CONFIRMED, and a naming collision to avoid

`Person.role` (both the Next.js `Person` type and the Spring `Person` entity) is a
**free-text job title**, not an access-control role: the seeded values are `"Senior
PM"`, `"Lead Architect"`, `"Product Ops"`, `"Fullstack Dev"`, `"Legal & Program Ops"`,
`"Security & QA"`. The account-creation form in `/admin` (`src/app/admin/page.tsx`)
currently has a free-text **Role** input, not a dropdown, and nothing constrains its
values.

This document's new **access role** concept (§3) is a *different field* from this
existing `role` column — see §13's migration strategy for how they coexist.

---

## 3. Final Access Role Definitions — CONFIRMED requirement, not yet implemented

WorkOS will use exactly these eight access roles. These are access roles, not job
titles — no Department or Job Title field is added anywhere in this design.

1. **CEO**
2. **HR**
3. **IT**
4. **Graphics Designer**
5. **Marketing**
6. **Operation Manager**
7. **Sales Assistant**
8. **Sales Manager**

No additional access roles may be invented by any implementation phase. If a future
need arises for a ninth role, that is a new decision requiring the same
approval process this document went through — not an extension an implementer makes
unilaterally.

### 3.1 CEO — highest privilege

CEO has full access to all WorkOS modules, dashboards, employee data, and workspace
data. Specifically, CEO can:

- Manage employee accounts (create, and per §5, edit/deactivate as that phase defines).
- Create accounts for all 8 access roles, including other CEOs.
- Assign access roles during account creation.
- View all employees' Time Clock records (§6).
- View all employees' Reports (§7).
- Assign and reassign work (§8).
- Manage workspace membership across all workspaces (§9).
- Access every WorkOS module and feature.

Only the CEO may create employee accounts. There is no public registration, for any role.

---

## 4. Complete Role-Permission Matrix

This is the authoritative summary; §6–§9 give the full rules (including edge cases)
per module. Legend: **All** = every employee's records/data: **Own** = only the
signed-in user's own records.

| Access Role | Create Accounts | Time Clock | Reports | Assign/Reassign Work | Manage Workspace Membership | Chat |
|---|---|---|---|---|---|---|
| CEO | All 8 roles | All | All | Yes | All workspaces | Yes |
| HR | No | All | Own | No | View only, workspaces they belong to | Yes |
| IT | No | Own | Own | No | View only, workspaces they belong to | Yes |
| Graphics Designer | No | Own | Own | No | View only, workspaces they belong to | Yes |
| Marketing | No | Own | Own | No | View only, workspaces they belong to | Yes |
| Operation Manager | No | Own | All | Yes | Workspaces they own/manage (§9, §20) | Yes |
| Sales Assistant | No | Own | Own | No | View only, workspaces they belong to | Yes |
| Sales Manager | No | Own | Own | No | View only, workspaces they belong to | Yes |

Note the asymmetry between Time Clock and Reports: HR gets all-employee visibility for
Time Clock but only its own Reports; Operation Manager is the mirror image (own Time
Clock, all-employee Reports). This is exactly what §6/§7 of the requirements specify —
it is intentional, not an inconsistency to "fix."

Chat is the one column in this matrix that is **not** role-differentiated: every role
gets an identical "Yes," unlike every other column. See §4.1 for the binding
requirements behind that uniformity.

### 4.1 Chat Access — All Roles — CONFIRMED requirement, not yet implemented

Chat must be accessible to **every** authenticated WorkOS user, regardless of assigned
access role. Unlike Time Clock (§9), Reports (§10), Work Assignment (§11), and
Workspace Membership (§12), Chat access is not access-role-gated: all 8 roles in §3
receive identical Chat access. No future implementation phase may narrow this to a
subset of roles without a new decision going through the same approval process this
document went through — mirroring the constraint in §3 against inventing new roles
unilaterally.

Binding requirements:

1. Every one of the 8 access roles (§3) must be able to access and use the Chat
   module — no exceptions.
2. Chat must not be hidden or disabled based on a user's access role, in the UI or
   the backend.
3. The existing Chat frontend and its backend API functionality must be preserved.
   Chat's backend today is the Next.js route handlers described in §2.1
   (`/api/chat/*`), not Spring — if Chat's backend later moves to Spring as part of
   the broader migration in §6, this same preservation requirement carries over to
   that Spring implementation.
4. All Chat API endpoints must require authentication, rejecting unauthenticated
   requests.
5. Chat must resolve the message sender from the unified, authenticated `Person`
   identity established by this document's auth system (§6), not from any
   per-feature identity concept.
6. Client-supplied `personId` or sender identifiers must never be trusted to
   determine the sender of a message. The sender is always derived from the
   verified session/`ActingPersonResolver`-equivalent identity (§6.3), the same
   non-negotiable constraint already binding on `ActingPersonResolver` today.
   **CONFIRMED (current behavior):** the existing Chat route handlers
   (`src/app/api/chat/messages/route.ts`, `src/app/api/chat/stream/route.ts`)
   already follow this pattern — the sender/actor is read via
   `requireSessionPersonId()`, never from the request body — and this behavior must
   be preserved, not weakened, through the migration to unified auth.
7. Existing Chat messages and conversation data must be preserved through the
   authentication migration — this is a data-preservation requirement, not a
   Chat-specific migration plan (see §13 for the analogous `Person`-data migration
   constraints).
8. No additional Chat restrictions may be introduced based on access role unless
   explicitly approved through the same process as this document.
9. Chat availability does not, by itself, grant CEO-level account management
   (§5), Time Clock access beyond one's own (§9), Reports access beyond one's own
   (§10), work-assignment permission (§11), or workspace-membership management
   permission (§12). Chat access and every other module's permissions are evaluated
   independently.

---

## 5. Account Creation and Initial CEO Seeding Flow

### 5.1 Initial CEO account — backend seeder only

**Requirements (confirmed from the brief, restated as binding):**
- No public CEO registration, ever.
- No plaintext password in frontend code, ever.
- No credentials committed to the repository.
- Passwords stored using secure backend password hashing (PROPOSED: BCrypt via Spring
  Security's `PasswordEncoder` — see §6.3; the Spring backend currently has no hashing
  dependency at all, so this is new, not a swap).
- The seeder is idempotent — safe to run on every startup, never creates a duplicate CEO.
- The initial CEO's access role is `CEO`.

**PROPOSED mechanism**, modeled directly on the existing `LocalDevPeopleSeeder`
pattern already in the codebase (`backend/.../dev/LocalDevPeopleSeeder.java`), which
already demonstrates the idempotency shape this needs (`existsById` check before
insert, safe to re-run):

- A new `CeoAccountSeeder` (`ApplicationRunner`), registered in **every** profile
  (not `local-dev`-only like the existing dev seeder — the CEO must exist in real
  deployments too), that:
  1. Reads the initial CEO's email and password from environment variables /
     externalized config only (e.g. `APP_INITIAL_CEO_EMAIL`,
     `APP_INITIAL_CEO_PASSWORD`) — never a source-committed constant.
  2. Checks whether any `Person` with `accessRole = 'CEO'` already exists (not just a
     fixed id — see §13's caution about re-running against a partially-migrated
     table). If one exists, no-ops.
  3. If none exists and the required env vars are present, hashes the password and
     inserts the CEO `Person` row.
  4. If none exists and the required env vars are **absent**, fails startup loudly
     (matching the existing pattern where `LocalDevPeopleSeeder` fails loudly rather
     than silently degrading) rather than starting with no CEO and no way to log in.
- **First login:** the CEO logs in at the normal `/login` screen with the seeded
  email/password, like any other account — there is no separate "CEO first-run wizard"
  proposed here, since one isn't required by the brief. **REQUIRES CLARIFICATION**
  (§20): should the seeded password be a one-time value the CEO must change on first
  login, or a durable operational credential rotated out-of-band? The brief says
  "document how the initial CEO password is securely configured," which this section
  does, but doesn't mandate forced rotation — flagged rather than assumed.

Do **not** seed accounts for the other seven roles automatically. Only CEO is
seeded; the CEO creates everyone else through the account management UI, per §5.2.

### 5.2 Account creation form — exact fields, no more, no less

The CEO-only account creation form must contain **exactly**:

| Field | Type | Notes |
|---|---|---|
| Name | text | required |
| Email | email | required, unique |
| Password | password | required, minimum length enforced (existing UI already enforces ≥6 chars client-side; PROPOSED: raise this and enforce it server-side too, see §16) |
| Role | dropdown | one of the 8 access roles in §3, required |

**No** Department field. **No** Job Title field. **No** public registration. **No**
self-service account creation. Only an authenticated CEO may create accounts or assign
access roles.

**Compatibility note (CONFIRMED gap vs. current code):** the existing `/admin` "New
account" dialog (`src/app/admin/page.tsx`) already has Name/Email/Password fields, but
its "Role" field is a **free-text input**, not a dropdown, and it writes to the
free-text job-title `role` column via the Next.js in-memory `people-repository.ts` —
not to Spring/MySQL, and not to any access-role concept. This entire flow must be
rebuilt against the new access-role-aware, CEO-gated, Spring-backed endpoint (§14),
not incrementally patched, per §5.3.

### 5.3 Retiring the separate admin auth system

The existing `/admin` console is a fully separate identity system unrelated to any
`Person` (§2.3) — this must be retired, not preserved alongside the new system, per
the requirement that account management flows through the CEO's authenticated
`Person`-based session.

**PROPOSED retirement plan:**
1. Build the new CEO-gated account-management surface as a normal page inside the
   regular authenticated app (e.g. `/admin` or `/accounts`, protected by the same
   unified session + a CEO-only authorization check), not a separately-cookied area.
2. Once the new surface is live and verified, delete: `/admin/login`, the
   `AdminAuthProvider`/`useAdminAuth()`, `admin-session.ts`,
   `admin-session-cookie.ts`, `require-admin-session.ts`, `admin-credentials.ts`, and
   the `/api/admin/auth/*` and `/api/admin/people*` Next.js route handlers.
3. `proxy.ts`'s admin-specific branches (lines checking `ADMIN_SESSION_COOKIE`) are
   removed in the same pass.
4. This is a **deletion of a demo system**, not a migration of data — the admin
   console never wrote to the real (Spring/MySQL) `people` table, so there is no admin
   console data to carry forward (see §13 for what *does* need carrying forward: the
   Next.js in-memory people and the `local-dev` MySQL seed).

---

## 6. Authentication / Session Requirements

### 6.1 Final system requirements (restated as binding, from the brief)

- Backend-owned authentication (Spring Boot, not Next.js route handlers).
- Secure password hashing.
- Persistent, revocable sessions.
- Login endpoint.
- Logout endpoint.
- Authenticated current-user endpoint.
- Identity resolution from a verified session — never from client-supplied `personId`.
- Protected backend endpoints by default (currently the opposite is true — see §11).
- Appropriate role-based authorization.
- Secure session cookie handling.
- No public registration.

### 6.2 Migration approach — PROPOSED, evaluating the pieces named in the brief

The brief asks explicitly to evaluate "the existing Next.js auth provider, admin auth
system, in-memory sessions, proxy middleware, and API routes" and recommend a
migration that preserves functionality while removing the disconnected demo systems.
Findings and recommendation:

| Existing piece | Verdict | Disposition |
|---|---|---|
| `src/lib/auth.tsx` (`AuthProvider`/`useAuth()`) | Keep the *shape* (React context exposing `user`/`status`/`login`/`logout`) | Its `fetchMe`/`loginRequest`/`logoutRequest` calls move from Next.js relative routes to the Spring backend's endpoints — component code (`useAuth()` callers) needs no change, only `api-client.ts`'s implementation of those three functions |
| `src/lib/admin-auth.tsx` + admin cookie/session files | Retire entirely | Per §5.3 |
| In-memory `session.ts` `Map` | Retire entirely | Replaced by Spring-backed persistent sessions (§6.3) |
| `src/proxy.ts` | Keep the *pattern* (cheap presence-check redirect gate in front of real per-request validation) | Cookie name/shape changes to match whatever the new Spring-issued session cookie is; still not the source of truth for authorization, same as today |
| `/api/auth/*` Next.js route handlers | Retire, or convert to thin proxies | **REQUIRES CLARIFICATION** (§20): does the frontend call Spring directly (like `springRequest()` already does for Tasks/Workspaces/Boards), or do these stay as Next.js routes that proxy to Spring server-side? Both are viable; the choice affects cookie domain/SameSite handling (§2.4) |
| `people-repository.ts` (Next in-memory) | Retire entirely | Spring/MySQL `people` table becomes the only `Person` store (§13) |
| `demo-credentials.ts` / `admin-credentials.ts` | Delete | Shared/hardcoded demo passwords have no place once real per-user hashed passwords exist |

### 6.3 Session/password mechanics — PROPOSED

- **Password hashing:** BCrypt via Spring Security's `PasswordEncoder`
  (`spring-boot-starter-security` is not currently a dependency — this is new, not a
  reconfiguration of something existing).
- **Session strategy:** either server-side persistent sessions (a new `sessions`
  table, or Spring Session backed by MySQL/Redis) or signed/stateless JWTs. `BACKEND.md`
  already anticipated this exact fork in the road for the old design
  (`getSessionPersonId` "just becomes verify-and-decode instead of a map lookup") —
  the same flexibility applies here. **PROPOSED default: persistent, revocable
  DB-backed sessions**, because the brief explicitly requires sessions to be
  "revocable" (a bare JWT without a server-side revocation list can't be revoked
  before expiry) and because a CEO deactivating an employee's account (§5) should be
  able to immediately kill that employee's active sessions.
- **Cookie:** httpOnly, `SameSite` and `Secure` attributes matching the existing
  `workos_session` cookie's posture (lax/secure-in-prod), issued and validated by
  Spring if Spring becomes the session authority (see the open question in §6.2's
  table), with `allowCredentials(true)` added to `CorsConfig` and
  `credentials: "include"` added to the frontend's `fetch()` calls (§2.4).
- **`ActingPersonResolver` replacement:** a new implementation (e.g.
  `SessionActingPersonResolver`), registered outside `local-dev`, that resolves the
  actor from the verified session — never from a header/param/body the client
  controls, preserving the exact non-negotiable constraint already documented on the
  `ActingPersonResolver` interface today. `LocalDevActingPersonResolver` is kept
  **only** for local development under the `local-dev` profile, exactly as it is now.

---

## 7. Backend Authorization Requirements

### 7.1 How the backend resolves identity and checks permissions — PROPOSED

1. Every authenticated request carries the session cookie (or bearer token, per
   §6.2's open question).
2. A servlet filter / Spring Security filter chain validates the session and resolves
   it to a `Person` (id + `accessRole`), exposed to controllers the same way
   `ActingPersonResolver` already does today (interface preserved, implementation
   swapped, per §6.3) — this keeps the existing controller code
   (`actingPersonResolver.currentPersonId()`) largely unchanged, minimizing churn in
   already-working modules (Workspaces, Boards, Tasks).
3. A new `currentPerson().getAccessRole()` (or equivalent) becomes available
   alongside the existing `currentPersonId()`, for the new role-gated checks in §8/§9/§10.
4. Endpoints that currently have no auth check at all get one (see §7.2). Endpoints
   that already enforce workspace-membership/task-ownership (Tasks, Workspaces) keep
   that logic and layer the new access-role checks on top where the brief requires it
   (assignment, per §10).

### 7.2 Endpoints confirmed to currently lack authentication or role authorization

| Module | Endpoint | Current state | Required change |
|---|---|---|---|
| People | `GET /api/people` | No auth at all (documented exception in `BACKEND.md`, confirmed in `PersonController`) | **REQUIRES CLARIFICATION** (§20): keep this endpoint open, or gate it behind authentication now that real accounts/passwords exist? Its own doc comment calls out the exception as deliberate, but that was written when there was no real auth to have |
| Time Clock | `GET /api/time/entries` | Open to **any** authenticated actor, no role check (`TimeEntryService`'s own Javadoc says so explicitly) | Must enforce §11's rules: CEO/HR see all, everyone else sees only their own — see §11 for the concrete fix |
| Reports | *(no endpoint exists yet — client-side only, see §7.3)* | N/A | New endpoint(s) needed, role-gated per §12 |
| Work assignment | `PATCH /api/tasks/:taskId` (ownerId/assigneeIds fields) | Enforces **owner-only**, not **CEO/Operation-Manager-only** (`TaskService.updateTask`) | Must change — see §13's compatibility note, this is a real behavior change, not just "add a check" |
| Members | `POST/DELETE /api/workspaces/:id/members*` | Enforces **workspace-owner-only** (`WorkspaceService.addMember`/`removeMember`), no access-role concept at all | Must add CEO-bypasses-ownership and Operation-Manager-scoped rules — see §14, flagged as a decision in §20 |
| Account creation | *(no endpoint exists on Spring side yet — only the retiring Next.js admin routes, §5.3)* | N/A | New CEO-only endpoint needed, see §14 |
| Boards | `POST/PATCH/DELETE /api/boards/:id` | Workspace-membership only, no owner/role distinction | Not named in the brief's required permission rules — **no new restriction proposed**; preserve as-is unless a future decision extends RBAC here |
| Chat | `/api/chat/*` (Next.js, participant-only, session-authenticated) | Already requires authentication and already resolves the sender from the session (CONFIRMED, §4.1 item 6) — no access-role concept exists or is required | No new *role* restriction — Chat is intentionally uniform across all 8 roles (§4.1). The required change is migrating its identity source to the unified auth system in §6 while preserving today's already-correct "never trust a client-supplied sender id" behavior — see §7.5 |

### 7.3 Reports has no backend surface to authorize yet

The `/dashboards` (Reports) page (`src/app/(app)/dashboards/page.tsx`) is entirely
client-side today: it reads `tasksByBoard` (already scoped to boards in workspaces
the signed-in user belongs to, via the existing Task/Board APIs) and computes
completion percentages and a cross-board "Completed Tasks" list in the browser. There
is **no `Report` entity, no Reports endpoint, and no server-side Reports concept
anywhere in the codebase today.** Designing §12's permission rule ("HR/IT/etc. can
view only their own Reports") requires first deciding what a "Report" *is* as a
data concept — this is flagged as an open decision in §20, not assumed here.

### 7.4 Boards is intentionally not touched by this RBAC pass

Boards has no access-role permission rules defined anywhere in the brief (§9 of the
original requirements covers Members/workspace-membership, not board-level
permissions). Per the brief's own instruction not to invent permissions that haven't
been defined, this document proposes **no change** to Boards' workspace-
membership-only model. A future RBAC pass may extend to this, but that is out of
scope here.

Chat, unlike Boards, *is* covered by an explicit, finalized requirement — see §7.5.

### 7.5 Chat Backend Authorization Requirements — PROPOSED, building on CONFIRMED current behavior

Chat is in scope for this RBAC pass, but only for identity/authentication
plumbing — never for access-role gating (§4.1). Concretely:

1. Every Chat API endpoint (`GET /api/chat/messages`, `POST /api/chat/messages`,
   `GET /api/chat/stream`, and any future Chat endpoint) must require an
   authenticated `Person`, rejecting unauthenticated requests with 401 — **already
   true today** (`requireSessionPersonId()` returns 401 when absent), and this must
   continue to hold once identity resolution moves to the unified auth system (§6).
2. The authenticated sender/actor for a Chat action must be resolved from the
   verified session — the unified equivalent of today's `requireSessionPersonId()`,
   or `ActingPersonResolver` if Chat's backend later moves to Spring (§4.1 item 3) —
   never from a client-supplied `personId`, `senderId`, or any other body/query/header
   value. This is **already true today** in `chat-repository.ts`'s callers; the
   requirement is to preserve it, not introduce it.
3. All 8 access roles (§3) are authorized to use Chat — there is no role check to
   add, and no role check may be added, beyond the existing participant-only
   conversation-access check (`canAccessConversation`), which governs *which
   conversation* a person can read/post into, not *whether their role* permits Chat
   at all.
4. Chat authorization must not accidentally inherit restrictions intended for other
   modules. Concretely: the new `accessRole` field being added to the authenticated
   `Person` (§6.3, §8) must not be consulted anywhere in the Chat code path to
   permit or deny access — Chat's only authorization check remains conversation
   participation.
5. Chat access must not grant additional permissions in other modules. Concretely:
   nothing in the Chat code path may be used as, or treated as, evidence of
   authorization for Time Clock (§9), Reports (§10), work assignment (§11),
   workspace membership (§12), or account management (§5).

---

## 8. Frontend Integration Requirements — PROPOSED

- The frontend must never be trusted as the sole enforcement layer (per the brief) —
  every rule in §11/§12/§13/§14 must be enforced server-side first; frontend
  visibility rules are a UX convenience layered on top, never a substitute.
- `useAuth()`'s `Person` shape gains `accessRole` (alongside the existing job-title
  `role` field, kept separate per §13.2) so the frontend can conditionally render
  CEO-only UI (account management), Assign-button visibility (CEO/Operation Manager
  only, replacing today's owner-only `OwnerPicker` gate — see §10's compatibility
  note), and Time Clock/Reports view scoping.
- The existing `useTaskPermissions()` hook (`src/lib/permissions.ts`) computes
  owner/assignee-based `canEditCore`/`canEditProgress` — this hook's *purpose*
  (core vs. progress field tiers) is unrelated to and unaffected by the new
  access-role work-assignment rule; §10 only changes who may touch `ownerId`/
  `assigneeIds` specifically, not the rest of `canEditCore`'s owner-only fields.
- The `OwnerPicker` component's `disabled` prop (currently "not the task's owner")
  must be re-derived from "not CEO and not Operation Manager" once §10 is enforced
  server-side — otherwise the UI will offer a control the backend now rejects.
- Members page (`src/app/(app)/members/page.tsx`) currently scopes to
  `workspaces.filter(w => w.ownerId === user?.id)` — this must be extended so CEO sees
  every workspace and Operation Manager sees workspaces they're authorized to manage
  (§14), not only ones they personally created.
- **Chat (§4.1, §7.5):** Chat remains visible and accessible in the UI for every
  authenticated role — it must not be hidden or disabled by role-based UI gating,
  unlike the CEO-only/Operation-Manager-only affordances above. The existing Chat UI
  and functionality are preserved as-is; the only change is that Chat's identity
  source moves to the unified WorkOS authentication system (§6) alongside every other
  module, once that migration lands.

---

## 9. Time Clock Permission Rules

| Access Role | Own records | All employees' records |
|---|---|---|
| CEO | ✅ | ✅ |
| HR | ✅ | ✅ |
| IT | ✅ | ❌ |
| Graphics Designer | ✅ | ❌ |
| Marketing | ✅ | ❌ |
| Operation Manager | ✅ | ❌ |
| Sales Assistant | ✅ | ❌ |
| Sales Manager | ✅ | ❌ |

**Binding constraint:** backend authorization is mandatory. A non-CEO/non-HR user must
not be able to reach another employee's records by manipulating `personId`, query
parameters, URLs, or direct API requests.

**PROPOSED enforcement point:** `TimeEntryController.entries()` /
`TimeEntryService.listEntries()`. Today `listEntries(personIdFilter)` returns any
person's entries (or all entries with no filter) to any authenticated caller — this
is exactly the gap the module's own Javadoc flags as deferred pending a role system.
The fix: `listEntries` takes the *actor's* resolved `Person` (id + access role), and:
- if `accessRole` is CEO or HR → honor `personIdFilter` as today (or omit it for all).
- otherwise → ignore any client-supplied `personId` and always scope to the actor's
  own id, regardless of what the request asked for.

---

## 10. Reports Permission Rules

| Access Role | Own Reports | All employees' Reports |
|---|---|---|
| CEO | ✅ | ✅ |
| Operation Manager | ✅ | ✅ |
| HR | ✅ | ❌ |
| IT | ✅ | ❌ |
| Graphics Designer | ✅ | ❌ |
| Marketing | ✅ | ❌ |
| Sales Assistant | ✅ | ❌ |
| Sales Manager | ✅ | ❌ |

**Binding constraint:** enforced by the backend; a user must not reach another
employee's Reports by changing a `personId` or calling the API directly.

**Blocked on a design decision (§7.3, §20):** there is no Reports data concept or
endpoint today to attach this rule to. This table specifies the *rule*; the endpoint
design itself is deferred to whichever phase resolves what a "Report" is (per-board
completion stats scoped to a person's task ownership/assignment? A dedicated
per-person KPI rollup? Something else?). Do not build an endpoint against a guessed
shape — resolve the open question first.

---

## 11. Work Assignment Permission Rules

Only **CEO** and **Operation Manager** may assign or reassign work:
- Assign tasks to employees.
- Reassign tasks.
- Change task assignees.

All other roles cannot assign/reassign work to other employees. Other roles may still
view and update their own assigned tasks per the **existing** task ownership/field
rules (`canEditCore`/`canEditProgress`, §8) — this RBAC rule narrows *who may change
`ownerId`/`assigneeIds`*, it does not replace the rest of the existing task permission
model.

**Binding constraint:** enforced server-side on task creation, assignment,
reassignment, and any endpoint that changes assignees. Hiding the Assign button in the
UI is not sufficient (per the brief, restated as binding).

**CONFIRMED compatibility conflict, not just a gap:** `TaskService.updateTask()`
today lets the task **owner** (whoever created it) change `ownerId`/`assigneeIds` —
that's the existing "core fields, owner-only" rule. The new requirement is stricter
and role-based, not ownership-based: a non-CEO/non-Operation-Manager task owner must
**lose** the ability to reassign their own task, which they currently have. This is a
genuine behavior change to existing, already-implemented logic, not an additive
permission check — flag this explicitly to whoever implements Phase 3, since it's easy
to miss when only reading `TaskService` as "already has owner checks, must be fine."

**PROPOSED enforcement point:** in `TaskService.updateTask()` (and `createTask()` for
initial assignment), when the patch touches `ownerId` or `assigneeIds`, additionally
require `actor.accessRole ∈ {CEO, Operation Manager}` — on top of, not instead of, the
existing owner-only gate for the rest of the core fields.

---

## 12. Members / Workspace Membership Permission Rules

| Access Role | View members | Add/remove members | Create employee accounts | Change global access roles |
|---|---|---|---|---|
| CEO | All workspaces | All workspaces | ✅ | ✅ |
| Operation Manager | Workspaces they own/manage | Workspaces they own/manage | ❌ | ❌ |
| All other roles | Workspaces they belong to | ❌ | ❌ | ❌ |

**Binding constraints (restated from the brief):**
- Adding an employee to a workspace must **not** create a new account or change their
  global access role.
- Workspace membership and global WorkOS access roles are, and remain, separate
  concepts — a `Workspace.memberIds` entry is not an access grant beyond
  visibility/participation in that workspace's boards/tasks.
- Preserve existing workspace permission checks where compatible (see below for what
  "compatible" means concretely).

**CONFIRMED compatibility note:** `WorkspaceService.addMember`/`removeMember`
currently require `actor.id == workspace.ownerId`, full stop — there is no broader
concept today. The CEO-bypasses-ownership rule is purely additive (an `OR
accessRole == CEO` alongside the existing owner check) and safe to layer on without
touching existing owner behavior. **The Operation Manager rule is not purely
additive** — "workspaces they own or are authorized to manage" implies a scope wider
than `ownerId` alone might cover, and the current schema has no concept of
"authorized to manage a workspace I don't own." This is flagged as an open decision in
§20 rather than resolved here, because guessing the shape (e.g., inventing a new
`workspace_managers` table) without confirmation would violate the brief's "do not
invent permissions that have not been defined" instruction.

---

## 13. Existing Demo Account Migration Strategy

### 13.1 What exists today, precisely

Three separate People datasets exist right now, none of which have an access role:

1. `workos-app/src/lib/data.ts`'s `PEOPLE` (6 accounts: sarah-chen, alex-morgan,
   priya-patel, david-kim, maya-reyes, noah-ibrahim) — seeds the Next.js in-memory
   `people-repository.ts`.
2. The same 6 accounts, mirrored in
   `backend/.../dev/LocalDevPeopleSeeder.java` — seeds the Spring/MySQL `people` table,
   **only** under the `local-dev` profile.
3. Any accounts created through the (soon-to-be-retired, §5.3) `/admin` console —
   written only to the Next.js in-memory store, never to MySQL.

Every one of these has a `role` value that is a **job title** (`"Senior PM"`, `"Lead
Architect"`, etc.), not one of the 8 access roles in §3.

### 13.2 Binding constraints (restated from the brief)

- Do **not** automatically map existing job-title `role` values to the new access
  roles — there is no reliable mapping ("Lead Architect" is not obviously "IT" or
  any other role).
- Do **not** silently grant CEO or HR privileges to any existing person.
- The access role must be stored **separately** from any existing display/job-title
  data that is retained.

### 13.3 PROPOSED migration mechanics

- New Flyway migration (`V5__add_access_role_to_people.sql` or similar) adds a new,
  separate column — e.g. `access_role VARCHAR(32) NULL` — to the `people` table.
  **Nullable**, not defaulted to any role, so every existing row starts with **no**
  access role until a CEO explicitly assigns one. The existing `role` (job-title)
  column is untouched.
- A `password_hash` column is added in the same or a companion migration (no password
  column exists today at all — see §2.2 — so this is a net-new column, not a
  type change).
- After migration, the 6 demo accounts (and any `/admin`-created ones that are
  carried forward, per the decision in §20) exist in MySQL with `access_role = NULL`
  and no usable password — they **cannot log in** until:
  1. A CEO account exists (seeded, §5.1), and
  2. The CEO explicitly assigns each person an access role and (per whatever password
     flow the implementation phase settles on — direct assignment, or a "set your own
     password" invite flow, flagged as an open question in §20) a real password.
- **REQUIRES CLARIFICATION** (§20): should the 6 demo people be carried into the real
  schema at all, given they were never given real per-user passwords or a job-title →
  access-role mapping that's safe to assume? An equally valid answer is "start the
  `people` table empty except for the seeded CEO, and let the CEO create every real
  account fresh."

---

## 14. API Endpoint Changes/Additions Required — PROPOSED

| Endpoint | Change |
|---|---|
| `POST /api/auth/login` | New, on Spring — replaces the Next.js `/api/auth/login` per §6.2's chosen migration shape |
| `POST /api/auth/logout` | New, on Spring — same |
| `GET /api/auth/me` | New, on Spring — same; response includes `accessRole` |
| `POST /api/accounts` (or similar; final path TBD) | **New.** CEO-only. Body: `{name, email, password, accessRole}` exactly per §5.2's 4-field form. Creates a `Person` with a hashed password and the chosen access role |
| `GET /api/accounts` | **New.** CEO-only (replaces `/api/admin/people` GET, §5.3) |
| `PATCH /api/accounts/:id` | **New**, if account editing/deactivation is in scope for this phase — **REQUIRES CLARIFICATION** (§20), not named explicitly in the brief beyond account *creation* |
| `GET /api/people` | Unchanged shape; auth requirement TBD (§7.2, §20) |
| `GET /api/time/entries` | **Behavior change**, same path: role-scoped per §11, no signature change needed (`personId` param becomes advisory for CEO/HR, ignored otherwise) |
| `PATCH /api/tasks/:taskId` | **Behavior change**, same path: `ownerId`/`assigneeIds` changes additionally gated to CEO/Operation Manager per §13 |
| `POST /api/tasks` | **Behavior change**, same path: initial `assigneeIds`/non-self `ownerId` at creation time gated the same way, for consistency with §11 — **REQUIRES CLARIFICATION** (§20): does a non-CEO/non-OM employee lose the ability to assign a task to *anyone*, or only to *reassign after creation*? The brief's wording ("assign tasks to employees... assign and reassign work") reads as covering both, but this is worth an explicit confirmation before implementation since it changes everyday task-creation UX for most roles |
| `POST/DELETE /api/workspaces/:id/members*` | **Behavior change**, same path: CEO bypasses ownership; Operation Manager scope per §14's open question |
| Reports endpoint(s) | **New**, shape blocked on §7.3/§20's open question |
| `/api/admin/auth/*`, `/api/admin/people*` | **Deleted** per §5.3 |
| `/api/chat/*` (`messages`, `stream`, and any future Chat endpoint) | **No role-authorization change** — all 8 roles remain authorized (§4.1, §7.5). Only the identity source changes, moving from today's `requireSessionPersonId()` to the unified session/`Person` identity from §6, once that migration phase reaches Chat |

---

## 15. Database Schema / Migration Proposal — PROPOSED

Building on the existing Flyway-owned schema (`V1`–`V4`; Hibernate is validate-only,
per `spring.jpa.hibernate.ddl-auto=validate` — **all schema changes must go through
new Flyway migrations**, never rely on Hibernate to generate DDL):

```sql
-- V5__add_auth_and_access_role_to_people.sql (illustrative, not final)
ALTER TABLE people
    ADD COLUMN access_role  VARCHAR(32) NULL,   -- one of the 8 roles in §3, or NULL until assigned
    ADD COLUMN password_hash VARCHAR(255) NULL; -- NULL until the account has a real password
```

```sql
-- V6__create_sessions_table.sql (illustrative — only if persistent DB-backed
-- sessions are the chosen strategy over JWTs, per §6.3's open fork)
CREATE TABLE sessions (
    token       VARCHAR(128) NOT NULL,
    person_id   VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (token),
    CONSTRAINT fk_sessions_person FOREIGN KEY (person_id) REFERENCES people (id)
) ENGINE = InnoDB;
```

**Explicitly not proposed:** repurposing the existing `role` column, adding a
`department`/`job_title`-labeled column, or a `CHECK` constraint on `access_role`
naming — a `CHECK (access_role IN (...))` is tempting for the 8 fixed values but
would make adding/renaming a role a schema migration rather than an application-level
change; **REQUIRES CLARIFICATION** (§20) whether that rigidity is wanted.

A Reports-related schema addition is deliberately **not proposed** here — it's
downstream of §7.3/§20's unresolved "what is a Report" question.

---

## 16. Security Requirements

- Passwords: hashed with BCrypt (or an equivalent modern KDF), never logged, never
  returned in any API response (the existing `Person`/`PersonResponse` DTOs already
  never include a password field — preserve that).
- Sessions: revocable server-side (§6.3); a CEO deactivating/deleting an account
  should invalidate that person's active sessions, not just block future logins.
- No client-supplied `personId`/`actorId` is ever trusted for identity — this is
  already a hard invariant of `ActingPersonResolver` today and must remain one.
- CORS: keep the existing explicit-allowlist approach (`CorsConfig`'s
  `app.cors.allowed-origins`), add `allowCredentials(true)` once cookies must cross
  the Next.js↔Spring origin boundary (§2.4), and do **not** switch to a wildcard
  origin, which is incompatible with credentialed requests entirely.
- Rate limiting / login throttling on `/api/auth/login`: not present today in any
  form; **REQUIRES CLARIFICATION** (§20) whether it's in scope for this phase or a
  later hardening pass.
- The GlobalExceptionHandler's existing convention (`{"error": "..."}`, never leaking
  internal exception details on unexpected errors — confirmed in
  `GlobalExceptionHandler.handleUnexpected()`) is preserved for new auth-related error
  paths (e.g. a new `UnauthorizedException` → 401, following the same pattern as the
  existing `ForbiddenException`/`ConflictException`/`ResourceNotFoundException`).
- Local dev credentials: `application.properties` already correctly avoids a default
  `DB_PASSWORD` (must be set via environment). The same discipline applies to the new
  `APP_INITIAL_CEO_PASSWORD` (§5.1) — never a source-committed default.
- Chat sender spoofing (§4.1, §7.5): Chat must never trust a client-supplied
  `personId`/sender identifier to attribute a message. This is already correctly
  enforced today (`requireSessionPersonId()`) and must remain enforced — with the
  identity source, not the rule itself, changing — once Chat's auth migrates to the
  unified session (§6).

---

## 17. Implementation Phases

**Phase 1 — Database and Role Foundation.** Flyway migrations for `access_role`,
`password_hash`, and (if DB-backed sessions are chosen) a `sessions` table. No
application behavior changes yet — additive schema only.

**Phase 2 — Backend Authentication and Sessions.** Add `spring-boot-starter-security`
and a password-hashing `PasswordEncoder`; implement login/logout/me endpoints on
Spring; implement the session mechanism chosen in §6.3; write the real
`ActingPersonResolver` implementation that replaces the client-trust-nothing
resolution with real verified-session resolution (kept alongside, not instead of,
`LocalDevActingPersonResolver` for local dev).

**Phase 3 — Backend Authorization and Account Management.** Implement §9/§10/§11's
role checks in `TimeEntryService`/`TaskService`/`WorkspaceService`; implement the new
CEO-only account-management endpoints (§14); implement the work-assignment behavior
change flagged in §11/§14 (this is the phase most likely to have a real regression
if the "owner-only → CEO/OM-only" narrowing isn't communicated clearly to whoever
implements it).

**Phase 4 — Initial CEO Seeder.** The idempotent, always-registered `CeoAccountSeeder`
(§5.1), reading credentials from environment config only.

**Phase 5 — Frontend Unified Authentication.** Point `AuthProvider`/`api-client.ts`'s
auth functions at the new Spring endpoints (§6.2); retire the Next.js in-memory
session store and its route handlers; resolve the CORS/credentials gap (§2.4). Chat's
route handlers (`/api/chat/*`) move their identity resolution to the same unified
session in this phase, preserving their existing "never trust a client-supplied
sender id" behavior (§4.1, §7.5) — Chat gets no new role checks in this or any phase.

**Phase 6 — CEO Account Management Interface.** Build the new 4-field account-creation
UI (§5.2) inside the regular authenticated app, CEO-gated; retire `/admin`,
`/admin/login`, and every admin-specific file listed in §5.3.

**Phase 7 — Role-Based Dashboard and Module Visibility.** Wire `accessRole` into
`useAuth()`'s `Person`; gate Time Clock/Reports view scoping and the Assign-button
visibility (§8) — as UX convenience layered on top of Phase 3's server-side
enforcement, never as the enforcement itself.

**Phase 8 — Security and Regression Testing.** Verify every rule in §9/§10/§11/§12
against direct API calls (not just the UI) — matching the brief's explicit "manipulating
personId, query parameters, URLs" concern; verify existing Task/Workspace/Board
functionality (creation, editing, the *non*-reassignment parts of task permissions)
is unregressed by the new access-role layer; verify session revocation actually
revokes. Also include Chat in this pass (§4.1, §7.5): verify all 8 access roles can
reach and use Chat; verify every Chat endpoint rejects unauthenticated requests;
verify a user cannot spoof another sender by manipulating `personId`/sender id in a
direct API call; and verify existing Chat messages/conversations are intact and
unregressed after the identity-source migration.

---

## 18. Acceptance Criteria

- A brand-new deployment (empty MySQL, no `local-dev` profile) starts successfully,
  seeds exactly one CEO account from environment-provided credentials, and creates no
  duplicate CEO on a restart.
- No account can be created except by an authenticated CEO, and only through the
  4-field form (§5.2) with a role chosen from the exact 8-item dropdown.
- A non-CEO/non-HR user calling `GET /api/time/entries?personId=<someone-else>`
  directly (bypassing the UI) receives only their own entries, not the requested
  person's.
- A non-CEO/non-Operation-Manager user calling `PATCH /api/tasks/:id` with an
  `ownerId`/`assigneeIds` change directly (bypassing the UI) is rejected, even if they
  are the task's current owner.
- Adding a person to a workspace never changes their `access_role` or creates a new
  `Person` row.
- The `/admin` console and its dedicated cookie/session system no longer exist post-
  Phase 6, and no code path depends on `ADMIN_SESSION_COOKIE`.
- Every session-authenticated endpoint rejects a request with no cookie / an invalid
  cookie with 401, and a request lacking the required access role with 403 — using the
  existing `{"error": "..."}` convention.
- The 6 existing demo people, if carried forward (§13.3, pending §20), have no access
  role and cannot log in until a CEO explicitly assigns one.
- All 8 access roles (§3) can access and use Chat — verified for CEO, HR, IT,
  Graphics Designer, Marketing, Operation Manager, Sales Assistant, and Sales Manager
  individually, not just spot-checked (§4.1).
- Every Chat API endpoint rejects an unauthenticated request with 401.
- The Chat message sender is always the authenticated session's `Person`, never a
  value read from the request body/query/header.
- A user cannot spoof another sender's identity in Chat by manipulating a
  client-supplied `personId` or sender id, verified by direct API call, not just
  through the UI.
- Existing Chat messages and conversation data remain intact and unchanged after the
  authentication migration.
- Chat availability does not, by itself, grant any user account management,
  Time Clock, Reports, work-assignment, or workspace-membership permissions they
  would not otherwise have (§4.1 item 9).

---

## 19. Risks and Compatibility Considerations

- **The work-assignment rule is a behavior *narrowing*, not just a new check** (§11) —
  regular employees who can reassign their own tasks today will lose that ability.
  This is the single highest-risk change in this spec for "it looks done but breaks a
  workflow someone relies on" — flagged for extra scrutiny in Phase 3/8.
- **Cross-origin cookies** (§2.4) will silently fail (cookie never sent) if
  `allowCredentials`/`credentials: "include"` aren't both set — this is easy to miss
  because everything *looks* like it should work in same-origin local dev if the
  frontend and Spring happen to be proxied together, and only breaks once they're
  genuinely cross-origin (different ports today, likely different hosts in a real
  deployment).
- **The migration is mid-flight already** (§2.1) — Tasks/Workspaces/Boards/People/Time
  Clock already point at Spring; Auth/Chat/Admin/Reset don't yet. Any phase that
  assumes a clean "before" state should re-verify against current `api-client.ts`
  before starting, since this document's own snapshot will age.
- **Reports has no backend concept to hang permissions on** (§7.3) — implementing
  §10's table requires a prior, separate design decision about what a Report is; doing
  this ad hoc during Phase 3 risks inventing a shape nobody approved.
- **Operation Manager's workspace-management scope is underspecified** (§12, §20) —
  implementing it against a guess risks building the wrong authorization model for a
  role explicitly called out in the brief as needing broader-than-owner access.
- **Demo data has no safe automatic path forward** (§13) — any implementer tempted to
  "just default everyone to some role so login works in the demo" would directly
  violate the brief's explicit prohibition on silently granting privileges.
- **Chat is lower-risk than it looks, but only if its migration doesn't drift from
  the rest of Phase 5** (§4.1, §7.5) — Chat's sender-identity handling is already
  correct today (`requireSessionPersonId()`, never a client-supplied id), so the risk
  isn't a missing check, it's an implementer "helpfully" adding a role gate to Chat
  during the access-role rollout that was never asked for and directly contradicts
  §4.1's uniform-access requirement.
- **Test coverage exists for current Spring services** (`TaskServiceTest`,
  `WorkspaceServiceTest`, `TimeEntryServiceTest`, etc., confirmed present under
  `backend/src/test/java`) — Phase 3's authorization changes should extend these
  existing suites rather than relying only on new tests, since the existing ones are
  the regression net for the *non*-RBAC behavior (ownership, membership, clock
  in/out conflicts) that must keep working unchanged.

---

## 20. Decisions That Still Require Clarification

Consolidated from every **REQUIRES CLARIFICATION** flagged above, in the order they
first appear:

1. **§5.1** — Is the seeded initial CEO password a one-time value forced to rotate on
   first login, or a durable operational credential?
2. **§6.2** — Does the frontend call the new Spring auth endpoints directly (like it
   already does for Tasks/Workspaces/Boards), or do `/api/auth/*` stay as Next.js
   routes that proxy to Spring server-side? Affects cookie domain/SameSite design.
3. **§7.2** — Should `GET /api/people` remain open (no auth), now that real
   accounts/passwords will exist, or should it require authentication going forward?
4. **§7.3 / §10** — What is a "Report" as a data concept? (Needed before any Reports
   endpoint or permission enforcement can be built.)
5. **§12** — What does "workspaces [Operation Manager is] authorized to manage" mean
   precisely — only workspaces they created (`ownerId`), or a broader delegated-
   management concept requiring new schema?
6. **§13.3** — Should the 6 existing demo `Person` records be carried into the real
   schema at all, or should the real deployment start with only the seeded CEO and
   let all other accounts be created fresh?
7. **§14** — Does a non-CEO/non-Operation-Manager lose the ability to assign
   `assigneeIds` at task *creation* time too, or only lose the ability to *reassign*
   after creation?
8. **§15** — Should `access_role` be a free `VARCHAR` (application-level validation
   only) or a DB-level `CHECK`/enum constraint? Trade-off: rigidity vs. ease of future
   role changes.
9. **§16** — Is login rate-limiting/throttling in scope for this implementation pass,
   or deferred to a later hardening phase?
10. **§14 (account editing)** — Is editing or deactivating an existing account's role/
    password in scope for this phase, or is only account *creation* (as explicitly
    named in the brief) in scope for now?

---

*End of specification. No source code, database, or Git history was modified while
producing this document.*
