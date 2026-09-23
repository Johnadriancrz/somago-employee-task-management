package com.workos.workos_backend.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import com.workos.workos_backend.repository.BoardMetaRepository;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.WorkspaceRepository;

import jakarta.persistence.EntityManager;

/**
 * Verifies Workspace-to-Person and Workspace-to-Board relationships, the
 * owner-must-be-member business rule, and the foreign key constraints from
 * the Step 2A migration (V1__create_person_workspace_board_tables.sql).
 * Runs against an isolated in-memory H2 database, never workos_db.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class WorkspacePersistenceTest {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private BoardMetaRepository boardMetaRepository;

    @Autowired
    private EntityManager entityManager;

    private Person person(String id, String email) {
        return personRepository.saveAndFlush(new Person(id, id, email, "XX", "Role", "chip"));
    }

    @Test
    void workspaceOwnerIsPersistedAsAMember() {
        Person owner = person("owner-1", "owner1@workos.dev");
        Person member = person("member-1", "member1@workos.dev");

        Workspace workspace = new Workspace("ws-1", "Product Eng", "PE", owner);
        workspace.addMember(member);
        workspaceRepository.saveAndFlush(workspace);
        entityManager.clear();

        Workspace reloaded = workspaceRepository.findById("ws-1").orElseThrow();

        assertThat(reloaded.getOwner().getId()).isEqualTo("owner-1");
        assertThat(reloaded.getMembers()).extracting(Person::getId)
                .containsExactlyInAnyOrder("owner-1", "member-1");
    }

    @Test
    void rejectsWorkspaceWhoseOwnerIsNotAMember() {
        Person owner = person("owner-2", "owner2@workos.dev");
        Workspace workspace = new Workspace("ws-2", "Product Eng", "PE", owner);
        workspace.removeMember(owner);

        assertThatThrownBy(() -> workspaceRepository.saveAndFlush(workspace))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .cause().isInstanceOf(IllegalStateException.class);
    }

    @Test
    void workspaceOwnsItsBoardsAndCascadesDeleteToThem() {
        Person owner = person("owner-3", "owner3@workos.dev");
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("ws-3", "Product Eng", "PE", owner));

        boardMetaRepository.saveAndFlush(new BoardMeta("board-1", workspace, "Sprint Board", "desc", BoardIcon.KANBAN));
        entityManager.clear();

        List<BoardMeta> boards = boardMetaRepository.findByWorkspaceId("ws-3");
        assertThat(boards).extracting(BoardMeta::getId).containsExactly("board-1");

        workspaceRepository.deleteById("ws-3");
        workspaceRepository.flush();

        assertThat(boardMetaRepository.findById("board-1")).isEmpty();
    }

    @Test
    void rejectsBoardReferencingAnUnknownWorkspace() {
        Workspace phantom = new Workspace();
        setId(phantom, "does-not-exist");

        BoardMeta board = new BoardMeta("board-orphan", phantom, "Orphan Board", "desc", BoardIcon.TABLE);

        // Hibernate catches the dangling reference itself (unsaved transient
        // instance check) before a statement ever reaches the FK constraint.
        assertThatThrownBy(() -> boardMetaRepository.saveAndFlush(board))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    void rejectsMembershipReferencingAnUnknownPerson() {
        Person owner = person("owner-4", "owner4@workos.dev");
        Workspace workspace = new Workspace("ws-4", "Product Eng", "PE", owner);
        Person ghost = new Person();
        setId(ghost, "does-not-exist");
        workspace.addMember(ghost);

        assertThatThrownBy(() -> workspaceRepository.saveAndFlush(workspace))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    private static void setId(Object entity, String id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
