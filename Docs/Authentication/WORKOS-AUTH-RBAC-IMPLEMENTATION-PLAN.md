# WorkOS Authentication & RBAC — Implementation Plan

**Status:** Documentation only. No application code, database schema, migration, or
Git history was modified while producing this document.

**Relationship to the spec:** This plan implements
[`WORKOS-AUTH-RBAC-SPEC.md`](./WORKOS-AUTH-RBAC-SPEC.md), which remains the source of
truth for *requirements* (the 8 access roles, the permission matrix, the 4-field
account-creation form, Chat's uniform-access rule, etc.). This document does not
restate every requirement — it sequences the work, names the exact files each phase
touches, and re-verifies the spec's "as found" claims against the code as it exists
today (2026-09-23), since the spec itself warns its own snapshot will age.

**Notation:** **VERIFIED** = re-checked directly against the current codebase while
writing this plan (not copied from the spec). **PROPOSED** = a recommendation for
implementation. **OPEN QUESTION** = points back to spec §20; not decided here.

---

## 0. Re-verification of the spec against current code

Every "CONFIRMED" claim in spec §2 was re-checked directly (not assumed) while
producing this plan:

| Spec claim | Result |
|---|---|
| Spring has no `spring-boot-starter-security`, no password hashing | **VERIFIED** — `backend/pom.xml` has no security or hashing dependency |
| `Person` entity has no password field | **VERIFIED** — [`Person.java`](../../backend/src/main/java/com/workos/workos_backend/entity/Person.java) has `id/name/email/initials/role/chipClass` only |
| `ActingPersonResolver` has exactly one impl, fixed to one id, `local-dev`-only | **VERIFIED** — [`LocalDevActingPersonResolver.java`](../../backend/src/main/java/com/workos/workos_backend/actor/LocalDevActingPersonResolver.java) |
| `CorsConfig` has no `allowCredentials(true)` | **VERIFIED** |
| `role` column is a free-text job title, `/admin`'s Role field is free text | **VERIFIED** — [`admin/page.tsx:284-287`](../../workos-app/src/app/admin/page.tsx) is a plain `<input>`, not a `<select>` |
| Two disconnected demo identity systems (workspace-user + admin) | **VERIFIED** — [`auth.tsx`](../../workos-app/src/lib/auth.tsx), [`admin-auth.tsx`](../../workos-app/src/lib/admin-auth.tsx), [`session.ts`](../../workos-app/src/lib/server/session.ts), [`admin-credentials.ts`](../../workos-app/src/lib/server/admin-credentials.ts) all match the spec's description exactly |
| `proxy.ts` checks cookie presence only | **VERIFIED** — [`proxy.ts`](../../workos-app/src/proxy.ts) |
| `TimeEntryService.listEntries` has no role scoping | **VERIFIED** |
| `TaskService.updateTask` gates `ownerId`/`assigneeIds` on owner-only, not role | **VERIFIED** — [`TaskService.java:123-166`](../../backend/src/main/java/com/workos/workos_backend/service/TaskService.java) |
| `WorkspaceService.addMember`/`removeMember` gate on `actor.id == ownerId` only | **VERIFIED** |
| Members page scopes to `workspaces.filter(w => w.ownerId === user?.id)` | **VERIFIED** — [`members/page.tsx:28`](<../../workos-app/src/app/(app)/members/page.tsx>) |
| Chat's Next.js routes resolve the sender via `requireSessionPersonId()`, never trust body | **VERIFIED** — [`chat/messages/route.ts`](../../workos-app/src/app/api/chat/messages/route.ts) |

**Everything above still holds.** The spec is accurate. One material fact has changed
or was not visible to the spec's own "as found" section, documented next.

### 0.1 New finding not reflected in the spec: a dormant, fully-built Chat backend already exists in Spring

The spec's §2.1 table and §4.1/§7.5 describe Chat's backend as "the Next.js route
handlers... not Spring" and treat any future Spring Chat implementation as a
not-yet-started possibility ("if Chat's backend later moves to Spring"). That framing
is correct for *what the frontend currently calls* — **VERIFIED**:
[`api-client.ts:211-232`](../../workos-app/src/lib/api-client.ts) still calls the
relative Next.js `request()` path for `fetchMessages`/`sendMessageRequest`/the SSE
`EventSource`, not `springRequest()`.

But it is not correct that Spring has no Chat implementation. **VERIFIED**, all present
and tested today:

- `backend/.../entity/ChatMessage.java`
- `backend/.../repository/ChatMessageRepository.java`
- `backend/.../service/ChatService.java` + `ChatMessageBroadcaster.java` (an
  `SseEmitter`-based broadcaster — the Spring analogue of the frontend's own
  in-memory `chat-events.ts` `EventEmitter`)
- `backend/.../controller/ChatController.java` — implements
  `GET/POST /api/chat/messages` and `GET /api/chat/stream`, matching BACKEND.md's
  contract exactly
- `V4__create_chat_messages_table.sql` (Flyway)
- Full test coverage: `ChatControllerTest`, `ChatServiceTest`,
  `ChatMessageBroadcasterTest`

This module resolves its actor via the same `ActingPersonResolver.currentPersonId()`
as every other Spring controller — i.e. it has **the same fixed-dev-actor limitation**
as Workspaces/Boards/Tasks, not real per-user identity, and (outside `local-dev`) no
resolver bean at all.

**Why this matters for the plan:** Phase 6 (Chat) as scoped by the spec assumed
"preserve the Next.js Chat implementation; do not move it to Spring unless the spec
explicitly requires it" (§4.1 item 3, §7.5). That instruction still stands — this plan
does **not** propose switching the frontend to the Spring Chat module, because the
spec's binding requirement is preservation, not a rewrite, and this document is not
authorized to make that call unilaterally. But the dormant module changes the
risk/effort picture for whoever eventually reads this:

- It is **not** evidence that Chat has already been migrated — it is untested-in-
  production, unwired, parallel code that nothing currently calls.
- It carries the **same identity-resolution gap** as the rest of Spring (fixed actor,
  no real auth) and would need the exact same `SessionActingPersonResolver` swap
  (§6.3 of the spec) as Workspaces/Boards/Tasks/Time Entries if it were ever adopted.
- Its existence is a **decision point worth surfacing to the product owner alongside
  spec §20**, not one this plan resolves: keep Chat on Next.js (per the spec's current
  binding instruction, this plan's default) and leave the Spring module dormant/
  untouched, or adopt the Spring module in Phase 6 as an alternative to porting the
  Next.js repository. Phase 6 below is written for the spec's binding default
  (Next.js stays authoritative); §0.1 is flagged so this default is a conscious choice,
  not an oversight.

### 0.2 Secondary finding: a Spring `Reset` endpoint exists, not in the spec's endpoint inventory

`ResetController` (`@Profile("local-dev")`) + `ResetService` already implement
`POST /api/reset` on Spring, restoring Workspaces/Boards/Tasks and re-seeding People
via `LocalDevPeopleSeeder.seedMissingPeople()`. The spec's §14 endpoint table doesn't
mention it (the frontend still calls the Next.js `/api/reset` stub — **VERIFIED**,
`api-client.ts` has no `springRequest` call for reset). Low relevance to auth
mechanics, but directly relevant to Phase 1/3 below: **once `access_role`/
`password_hash` columns exist, this Spring reset path re-seeds People rows via
`seedMissingPeople()`'s insert-if-missing logic, which will leave any existing row's
`access_role`/`password_hash` untouched** (matching its documented "never overwrites"
behavior) — good, this means Reset cannot accidentally wipe an assigned access role or
password, but it also means Reset must never be extended to *insert* a default
`access_role` for missing rows, or it would silently violate spec §13.2's "never
silently grant a role" constraint. Called out again in Phase 1's risk section.

---

## 1. Authentication-Relevant Implementation Audit

Per the task's audit requirement — every authentication-related implementation
currently in the project, its behavior, its concern, and its disposition.

| # | File(s) | Current behavior | Concern | Disposition | Migration-breaking dependencies |
|---|---|---|---|---|---|
| 1 | [`src/lib/auth.tsx`](../../workos-app/src/lib/auth.tsx) | React context; `fetchMe`/`loginRequest`/`logoutRequest` via `api-client.ts` | None architecturally — shape is sound | **Retain shape**, swap the 3 functions' transport (spec §6.2) | Every `useAuth()` caller (TopBar, gated pages) — must see no shape change |
| 2 | [`src/lib/server/session.ts`](../../workos-app/src/lib/server/session.ts) | In-memory `Map<token, personId>`, wiped on restart, no password check | No persistence, no revocation, no real credential check | **Remove entirely** | `require-session.ts`, `/api/auth/*` routes |
| 3 | [`src/lib/server/require-session.ts`](../../workos-app/src/lib/server/require-session.ts) | Cookie → `session.ts` lookup → personId or null | Fine as a *pattern*; backed by the in-memory map | **Replace impl**, same signature if Next.js keeps a thin auth proxy (open question, spec §20.2) | Every Chat route handler calls this directly |
| 4 | [`src/lib/server/demo-credentials.ts`](../../workos-app/src/lib/server/demo-credentials.ts) | One shared plaintext password (`DEMO_PASSWORD`) for all seed accounts | Shared password = no real authentication | **Remove entirely** | `/api/auth/login` route, `/login` page's hint text |
| 5 | [`src/lib/server/people-repository.ts`](../../workos-app/src/lib/server/people-repository.ts) `verifyPersonCredentials` | Plaintext `===` comparison against a per-record password stored in-memory | No hashing, no persistence | **Remove entirely** (whole repository retires per spec §6.2) | `/api/auth/login`, `/api/admin/people*` |
| 6 | [`src/proxy.ts`](../../workos-app/src/proxy.ts) | Checks `SESSION_COOKIE`/`ADMIN_SESSION_COOKIE` *presence* only, redirects/401s | Correct *pattern*; not itself a security boundary (by design, real check is per-request) | **Retain pattern**, drop the admin branches in Phase 7, update cookie name if it changes | Every page/route depends on its matcher regex staying correct |
| 7 | [`src/lib/admin-auth.tsx`](../../workos-app/src/lib/admin-auth.tsx) | Separate React context, separate cookie/session, `{email}`-only identity (not tied to any `Person`) | Entirely parallel, unrelated identity system | **Remove entirely** (spec §5.3) | `/admin/*` pages, `AdminAuthProvider` in root layout |
| 8 | [`src/lib/server/admin-session.ts`](../../workos-app/src/lib/server/admin-session.ts), [`admin-session-cookie.ts`](../../workos-app/src/lib/server/admin-session-cookie.ts), [`require-admin-session.ts`](../../workos-app/src/lib/server/require-admin-session.ts) | Admin-only session map/cookie/guard, same shape as the workspace-user trio | Same class of concern as #2/#3 | **Remove entirely** | `/api/admin/*` routes |
| 9 | [`src/lib/server/admin-credentials.ts`](../../workos-app/src/lib/server/admin-credentials.ts) | **Hardcoded credential pair**, `admin@workos.dev` / a fixed plaintext password, not tied to any `Person` | Hardcoded credential in source — the single highest-severity item in this audit | **Remove entirely** | `/api/admin/auth/*` routes |
| 10 | `src/app/api/admin/**` (4 route files) | Next.js routes backing the admin console, writing only to the in-memory `people-repository.ts` (never MySQL) | Parallel account-management surface with no relation to real `Person`/RBAC | **Remove entirely** (spec §5.3), replaced by the CEO-gated `/api/accounts` Spring endpoint | `/admin` page UI |
| 11 | `backend/.../actor/ActingPersonResolver.java` (interface) + `LocalDevActingPersonResolver.java` (impl) | Interface fine; impl returns one fixed, config-driven id, `local-dev`-only, explicitly documented as never trusting client input | None with the interface; the impl is *correct for local dev*, but there is **no non-local-dev implementation at all today** — every Spring endpoint is unauthenticated in any other profile | **Retain** the interface unchanged; **retain** `LocalDevActingPersonResolver` for local dev only; **add** a new `SessionActingPersonResolver` for all other profiles (Phase 2) | Every existing Spring controller depends on this interface's contract not changing |
| 12 | `backend/.../dev/LocalDevPeopleSeeder.java` | Idempotent insert-if-missing seeding of 6 demo `Person` rows, `local-dev`-only | None on its own; becomes relevant once `access_role`/`password_hash` exist (§0.2 above) — must not start defaulting a role | **Retain**, extend carefully (Phase 1/3) — never auto-assign `access_role` | `ResetService.reset()` calls its `seedMissingPeople()` |
| 13 | Every Spring controller lacking any auth check (`WorkspaceController`, `TaskController`, `BoardController`, `TimeEntryController`, `PersonController`, `ChatController`) | No filter chain, no annotation-based guard — any caller with network access to port 8080 can call any endpoint | **Every Spring endpoint is unauthenticated today outside relying on `local-dev`'s fixed actor** | **Add** Spring Security filter chain (Phase 2), then per-endpoint role checks (Phase 4) | All existing controller tests assume no security context — will need a security-aware test configuration (Phase 2/8) |
| 14 | `GET /api/people` (`PersonController`) | No auth at all, **by design**, per its own doc comment and BACKEND.md | Deliberate exception, but written before real accounts/passwords existed | **Open question** (spec §20.3) — this plan defaults to *leaving it open* unless told otherwise, since narrowing it is a behavior change beyond what's confirmed | Nothing currently depends on it being open other than the (currently working) People list in every page |
| 15 | `Person` entity/table — no password column | N/A (doesn't exist yet) | Password storage is the actual root cause of items 4/5/9 above | **Add** `password_hash` (Phase 1) | — |

---

## 2. Person / Role Data Model Review

**Current state (VERIFIED today, re-checked against `data.ts`, `LocalDevPeopleSeeder`,
and `Person.java`/`V1` migration):**

- 6 demo people exist in three independent places: `workos-app/src/lib/data.ts`
  (Next.js in-memory seed), `LocalDevPeopleSeeder.SEED_PEOPLE` (Spring/MySQL,
  `local-dev` only), and whatever has been created through `/admin` (Next.js
  in-memory only, never MySQL).
- Every one of these 6 has a `role` value that is a job title: `"Senior PM"`,
  `"Lead Architect"`, `"Product Ops"`, `"Fullstack Dev"`, `"Legal & Program Ops"`,
  `"Security & QA"`. **None of these map cleanly to any of the 8 access roles** — e.g.
  nothing here is obviously "IT" or "Marketing" — confirming spec §13.2's instruction
  not to auto-map.
- No `password` field exists anywhere in the Spring/MySQL schema. The Next.js
  in-memory store does have a `password` field per `StoredPerson` (used only by the
  demo/admin flows), but it never reaches MySQL.

**Safe migration/backfill strategy (PROPOSED, building on spec §13.3, made concrete
for Phase 1):**

1. `V5` migration adds `access_role VARCHAR(32) NULL` and `password_hash VARCHAR(255)
   NULL` to `people` — nullable, no default, so **every existing row (all 6 demo
   people, plus any real rows created later without these fields set) starts with
   `access_role = NULL`.** A row with `access_role IS NULL` is unauthenticatable by
   construction (Phase 2's login flow rejects `NULL` role and/or `NULL` password_hash
   with the same "invalid credentials" response as a wrong password — never a
   distinguishable error, to avoid leaking which emails exist).
2. `CeoAccountSeeder` (Phase 3, all profiles) checks `existsByAccessRole("CEO")` —
   **not** a fixed id — before inserting, so it is safe to run every startup and
   impossible to duplicate even if the configured CEO email/id changes between runs.
   This directly satisfies "avoid duplicate CEO accounts when the seeder runs more
   than once," including the edge case of re-running against a database that already
   has a CEO seeded under a *different* id or email than the current environment
   variables specify — the seeder must no-op in that case too, not attempt a second
   insert.
3. `LocalDevPeopleSeeder.seedMissingPeople()` is **not** modified to assign a
   role/password to the 6 demo rows — per §0.2 above, its insert-if-missing contract
   must continue to leave `access_role`/`password_hash` alone for existing rows,
   satisfying "avoids accidentally granting CEO or HR privileges."
4. The 6 demo people are **carried forward as-is** (rows exist, `access_role IS NULL`,
   `password_hash IS NULL`) rather than deleted, preserving their referential
   integrity as task owners/assignees/workspace members/time-entry owners/chat
   authors — deleting them would cascade-orphan a large amount of existing demo data.
   They simply cannot log in until a CEO explicitly assigns them a role and a
   password-set path (exact mechanism is spec §20.6's open question — this plan
   defaults to "carry forward, cannot log in until assigned," which is explicitly
   spec-endorsed as one of two equally valid answers, and is the only one of the two
   compatible with "preserve existing people and related records").
5. `Person.role` (job title) is untouched by any of this — a second, independent
   `accessRole` field is added to the Spring `Person` entity and the frontend `Person`
   type, never replacing or repurposing `role`.

---

## 3. Authorization Requirements vs. Current Endpoints

See §7.2 of the spec for the authoritative table (re-verified, §0 above — no changes).
This plan does not duplicate it; §5 below (phase table) states, per phase, exactly
which of those rows it closes.

**One clarification this plan adds:** the spec correctly notes account creation
(`POST /api/accounts`) has no endpoint yet. Because that endpoint's *only*
authorization rule is "caller must be CEO," and CEO-checking requires the same
`accessRole`-aware authorization primitive that Phase 4 builds for every other
role-gated endpoint, **Phase 3 (CEO seeder + account creation) has a soft dependency
on a minimal slice of Phase 4** (see §5, Phase 3's notes) — not the full role matrix,
just "reject if `actor.accessRole != CEO`." This plan calls that out explicitly rather
than have an implementer discover the circular dependency mid-phase.

---

## 4. Chat Authentication — Detailed Findings

Per the task's explicit instruction to review this carefully:

- **Sender resolution today (VERIFIED):** `chat/messages/route.ts` and
  `chat/stream/route.ts` both call `requireSessionPersonId()` first, before touching
  the request body, and use its return value as the only source of the acting
  person id. Neither route reads a `personId`/`senderId` from the body/query for
  identity purposes.
- **`requireSessionPersonId()` mechanics (VERIFIED):** reads the `workos_session`
  httpOnly cookie via `next/headers`, looks it up in the in-memory `sessions` `Map`
  in `session.ts`, returns the mapped `personId` or `null`. No password/role check —
  purely "is there a valid token."
- **Can a client-supplied `personId` be trusted? No — VERIFIED not trusted anywhere**
  in the Chat route handlers today. This must be preserved exactly through the
  migration (spec §4.1 item 6, §7.5 item 2).
- **Existing Chat data preservation:** Chat messages live in the Next.js in-memory
  `chat-repository.ts` today (wiped on restart, same as every other Next.js stub) —
  there is **no durable Chat data to migrate** from Next.js today; "preserve existing
  messages" in practice means "don't break the `ChatMessage` shape/contract" for
  whatever real persistence Chat ends up with, not "migrate rows out of the in-memory
  map," since restarting the dev server already loses that data today regardless of
  this project.
- **How to integrate with unified auth (PROPOSED, Phase 6):** swap
  `requireSessionPersonId()`'s *implementation* to validate against the new Spring
  session mechanism (exact call shape depends on spec §20.2's open question — either
  a direct Spring `GET /api/auth/me` call from the Next.js route handler, or a shared
  cookie Spring itself can validate) while keeping the function's signature
  (`Promise<string | null>`) and every call site identical. Per §0.1 above, this plan
  does **not** propose switching Chat's data path to the dormant Spring
  `ChatController` — only its identity resolution changes.

---

## 5. Phased Implementation Plan

**Reordering rationale vs. the task's suggested phase list:** the suggested order is
sound and this plan keeps it, with one adjustment made explicit rather than silent:
Phase 3 ("CEO seeder and employee account creation") needs a minimal CEO-only guard
that logically belongs to Phase 4's authorization work. Rather than move account
creation later (which would delay the CEO's ability to create any other account, the
critical unblock for all manual testing after Phase 2), Phase 3 builds that one guard
itself and Phase 4 reuses/extends it for the full 8-role matrix. This is called out
in Phase 3 and Phase 4's "Dependencies" fields below so it isn't missed.

---

### Phase 1 — Database and Access-Role Foundation

**Objective:** Add the schema needed for auth and roles, with zero behavior change.

**Files created:**
- `backend/src/main/resources/db/migration/V5__add_access_role_and_password_to_people.sql`
  — adds `access_role VARCHAR(32) NULL`, `password_hash VARCHAR(255) NULL` to `people`.
- `backend/src/main/resources/db/migration/V6__create_sessions_table.sql` — **only if**
  DB-backed sessions are the chosen strategy (spec §6.3's open fork; this plan's
  PROPOSED default, for the "revocable" requirement).

**Files modified:**
- `backend/.../entity/Person.java` — add `accessRole` (nullable) and `passwordHash`
  fields/getters/setters. **`passwordHash` must never be exposed by
  `PersonResponse::from`** — verify `PersonResponse.java` doesn't add it by accident.
- `backend/.../dto/PersonResponse.java` — add `accessRole` to the response (never
  `passwordHash`).

**Backend changes:** Schema only; no controller/service behavior changes yet.

**Frontend changes:** None yet.

**Database/migration changes:** `V5` (and `V6` if DB sessions chosen), additive only,
nullable columns, no backfill, no default value — per §2 above.

**Dependencies:** None — first phase.

**Tests required:**
- Flyway migration applies cleanly against a fresh DB and against the existing
  MySQL dev DB with the 6 seeded demo rows (verify per
  [[feedback-mysql-verification-credentials]] — ask the user for `DB_PASSWORD`
  before running this against real MySQL; do not guess or assume it's in the shell
  environment).
- `PersonRepositoryTest`/`PersonControllerTest` extended to assert
  `passwordHash` never appears in a `PersonResponse` JSON body.

**Acceptance criteria:** Migration applies with zero errors on a clean DB and on the
existing dev DB; all 6 demo rows have `access_role = NULL`, `password_hash = NULL`
afterward; existing 165/165 backend test suite still passes unmodified.

**Risks / rollback:** Additive nullable columns are low-risk and reversible with a
`V7` drop migration if needed. The one real risk is a future implementer "helpfully"
adding a `NOT NULL DEFAULT` or a backfill in this same migration — explicitly
prohibited by spec §13.2 and §2 above.

---

### Phase 2 — Spring Security and Backend Session Authentication

**Objective:** Give Spring a real authentication mechanism, without yet touching any
authorization/role logic.

**Files created:**
- `backend/.../config/SecurityConfig.java` — Spring Security filter chain, password
  encoder bean (BCrypt).
- `backend/.../auth/SessionActingPersonResolver.java` — replaces
  `LocalDevActingPersonResolver` outside `local-dev`, resolves identity from the
  verified session, never from client input (preserving `ActingPersonResolver`'s
  existing non-negotiable Javadoc contract).
- `backend/.../controller/AuthController.java` — `POST /api/auth/login`,
  `POST /api/auth/logout`, `GET /api/auth/me`.
- `backend/.../service/AuthService.java` / session-issuing service (shape depends on
  DB-sessions-vs-JWT choice, spec §6.3).
- `backend/.../entity/Session.java` + `SessionRepository.java` (if DB-backed sessions).

**Files modified:**
- `backend/pom.xml` — add `spring-boot-starter-security`.
- `backend/.../config/CorsConfig.java` — add `.allowCredentials(true)` (spec §2.4,
  §19's flagged cross-origin risk).
- `application.properties` / `application-local-dev.properties` — session cookie
  name/attributes, new env vars documented (no committed defaults for secrets).

**Backend changes:** New auth endpoints; `LocalDevActingPersonResolver` unchanged and
still `local-dev`-only; every *existing* controller (`WorkspaceController` etc.)
requires **no code change** in this phase — they still call
`actingPersonResolver.currentPersonId()`, now backed by the new resolver outside
`local-dev`.

**Frontend changes:** None yet — `api-client.ts`'s auth functions still point at
Next.js in this phase (deferred to Phase 5), so this phase is Spring-only and
independently testable via direct HTTP calls.

**Database/migration changes:** None beyond Phase 1 (uses `V5`/`V6`).

**Dependencies:** Phase 1 (`password_hash` column, `sessions` table if DB-backed).

**Tests required:**
- New `AuthControllerTest`/`AuthServiceTest`: login success/failure (wrong password,
  unknown email, `access_role IS NULL` account), logout, `me` with/without a valid
  session.
- Regression: full existing 165-test suite still green with the security filter
  chain active — every existing controller test will need a security-aware test
  configuration (e.g. a test-only resolver or `@WithMockUser`-equivalent) added here,
  not deferred to Phase 8, or Phase 3/4 will be built against a broken test harness.
- Cross-origin cookie check (manual or Playwright): confirm a browser at the Next.js
  origin actually receives and resends the Spring session cookie once
  `credentials: "include"` is added on the frontend (Phase 5) — this is the concrete
  risk flagged in spec §19.

**Acceptance criteria:** Direct `curl`/Postman login against Spring returns a session
cookie; `GET /api/auth/me` with that cookie returns the person; without it, 401;
existing test suite (165 tests) passes with the security chain enabled.

**Risks / rollback:** Adding a global security filter chain is the single change most
likely to break existing controller tests wholesale if done carelessly (e.g. Spring
Security's default "deny everything" posture applied before per-endpoint rules exist)
— mitigate by explicitly permitting all existing endpoints in this phase (Phase 4 adds
the real restrictions), so Phase 2 is additive-only in practice despite touching a
security-sensitive area. Rollback = revert the Maven dependency and config classes;
no data migration to undo.

---

### Phase 3 — CEO Seeder and Employee Account Creation

**Objective:** Get one real, logged-in-capable CEO account into every environment,
and let that CEO create the other 7 roles' accounts.

**Files created:**
- `backend/.../dev/CeoAccountSeeder.java` (`ApplicationRunner`, **registered in every
  profile**, not `local-dev`-only) — reads `APP_INITIAL_CEO_EMAIL`/
  `APP_INITIAL_CEO_PASSWORD` from environment/config only, no-ops if a `CEO` already
  exists (checked by `accessRole`, not a fixed id — see §2 above), fails startup
  loudly if no CEO exists and the env vars are absent.
- `backend/.../controller/AccountController.java` — `POST /api/accounts` (CEO-only),
  `GET /api/accounts` (CEO-only).
- `backend/.../service/AccountService.java`.
- `backend/.../dto/CreateAccountRequest.java` — exactly `{name, email, password,
  accessRole}`, matching spec §5.2's 4-field form, no Department/Job Title field.
- Minimal CEO-only guard (see rationale above) — e.g. a small
  `RequireAccessRole`/`CurrentPerson` helper that Phase 4 later extends to the full
  8-role matrix rather than replaces.

**Files modified:** None outside the new files above.

**Backend changes:** New account-management endpoints; no changes to
Workspace/Board/Task/TimeEntry authorization yet (that's Phase 4).

**Frontend changes:** None yet (the new CEO account-management UI is Phase 6 per the
task's suggested ordering — see note below on why this plan keeps that split).

**Database/migration changes:** None beyond Phase 1.

**Dependencies:** Phase 1 (schema), Phase 2 (auth/session machinery — the CEO-only
guard needs a real authenticated identity to check a role against). **Soft
dependency on a slice of Phase 4** — see reordering rationale above.

**Tests required:**
- `CeoAccountSeederTest`: no-ops on second run, no-ops even if the existing CEO's
  email differs from the current env vars, fails startup with clear message if no
  CEO exists and env vars are absent.
- `AccountControllerTest`/`AccountServiceTest`: only a CEO can call
  `POST/GET /api/accounts`; non-CEO gets 403; the form accepts exactly 4 fields;
  `accessRole` must be one of the 8 (reject anything else, including attempts to
  invent a 9th role).

**Acceptance criteria:** A brand-new deployment (empty MySQL, no `local-dev` profile)
seeds exactly one CEO from environment-provided credentials on first boot; a restart
creates no duplicate; that CEO can create an account for each of the other 7 roles
through the new endpoint; a non-CEO account cannot call it.

**Risks / rollback:** Losing `APP_INITIAL_CEO_PASSWORD` after the CEO's password has
been rotated away from the seeded value would be a lockout risk — out of scope to
solve here, but worth flagging for the operational runbook this plan doesn't own.
Rollback = the seeder and account endpoints are net-new; disabling them (e.g. profile
exclusion) leaves the rest of the system unaffected since nothing else depends on them
yet in this phase.

---

### Phase 4 — Backend Authorization and RBAC Enforcement

**Objective:** Enforce the full role-permission matrix (spec §4/§9/§10/§11/§12)
server-side, closing every gap in spec §7.2's table.

**Files modified:**
- `backend/.../service/TimeEntryService.java` — `listEntries` takes the actor's
  resolved `Person`; non-CEO/non-HR always scoped to own id regardless of
  `personIdFilter` (spec §9).
- `backend/.../controller/TimeEntryController.java` — pass the resolved actor through.
- `backend/.../service/TaskService.java` — `updateTask`/`createTask`: touching
  `ownerId`/`assigneeIds` additionally requires `actor.accessRole ∈ {CEO, Operation
  Manager}`, on top of (not instead of) the existing owner-only gate for other core
  fields (spec §11). **This is the highest-risk change in this phase** — see spec §19
  and this plan's Phase 8 test requirements.
- `backend/.../service/WorkspaceService.java` — `addMember`/`removeMember`: CEO
  bypasses ownership (purely additive `OR accessRole == CEO`); Operation Manager scope
  is an **OPEN QUESTION** (spec §20.5) — do not implement a guessed shape (e.g. a new
  `workspace_managers` table) without that decision first. This plan's default: ship
  the CEO-bypass now, leave Operation Manager's broader scope as a follow-up once
  §20.5 resolves, rather than block this entire phase on one unresolved sub-rule.
- Extend the Phase 3 CEO-only guard into a general `@RequireAccessRole(...)`-style
  check (or equivalent) usable by every controller above.

**Files created:** None expected beyond what Phase 3 already added, unless the
Operation-Manager-scope decision (above) requires new schema/entities later.

**Backend changes:** As listed above — this is the phase with the most existing-code
behavior changes, not additive-only.

**Frontend changes:** None yet (Phase 5/6/7 per the task's ordering) — but see risk
note below.

**Database/migration changes:** None beyond Phase 1, unless Operation-Manager scope
resolves to something needing new schema (deferred).

**Dependencies:** Phase 1, 2, 3 (needs `accessRole` on the authenticated identity and
at least the CEO-guard primitive Phase 3 built).

**Tests required:**
- Extend **existing** `TimeEntryServiceTest`/`TaskServiceTest`/`WorkspaceServiceTest`
  (per spec §19's explicit recommendation) rather than only adding new ones — the
  existing suites are the regression net for ownership/membership/clock-conflict
  behavior that must not change.
- New role-matrix tests: every row of spec §9/§10/§11/§12's tables, both "allowed"
  and "denied" directions, via direct service/controller calls (not just UI-reachable
  paths) — mirroring the brief's "manipulating personId/query params/URLs" concern.
- Explicit regression test: a non-CEO/non-OM task **owner** who could reassign their
  own task before this phase can no longer do so after it (spec §11's flagged
  behavior narrowing) — this is exactly the kind of change that "looks done" while
  quietly breaking a workflow if untested.

**Acceptance criteria:** Matches spec §18's acceptance criteria for time-entries,
task-assignment, and workspace-membership rows exactly (direct-API-call verification,
not UI-only).

**Risks / rollback:** The task-assignment narrowing (§11) is a genuine behavior
regression for any existing non-privileged task owner relying on self-reassignment —
communicate this explicitly before shipping, per spec §19. Rollback for this phase is
harder than Phases 1-3 since it changes already-relied-upon behavior; consider a
feature flag or staged rollout (e.g. log-only "would have blocked" mode before
enforcing) if the product owner wants a safety net — **not proposed as required, just
as a mitigation option** given the risk level.

---

### Phase 5 — Unified Next.js Frontend Authentication

**Objective:** Point the frontend's auth surface at Spring; retire the Next.js
in-memory session store.

**Files modified:**
- `src/lib/api-client.ts` — `loginRequest`/`logoutRequest`/`fetchMe` switch from
  relative `request()` to `springRequest()` (same pattern already used for
  People/Workspaces/Boards/Tasks/Time-clock); add `credentials: "include"` to every
  `fetch()` call that needs the cross-origin cookie (spec §2.4/§19).
- `src/lib/auth.tsx` — no shape change expected (per spec §6.2's table); verify after
  the swap.
- `src/proxy.ts` — update to whatever cookie name/shape the new Spring session issues;
  keep the presence-check pattern.
- `src/lib/permissions.ts` / `useAuth()`'s `Person` type — add `accessRole` alongside
  the existing job-title `role` (spec §8).
- `src/components/ui/OwnerPicker.tsx` — `disabled` condition changes from "not the
  task's owner" to "not CEO and not Operation Manager," matching Phase 4's new
  backend rule.
- `src/app/(app)/members/page.tsx` — extend beyond
  `workspaces.filter(w => w.ownerId === user?.id)` so CEO sees every workspace and
  Operation Manager sees their authorized-to-manage set (pending §20.5).
- `src/app/(app)/dashboards/page.tsx` (Reports) and `src/app/(app)/time-clock/page.tsx`
  — view scoping per accessRole, as a UX layer on top of Phase 4's server-side
  enforcement (never a substitute for it).

**Files removed:** `src/lib/server/session.ts`, `src/lib/server/require-session.ts`'s
old implementation (replaced, not deleted outright if Chat still needs the function
signature — see Phase 6), `src/lib/server/demo-credentials.ts`,
`src/lib/server/people-repository.ts`, the Next.js `/api/auth/*` route handlers
(retired or converted to thin proxies — spec §20.2's open question decides which).

**Backend changes:** None new — consumes Phase 2/3/4's endpoints.

**Database/migration changes:** None.

**Dependencies:** Phase 2 (Spring auth endpoints must exist), Phase 4 (for
`accessRole`-driven UI to have something real to reflect).

**Tests required:**
- Playwright/manual: full login → authenticated page load → logout cycle against
  real Spring sessions, cross-origin cookie actually observed in the browser (not
  just assumed from code reading — this is the exact risk spec §19 flags as "looks
  fine in same-origin local dev, breaks for real cross-origin").
- Regression: Boards/Tasks/Workspaces/Time-clock pages (already Spring-integrated
  per prior work) continue to function once the *identity* behind those calls is a
  real session instead of the `local-dev` fixed actor.

**Acceptance criteria:** A user can log in with a real per-account password, see only
what their `accessRole` permits (as a UX layer), and a stale/expired session correctly
bounces to `/login` (existing `handleStaleSession()` pattern, re-pointed at the new
auth surface).

**Risks / rollback:** This phase is where the mid-flight migration (spec §2.1, §19)
becomes fully real — re-verify `api-client.ts`'s current state immediately before
starting, since Time-clock's integration already landed since the spec was written
and this plan's own file list could itself drift. Rollback = revert `api-client.ts`
back to the Next.js auth routes; keep them alive (not yet deleted) until this phase is
verified in a real deployment, then delete in Phase 7.

---

### Phase 6 — Chat Authentication Integration

**Objective:** Move Chat's *identity source* to the unified session, preserving every
existing Chat behavior and its uniform, non-role-gated access (spec §4.1, §7.5).

**Files modified:**
- `src/lib/server/require-session.ts` (or its Phase-5 replacement) — Chat's route
  handlers keep calling this exact function; only its internal implementation
  changes to validate against the Spring-backed session instead of the retired
  in-memory `Map`.
- `src/app/api/chat/messages/route.ts`, `src/app/api/chat/stream/route.ts` — **no
  logic change expected** beyond whatever `require-session.ts`'s new implementation
  requires them to import; the `canAccessConversation`/`createMessage`/`listMessages`
  calls and the "never trust a client-supplied personId" behavior are preserved
  exactly.
- `src/lib/server/chat-repository.ts`, `src/lib/server/chat-events.ts` — unmodified;
  Chat's data layer and SSE fan-out are unaffected by the identity-source change.

**Files explicitly NOT touched, per §0.1 above:** the dormant Spring
`ChatController`/`ChatService`/`ChatMessageBroadcaster`/`ChatMessage` entity — this
plan's default is to leave that module dormant, matching the spec's binding
"preserve Chat as-is" instruction. **Flag to the product owner:** if a future decision
adopts the Spring Chat module instead, that decision supersedes this phase's file list
and would additionally need `SessionActingPersonResolver` wired into `ChatController`
(it currently uses the same fixed-dev-actor resolver as everything else in Spring).

**Backend changes:** None (Chat's backend stays Next.js, per the binding requirement).

**Frontend changes:** None to Chat's UI components — only the identity plumbing
underneath, invisible to `useAuth()` callers.

**Database/migration changes:** None.

**Dependencies:** Phase 5 (the unified session must exist for Chat to validate against
it).

**Tests required:**
- All 8 access roles individually verified able to send/receive Chat messages (spec
  §18's explicit "not just spot-checked" requirement) — CEO, HR, IT, Graphics
  Designer, Marketing, Operation Manager, Sales Assistant, Sales Manager.
- Every Chat endpoint rejects an unauthenticated request with 401.
- Direct-API-call spoofing attempt: post a message with a client-supplied `personId`/
  `senderId` different from the authenticated session's — must be ignored/rejected,
  attributed to the real session identity only.
- Regression: existing Chat SSE stream (`chat-events.ts`'s `EventEmitter` fan-out)
  still delivers live messages after the identity-source swap.
- Explicit negative test: a newly-added `accessRole` must **not** appear anywhere in
  the Chat authorization path — assert Chat access is unaffected by role value
  (spec §4.1 item 4, §7.5 item 4).

**Acceptance criteria:** Matches spec §18's Chat-specific criteria exactly (all 8
roles verified individually, 401 on unauthenticated, no sender spoofing, no
role-based restriction, existing conversations/messages unregressed).

**Risks / rollback:** Per spec §19, the real risk here isn't a missing check — Chat's
current sender-identity handling is already correct — it's an implementer adding a
role gate that was never asked for. Explicitly test *against* that regression (a role
gate silently appearing) as part of this phase's test suite, not just for the
presence of the required behavior. Rollback = revert `require-session.ts`'s
implementation to the Phase-5-era version; Chat's route handlers need no changes to
roll back since they were never modified.

---

### Phase 7 — Removal of Legacy/Demo Authentication

**Objective:** Delete every demo/parallel identity system now that the unified system
is live and verified (spec §5.3, §6.2's disposition table).

**Files removed:**
- `src/lib/admin-auth.tsx`, `src/lib/server/admin-session.ts`,
  `src/lib/server/admin-session-cookie.ts`, `src/lib/server/require-admin-session.ts`,
  `src/lib/server/admin-credentials.ts`.
- `src/app/admin/page.tsx`, `src/app/admin/login/page.tsx` (if present as a separate
  file — verify exact path at implementation time).
- `src/app/api/admin/auth/login/route.ts`, `.../logout/route.ts`, `.../me/route.ts`,
  `src/app/api/admin/people/route.ts`, `src/app/api/admin/people/[personId]/route.ts`.
- `src/lib/server/demo-credentials.ts`, `src/lib/server/people-repository.ts` (if not
  already removed in Phase 5).
- `src/proxy.ts`'s `ADMIN_SESSION_COOKIE`/admin-branch logic (lines checking
  `pathname.startsWith("/api/admin")` and the `/admin` redirect branch) — the file
  itself is retained, just narrowed.

**Files added:**
- The new CEO-gated account-management page (e.g. `src/app/(app)/accounts/page.tsx`
  or reusing `/admin` as a normal authenticated route per spec §5.3 option 1) — the
  4-field form (Name/Email/Password/Role-dropdown) calling Phase 3's
  `POST/GET /api/accounts`.

**Backend changes:** None new — consumes Phase 3's endpoints.

**Database/migration changes:** None. Per spec §5.3 item 4, the admin console never
wrote to MySQL, so there is no admin-console data to carry forward or clean up.

**Dependencies:** Phase 3 (account endpoints must exist and be verified), Phase 5
(unified session must be live so the new account-management page can be gated by it).

**Tests required:**
- Confirm `/admin`, `/admin/login`, and every `/api/admin/*` path now 404 or redirect
  appropriately, and that nothing in the codebase still imports the deleted files
  (a simple grep-for-import check, not just "tests pass").
- New account-management page: CEO-only visibility/access, exact 4-field form,
  role dropdown limited to the 8 values.

**Acceptance criteria:** Matches spec §18: "The `/admin` console and its dedicated
cookie/session system no longer exist... no code path depends on
`ADMIN_SESSION_COOKIE`."

**Risks / rollback:** Low risk — this is deletion of dead code once its replacement is
verified live, not a data migration. The only real risk is deleting before the
replacement is fully verified in Phase 5/6's testing; sequence this phase strictly
after those are confirmed working, not in parallel.

---

### Phase 8 — Testing, Security Validation, and Regression Checks

**Objective:** Verify the whole system end-to-end against spec §18's acceptance
criteria, with explicit focus on the two things UI testing alone would miss:
direct-API-call authorization bypass attempts, and regressions in already-working
non-RBAC behavior.

**Files modified:** Test files only, across both `backend/src/test/java/**` and any
new frontend E2E test setup — no application code changes expected in this phase
(any bug found here is fixed by amending the relevant earlier phase, not by adding
new logic here).

**Backend changes:** None (verification phase).

**Frontend changes:** None (verification phase).

**Database/migration changes:** None.

**Dependencies:** All prior phases.

**Tests required (full checklist, consolidating every phase's test section above):**
- Every spec §18 acceptance-criteria bullet, verified via direct API calls
  (curl/Postman/integration tests), not solely through the UI.
- Full regression pass on existing, already-shipped functionality: Task
  creation/editing (non-reassignment fields), Board CRUD, Workspace CRUD (non-CEO
  paths), Time-clock in/out, existing Chat conversations — all unregressed by the new
  access-role layer.
- Session revocation: confirm a CEO-driven account deactivation (if in scope per
  spec §20.10) actually invalidates that person's active session, not just future
  logins.
- All 8 access roles individually exercised against the full permission matrix
  (spec §4), not just CEO and one other role as a stand-in for "it probably works
  the same for the rest."
- Cross-origin cookie behavior confirmed in a real browser session, not only assumed
  from `allowCredentials`/`credentials: "include"` being present in code.

**Acceptance criteria:** Every bullet in spec §18 passes; the full existing backend
test suite (165 tests pre-this-work, plus every test added in Phases 1-7) is green;
no manual-only verification substitutes for an automated regression test where one
is feasible.

**Risks / rollback:** N/A — this phase produces test coverage and a go/no-go signal,
not new production code paths to roll back.

---

## 6. Summary of Confirmed vs. Recommended

**Confirmed (from the spec, re-verified against current code in §0 above):** the two
disconnected demo auth systems, Spring's total lack of authentication, the 8 fixed
access roles, the full permission matrix, the exact 4-field account form, Chat's
uniform cross-role access requirement, and every specific file/behavior cited in
spec §2/§4/§7/§9-§14.

**This plan's own recommendations, not in the spec:** the Phase 3/4 CEO-guard
sequencing note (§5 above), the explicit "test against a role gate being added to
Chat" negative test (Phase 6), and the feature-flag/staged-rollout suggestion for
Phase 4's task-reassignment narrowing (mitigation only, not required).

**New information this plan surfaces that the spec did not have (§0.1, §0.2):** a
dormant, fully-tested Spring Chat backend already exists and is unused; a Spring
`Reset` endpoint already exists and is not in the spec's endpoint inventory. Neither
changes any binding requirement, but both are decision-relevant context for whoever
scopes Phase 6 and Phase 1/3's People-reset interaction.

---

## 7. Unresolved Questions Requiring Product-Owner Input

Every item in spec §20 remains open — this plan does not resolve any of them, per the
task's instruction not to invent a decision. Two additional questions surfaced by this
plan's own review:

1. **(New, from §0.1)** Given a dormant, tested Spring Chat backend already exists,
   should Phase 6 continue preserving Chat on Next.js (this plan's default,
   consistent with the spec's binding instruction), or should the product owner
   authorize adopting the existing Spring module instead? Either is technically
   viable; this plan takes no position beyond flagging that the "as-is preservation"
   default is now a conscious choice with a real alternative on the table, not the
   only option that exists in the codebase.
2. **(New, from Phase 3/4's dependency)** Confirm that a minimal "CEO-only" guard
   built in Phase 3 (ahead of Phase 4's full role matrix) is an acceptable sequencing
   — the alternative is delaying all account creation until Phase 4 completes, which
   would leave the seeded CEO unable to create any other account for a longer stretch
   of the rollout.

All ten items from spec §20 (initial CEO password rotation policy, Next.js
auth-proxy-vs-direct-Spring-call, `GET /api/people` auth requirement, what a "Report"
is, Operation Manager's workspace-management scope, whether to carry the 6 demo people
forward, task-creation-time assignment restriction, `access_role` column rigidity,
login rate-limiting scope, and account editing/deactivation scope) remain
unresolved and are prerequisites for Phases 3, 4, 5, and 10(Reports) respectively —
each phase above notes exactly which open question blocks or narrows it.

---

*End of implementation plan. No source code, database, or Git history was modified
while producing this document.*
