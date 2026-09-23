package com.workos.workos_backend.actor;

/**
 * Resolves the {@link com.workos.workos_backend.entity.Person} id "acting"
 * on the current request, for ownership/membership checks in the Workspace
 * and Board APIs.
 *
 * <p>Authentication is intentionally deferred (see BACKEND.md / Step 2B
 * scope). Until a real session/auth mechanism exists, no implementation of
 * this interface may resolve the actor from client-supplied input (a
 * header, query param, or body field) — that would let any caller
 * impersonate any {@code Person} simply by changing a value it controls.
 * The only implementation registered today,
 * {@link LocalDevActingPersonResolver}, is wired up exclusively under the
 * {@code local-dev} Spring profile and returns a single, server-configured
 * id. A future real-auth implementation (e.g. reading the signed-in user
 * from a session/JWT) replaces it without changing any caller of this
 * interface.
 */
public interface ActingPersonResolver {

    /** The {@code Person.id} acting on the current request. */
    String currentPersonId();
}
