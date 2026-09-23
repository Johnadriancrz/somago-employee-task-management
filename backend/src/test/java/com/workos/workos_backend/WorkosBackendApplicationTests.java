package com.workos.workos_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full-context smoke test. Activates {@code local-dev} because, as of Step
 * 2B, WorkspaceController/BoardController depend on ActingPersonResolver,
 * which is only registered under that profile (see
 * com.workos.workos_backend.actor.LocalDevActingPersonResolver) — without
 * it, the context correctly fails to start, which is the intended isolation
 * for a mechanism that must never run in production.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
class WorkosBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
