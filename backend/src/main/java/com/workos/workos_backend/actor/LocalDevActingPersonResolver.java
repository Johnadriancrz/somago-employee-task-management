package com.workos.workos_backend.actor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * LOCAL DEVELOPMENT ONLY. Resolves the acting person to a single, fixed
 * {@code Person.id} read from server-side configuration
 * ({@code app.local-dev.actor-person-id}) — never from a request header,
 * query parameter, or body field.
 *
 * <p>Only registered when the {@code local-dev} Spring profile is active
 * (see {@code application-local-dev.properties}). Without that profile, no
 * bean of this type exists, so the Workspace/Board controllers that depend
 * on {@link ActingPersonResolver} fail to wire — the app will not silently
 * fall back to trusting request input, and this class must never be
 * registered under a production profile.
 *
 * <p>{@link com.workos.workos_backend.dev.LocalDevPeopleSeeder} verifies at
 * startup that the configured id actually exists as a {@code Person}, so by
 * the time any request reaches this resolver the id is known-valid.
 */
@Component
@Profile("local-dev")
public class LocalDevActingPersonResolver implements ActingPersonResolver {

    private final String actorPersonId;

    public LocalDevActingPersonResolver(
            @Value("${app.local-dev.actor-person-id:sarah-chen}") String actorPersonId) {
        this.actorPersonId = actorPersonId;
    }

    @Override
    public String currentPersonId() {
        return actorPersonId;
    }
}
