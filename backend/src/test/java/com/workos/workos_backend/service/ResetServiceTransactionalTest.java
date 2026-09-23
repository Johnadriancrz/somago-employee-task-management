package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.WorkspaceRepository;

/**
 * Verifies ResetService.reset()'s transactional "all or nothing" guarantee.
 * Deliberately runs WITHOUT a class-level {@code @Transactional}: unlike
 * ResetServiceTest (which relies on the test's own transaction rolling back
 * everything at teardown), this class needs each step to actually commit so
 * a failure partway through {@code reset()} can be observed as a real
 * database-level rollback of everything reset() already did, not just of
 * the surrounding test transaction.
 *
 * <p>Forces the failure by making the people re-seed step collide on the
 * {@code people.email} unique constraint: deleting "noah-ibrahim" and giving
 * a decoy row its email means {@code LocalDevPeopleSeeder.seedMissingPeople()}
 * (which sees the id missing and tries to reinsert the canonical row) fails.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
class ResetServiceTransactionalTest {

    @Autowired
    private ResetService resetService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private PersonRepository personRepository;

    @AfterEach
    void cleanUp() {
        personRepository.findById("decoy-person").ifPresent(personRepository::delete);
        // Now that the email collision is gone, this both wipes any leftover
        // workspace from the test and restores noah-ibrahim in one call.
        resetService.reset();
    }

    @Test
    void aFailedPeopleReseedRollsBackTheWorkspaceWipeToo() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "Should Survive Rollback", "SS");
        Person noah = personRepository.findById("noah-ibrahim").orElseThrow();
        String noahEmail = noah.getEmail();
        personRepository.delete(noah);
        personRepository.saveAndFlush(
                new Person("decoy-person", "Decoy", noahEmail, "DP", "Decoy", "bg-primary text-on-primary"));

        assertThatThrownBy(() -> resetService.reset()).isInstanceOf(DataAccessException.class);

        // The workspace created above must still exist: the deleteAll() the
        // failed reset() performed before hitting the email collision was
        // rolled back along with everything else in that transaction.
        assertThat(workspaceRepository.findById(workspace.getId())).isPresent();
    }
}
