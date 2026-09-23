# WorkOS Chat — Blank Page Audit

**Scope:** Read-only audit of `http://localhost:3000/chat` rendering as a blank white page.
**No source code, API routes, auth system, or Spring Boot code were modified for this audit.**

---

## 1. Executive Summary

The Chat page (`workos-app/src/app/(app)/chat/page.tsx`) is the **only** page in the authenticated app shell that unconditionally renders `null` for the entire page — no `<AppShell>`, no header, no error message — whenever the client-side auth context's `user` is falsy:

```tsx
// page.tsx, line 53
if (!user) return null;
```

Every other authenticated page (`dashboards`, `time-clock`, `settings`, `members`, `my-work`, the board root) either doesn't gate on `user` at all, or degrades gracefully (optional chaining, empty arrays) instead of blanking the whole page.

`user` is falsy in two situations:
1. **Briefly, on every page load**, while `AuthProvider`'s `fetchMe()` call is in flight (`status: "loading"`) — normally invisible, resolves in milliseconds.
2. **Indefinitely**, if `fetchMe()` resolves to a 401 (`status: "unauthenticated"`) — and critically, **nothing redirects the user to `/login` in this case**. The generic stale-session recovery logic in `api-client.ts` (`handleStaleSession`) explicitly excludes `/api/auth/*` paths, which is exactly the path `fetchMe()` calls.

There is a well-documented, in-code-commented mechanism that produces exactly this permanent-401 state: the session store (`workos-app/src/lib/server/session.ts`) is an **in-memory `Map`, wiped on every dev-server restart**, while the session cookie itself survives in the browser. The Next.js middleware (`proxy.ts`) only checks that *a* cookie is present (not that it's valid) before allowing the page through — so a stale cookie passes the route gate, lands on `/chat`, and then `fetchMe()` 401s with no recovery path.

**Confirmed by code inspection: this is category A (frontend rendering problem) — specifically an auth-state edge case unique to the Chat page's render gate, not an API/backend defect, not a CSS/layout defect, and not caused by the dormant Spring Boot Chat module.** Runtime evidence (browser console/network) is still needed to confirm this is the *actual* trigger in your environment today, since a permanently-null `user` could in principle also result from a different failure (see §6).

---

## 2. Confirmed Symptoms

- `/chat` renders blank/white in the browser (user-reported, screenshot provided).
- No fix has been applied; this audit is read-only.

No browser console or Network tab output was available to this audit — see §9 for exactly what to capture.

---

## 3. Chat Route Rendering Flow

```
src/app/layout.tsx                (RootLayout)
  └─ AuthProvider                  (src/lib/auth.tsx)
       └─ src/app/(app)/layout.tsx (AuthenticatedLayout)
            └─ BoardProvider → ClockProvider → RailProvider → ConfirmProvider
                 └─ src/app/(app)/chat/page.tsx  (ChatPage)
                      ├─ useBoard()   — from BoardProvider
                      ├─ useAuth()    — from AuthProvider
                      ├─ if (!user) return null    ← EARLY EXIT, RENDERS NOTHING
                      └─ <AppShell>                 (IconRail, Sidebar, TopBar, children)
                           └─ ConversationThread
                                ├─ useConversationMessages() → fetchMessages() + openMessageStream()
                                └─ message list / composer UI
```

Key facts about this flow:

- **No route-level `loading.tsx` or `error.tsx`** exists under `src/app/(app)/chat/` (confirmed absent). So while `user` is `null`, there is no skeleton/spinner — just nothing.
- `AppShell` (`src/components/shell/AppShell.tsx`) itself has no auth dependency — it only reads `usePathname()` and `useRail()`. It is never reached if `ChatPage` returns early.
- All hooks in `ChatPage` (`useBoard`, `useAuth`, two `useState`s) are called **before** the `if (!user) return null` line, so there is no React "hooks called conditionally" violation — this is a clean, but unusually aggressive, early return.
- `ConversationThread` (and its `useConversationMessages` hook, which calls `fetchMessages`/`openMessageStream`) never mounts at all until `user` is truthy — so any failure inside message fetching or the SSE stream is **not reachable** while the page is blank. The blank page is upstream of Chat's own data layer.

---

## 4. Relevant Files and Their Responsibilities

| File | Responsibility |
|---|---|
| `workos-app/src/app/layout.tsx` | Root HTML shell; wraps everything in `AuthProvider`. |
| `workos-app/src/lib/auth.tsx` | `AuthProvider`/`useAuth()`. Holds `user`/`status` state; calls `fetchMe()` once on mount. |
| `workos-app/src/app/(app)/layout.tsx` | Route-group layout behind auth; mounts `BoardProvider`/`ClockProvider`/`RailProvider`/`ConfirmProvider`. Does **not** itself check `user`. |
| `workos-app/src/app/(app)/chat/page.tsx` | The Chat page. Contains the `if (!user) return null` early exit (line 53) and the `ConversationThread` UI. |
| `workos-app/src/components/shell/AppShell.tsx` | Shared chrome (icon rail, sidebar, top bar). No auth gate of its own. |
| `workos-app/src/lib/api-client.ts` | All HTTP calls, including `fetchMe`, `fetchMessages`, `sendMessageRequest`, `openMessageStream` (SSE). Contains `handleStaleSession()`, which explicitly does **not** fire for `/api/auth/*` 401s. |
| `workos-app/src/proxy.ts` | Edge middleware. Redirects to `/login` only when the session **cookie is absent** — does not validate the cookie's value. Comment on line 8-12 states this explicitly. |
| `workos-app/src/lib/server/require-session.ts` | Server-side helper; resolves the session cookie to a person id via `getSessionPersonId`. |
| `workos-app/src/lib/server/session.ts` | **In-memory `Map<token, personId>`** session store. Comment (lines 6-8) states it "resets on server restart." |
| `workos-app/src/app/api/auth/me/route.ts` | `GET /api/auth/me` — returns the person or 401 if `requireSessionPersonId()` yields no valid session. |
| `workos-app/src/app/api/chat/messages/route.ts` | `GET`/`POST /api/chat/messages` — both call `requireSessionPersonId()`, 401 if absent; then `canAccessConversation` (403 if not a participant). |
| `workos-app/src/app/api/chat/stream/route.ts` | `GET /api/chat/stream` — SSE endpoint. Same auth/authorization gate, then subscribes to the in-process `chatEvents` `EventEmitter`. |
| `workos-app/src/lib/server/chat-repository.ts` | In-memory message store (`messages` array), `canAccessConversation`, `createMessage`/`listMessages`. Messages start empty by design ("Blank on purpose"). |
| `workos-app/src/lib/server/chat-events.ts` | Module-level `EventEmitter` singleton used to fan out new messages to open SSE connections in this process only. |
| `backend/.../ChatController.java` etc. | Dormant Spring Boot Chat implementation (see §8) — not called by the frontend at all. |

---

## 5. Confirmed Root Cause

**Confirmed by code inspection:** The Chat page is uniquely vulnerable to rendering permanently blank because it gates its entire render tree on `useAuth().user` with no loading state, no error state, and no redirect:

```tsx
// workos-app/src/app/(app)/chat/page.tsx:53
if (!user) return null;
```

**Confirmed by code inspection, as a plausible trigger:** a stale session cookie. Sequence:

1. Dev server restarts at some point (common during active development — this workspace's `git status` shows uncommitted edits to `time-clock/page.tsx` and `api-client.ts`, consistent with an active dev session that could have restarted).
2. The browser still holds the `SESSION_COOKIE` from before the restart.
3. `proxy.ts` only checks `request.cookies.has(SESSION_COOKIE)` — true — so it lets the request through to `/chat` without redirecting to `/login`.
4. `ChatPage` mounts; `AuthProvider`'s `fetchMe()` calls `GET /api/auth/me`.
5. Server-side, `requireSessionPersonId()` → `getSessionPersonId()` looks up the token in the now-empty in-memory `sessions` Map → returns `null` → `/api/auth/me` returns `401`.
6. `AuthProvider`'s `.catch()` sets `status: "unauthenticated"`, `user` stays `null` — **permanently**, since nothing re-attempts `fetchMe()`.
7. `api-client.ts`'s `request()` does call `handleStaleSession()` on 401s generically, but explicitly **skips it for paths starting with `/api/auth/`** (line 77: `!path.startsWith("/api/auth/")`) — so the one 401 that actually indicates "your session is dead" is the one 401 that's excluded from the recovery redirect.
8. Result: `/chat` sits with `user === null` forever, `ChatPage` returns `null` forever, and the page is blank white — with no console error, since nothing throws (the fetch failure is caught and only logged... actually not even logged, since `fetchMe().catch(() => setStatus(...))` swallows the error silently).

This matches the reported symptom (persistent blank page, not a flash) exactly, and explains why it's specific to Chat: other pages using `useAuth()` (`time-clock`, `settings`, `members`, `my-work`) only use `user` for scoped bits of UI (`user?.id`, `if (!user) return []`) rather than gating the whole page.

**Not yet confirmed by runtime evidence.** This audit did not capture live browser console/Network output, so it cannot yet confirm that this exact scenario (vs. a different cause) is what's happening in your environment right now. See §9 for what to capture to close this gap.

---

## 6. Potential Causes That Remain Unverified

These are plausible alternative or contributing explanations that code inspection alone cannot rule out. Each needs the runtime evidence in §9 to confirm or eliminate:

1. **`fetchMe()` never resolving at all** (network error, CORS, or the dev server itself being down) — would also leave `status: "loading"` or throw, producing the same blank symptom via a different path. `fetchMe()`'s `.catch()` has no `finally`, but it does set `status` in both `.then` and `.catch`, so a hang would mean the fetch promise itself never settles (e.g., a stalled request) — Network tab will show this immediately as a pending request.
2. **`BoardProvider`, `ClockProvider`, `RailProvider`, or `ConfirmProvider` throwing during their own initial data fetch**, before `ChatPage` even runs — since these wrap `chat/page.tsx` in `(app)/layout.tsx`, an uncaught error/rejection in one of their mount effects could in principle produce a blank page (or a full white-screen React error boundary state, if one exists higher up — none was found in this audit, which itself is worth noting: **there is no error boundary in the `(app)` layout**, so any uncaught render-time exception anywhere in this tree would also currently present as a blank page rather than a friendly error).
3. **A genuinely expired/invalid Person record** — `getPerson(personId)` in `/api/auth/me` could return `null` even with a valid session token if the underlying in-memory people store was reset independently (e.g., via `/api/reset`) without the session being cleared. Not confirmed either way without runtime logs.
4. **Browser extension / dev-tool interference, or a stale service worker / cached bundle** from `.next/` build artifacts — generic possibilities that source inspection cannot confirm or deny.
5. **`Next.js 16.3.5` / `React 19.2.8`-specific behavior.** `AGENTS.md` in `workos-app/` explicitly warns this is a non-standard/bleeding-edge Next.js build with breaking changes from "the Next.js you know" (e.g., `proxy.ts` instead of the conventional `middleware.ts`, typed `LayoutProps<"/">`). This raises the possibility of a framework-level rendering/hydration quirk unrelated to the auth-gate hypothesis above, though nothing in the source points to one specifically.

None of these were confirmed or ruled out by this audit — they are listed as open hypotheses only.

---

## 7. API and Authentication Findings

- **`GET /api/chat/messages`**, **`POST /api/chat/messages`**, **`GET /api/chat/stream`** all correctly call `requireSessionPersonId()` first and return `401`/`403` appropriately (confirmed by code inspection — see §4 table). They are never reached while `ChatPage` is blank, because `ConversationThread` (which calls them) doesn't mount until `user` is truthy.
- **`requireSessionPersonId()`** (`src/lib/server/require-session.ts`) is a thin wrapper: cookie → `getSessionPersonId()` → in-memory `Map` lookup. It has no fallback and no way to distinguish "no cookie" from "cookie present but session expired/reset" — both return `null` identically. This is consistent with the design comment in `session.ts` ("Stub session store... resets on server restart") — it's explicitly a dev/mock implementation, not a defect introduced recently.
- **The current auth/session system could absolutely prevent Chat from loading**, specifically via the stale-session-after-restart path in §5. This is a pre-existing architectural gap (no client-side recovery for a 401 from `/api/auth/me` itself) that is not unique to Chat, but Chat is the page that turns it into a *silent, total* blank page instead of a degraded-but-visible page.
- Per instructions, the authentication system itself was not modified and no fix is proposed here beyond what's in §9's recommended order.

---

## 8. Relationship Between Next.js Chat and Spring Boot Chat

These are two **entirely separate, non-interacting** implementations:

| | Next.js Chat (active) | Spring Boot Chat (dormant) |
|---|---|---|
| Location | `workos-app/src/app/api/chat/*`, `workos-app/src/lib/server/chat-*.ts` | `backend/src/main/java/.../controller/ChatController.java` + related service/repository/entity/DTO/migration (`V4__create_chat_messages_table.sql`) |
| Storage | In-memory array (`chat-repository.ts`), wiped on restart | Real MySQL table via Flyway `V4` migration |
| Live updates | In-process Node `EventEmitter` (`chat-events.ts`) | `ChatMessageBroadcaster` (`SseEmitter`-based), same single-process limitation |
| Called by frontend? | **Yes** — `api-client.ts`'s `fetchMessages`/`sendMessageRequest`/`openMessageStream` call the Next.js routes via the plain `request()` helper | **No** — nothing in `api-client.ts` targets it; all other integrated modules (People, Workspaces, Boards, Tasks, Time Clock) use `springRequest()`, but the Chat functions in `api-client.ts` (lines 211-235) use plain `request()` against the Next.js routes |
| Test status | No dedicated frontend tests found | 3 backend test files present (`ChatControllerTest`, `ChatServiceTest`, `ChatMessageBroadcasterTest`) |

**The dormant Spring Boot Chat backend is not responsible for the blank page** — the frontend never calls it, and it has no bearing on whether `localhost:3000/chat` renders. It is out of scope for fixing this bug and was not modified. (This matches known project history: Chat/Time Clock/Reset were the last modules left on the Next.js in-memory stub before this session's Time Clock integration work, per prior project notes — Chat integration to Spring Boot was never started.)

---

## 9. Trace the Actual Failure — Runtime Evidence Needed

**This audit did not run the app or capture live output** — no process was started, restarted, or killed, per your instructions (the Java process on port 8080 was left untouched, and no `npm run dev` process was started or inspected).

To confirm §5's hypothesis (or surface the true cause if it's different), please capture and share the following. **Do not restart any running process to do this** — just interact with the already-loaded/loading page:

### A. Browser DevTools Console
1. Open `http://localhost:3000/chat` (or refresh it if already open).
2. Open DevTools → **Console** tab.
3. Copy **every** line shown, including warnings — especially anything mentioning `auth`, `401`, `fetchMe`, `hydration`, or a React error boundary stack trace.
4. Note whether the console is completely empty (this itself is a meaningful data point — it would support the "silent swallowed 401" hypothesis in §5, since `AuthProvider`'s `.catch()` doesn't log anything).

### B. Browser DevTools Network tab
1. Open DevTools → **Network** tab, check "Preserve log," then reload `/chat`.
2. Find the request to **`/api/auth/me`** — record its **status code** (200 vs 401) and response body.
3. Find any request to **`/api/chat/messages`** or **`/api/chat/stream`** — note whether they fire *at all*. If `/api/auth/me` is 401, per §3's flow these should be **absent entirely** (confirming `ConversationThread` never mounted), which would confirm the root cause in §5.
4. Check the **Cookies** for the request to `/chat` itself (Application tab → Cookies, or the request headers) — confirm whether the session cookie (`SESSION_COOKIE` — check `src/lib/server/session-cookie.ts` for its exact name) is present.

### C. Next.js terminal output
1. Look at the terminal running `npm run dev` (do not restart it).
2. Note whether it printed anything when you loaded `/chat` — a compile error, a thrown exception in a Server Component, or nothing at all.
3. Note whether the terminal shows evidence of a **recent restart** (a fresh "Ready in Xms" banner with a timestamp before your browser session's cookie was set) — this would directly corroborate §5.

Once you share this, the root cause can move from "confirmed hypothesis, unverified in this environment" to "confirmed by runtime evidence."

---

## 10. Recommended Fix Order

*(For your review only — no changes have been made. Do not implement until you authorize the next step.)*

1. **Verify §9 first.** Confirm whether `/api/auth/me` is actually returning 401 on the failing load, before changing anything.
2. If confirmed: give `ChatPage` a real loading/unauthenticated state instead of `return null` — e.g. use `status` (not just `user`) from `useAuth()` to show a spinner while `status === "loading"`, and redirect (or show a "session expired, please sign in again" prompt) when `status === "unauthenticated"` — consistent with how a stale-session recovery already exists for other 401s in `api-client.ts`.
3. Separately, consider whether `/api/auth/me` 401s should also trigger `handleStaleSession()` in `api-client.ts` (currently explicitly excluded) — this is the architectural gap that lets *any* page silently strand itself on a dead session, not just Chat. Worth a decision on intended behavior (some flows may want `/api/auth/me` to fail silently, e.g. to probe auth state on `/login`) rather than a blind fix.
4. Add a route-level `error.tsx` (and/or `loading.tsx`) under `src/app/(app)/` or `src/app/(app)/chat/` so any future uncaught error in this tree fails visibly instead of blank (addresses the gap noted in §6.2).
5. Only after the above: consider whether Chat should eventually move to the Spring Boot backend like Boards/Tasks/Time Clock — explicitly out of scope for this bug fix per your instructions, and not a cause of the current issue.

## 11. Regression Tests Needed After the Fix

- **Fresh session, `/chat` loads normally** — messages list renders (empty-state "No messages yet" for a new conversation), General channel and DM list populate from `people`.
- **Simulated stale session** (delete/corrupt the in-memory session token server-side, or restart the dev server with the cookie still set in the browser) — verify `/chat` now shows a loading or re-auth prompt instead of blank, and ideally redirects to `/login`.
- **`status === "loading"` transient state** — verify a brief, intentional delay in `fetchMe()` shows a loading indicator rather than a flash of blank content.
- **Genuinely unauthenticated (no cookie at all)** — confirm `proxy.ts` still redirects to `/login` before the client ever mounts (this path already works today per code inspection; should stay covered).
- **Other `useAuth()`-consuming pages** (`time-clock`, `settings`, `members`, `my-work`) — regression-check they still behave correctly after any shared change to `AuthProvider`/`handleStaleSession`, since a fix here is likely to touch shared code.
- **SSE stream (`/api/chat/stream`) reconnect behavior** — not exercised by this bug, but worth a smoke test once Chat is reachable again: send a message from one tab, confirm it appears live in another tab/conversation view without a refresh.
- **Spring Boot Chat module** — no regression risk from this fix (untouched, unrelated), but confirm no accidental coupling was introduced.

---

*Audit performed via static code inspection only. No processes were started, stopped, or restarted. No source files, API routes, authentication code, Spring Boot code, or environment/config files were modified. Only this report was created.*
