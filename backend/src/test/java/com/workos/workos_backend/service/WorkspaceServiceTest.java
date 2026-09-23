package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.exception.ResourceNotFoundException;
import com.workos.workos_backend.repository.WorkspaceRepository;

import jakarta.persistence.EntityManager;

/**
 * Exercises WorkspaceService's business rules directly against the real
 * (H2, in the test profile) database, passing actor ids explicitly — this
 * is deliberate: it lets ownership/membership permission logic be tested
 * against many different actors without needing to reconfigure the
 * local-dev fixed actor per scenario. Uses the seeded local-dev people
 * (sarah-chen, alex-morgan, priya-patel, ...) as real, pre-existing Person
 * rows to reference.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class WorkspaceServiceTest {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createWorkspaceMakesCreatorOwnerAndSoleMember() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "Product Eng", null);

        assertThat(workspace.getOwner().getId()).isEqualTo("sarah-chen");
        assertThat(workspace.getMembers()).extracting(Person::getId).containsExactly("sarah-chen");
    }

    @Test
    void generatesInitialsFromNameWhenOmitted() {
        assertThat(workspaceService.createWorkspace("sarah-chen", "Product Engineering", null).getInitials())
                .isEqualTo("PE");
        assertThat(workspaceService.createWorkspace("sarah-chen", "Ops", null).getInitials())
                .isEqualTo("O");
        assertThat(workspaceService.createWorkspace("sarah-chen", "  Growth   Team  ", "").getInitials())
                .isEqualTo("GT");
    }

    @Test
    void usesProvidedInitialsWhenGiven() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "Product Eng", "XY");
        assertThat(workspace.getInitials()).isEqualTo("XY");
    }

    @Test
    void listVisibleWorkspacesReturnsOwnedAndMemberOfOnly() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "Product Eng", "PE");
        workspaceService.addMember("sarah-chen", workspace.getId(), "alex-morgan");

        assertThat(workspaceService.listVisibleWorkspaces("sarah-chen"))
                .extracting(Workspace::getId).contains(workspace.getId());
        assertThat(workspaceService.listVisibleWorkspaces("alex-morgan"))
                .extracting(Workspace::getId).contains(workspace.getId());
        assertThat(workspaceService.listVisibleWorkspaces("priya-patel"))
                .extracting(Workspace::getId).doesNotContain(workspace.getId());
    }

    @Test
    void deleteWorkspaceRequiresOwner() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "Product Eng", "PE");
        workspaceService.addMember("sarah-chen", workspace.getId(), "alex-morgan");

        assertThatThrownBy(() -> workspaceService.deleteWorkspace("alex-morgan", workspace.getId()))
                .isInstanceOf(ForbiddenException.class);

        workspaceService.deleteWorkspace("sarah-chen", workspace.getId());
        assertThat(workspaceRepository.findById(workspace.getId())).isEmpty();
    }

    @Test
    void deleteWorkspaceRejectsUnknownId() {
        assertThatThrownBy(() -> workspaceService.deleteWorkspace("sarah-chen", "does-not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addMemberRequiresOwnerAndKnownPerson() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "Product Eng", "PE");

        assertThatThrownBy(() -> workspaceService.addMember("alex-morgan", workspace.getId(), "priya-patel"))
                .isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> workspaceService.addMember("sarah-chen", workspace.getId(), "unknown-person"))
                .isInstanceOf(ResourceNotFoundException.class);

        Workspace updated = workspaceService.addMember("sarah-chen", workspace.getId(), "priya-patel");
        assertThat(updated.getMembers()).extracting(Person::getId)
                .containsExactlyInAnyOrder("sarah-chen", "priya-patel");
    }

    @Test
    void removeMemberRequiresOwnerAndRejectsRemovingOwner() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "Product Eng", "PE");
        workspaceService.addMember("sarah-chen", workspace.getId(), "alex-morgan");

        assertThatThrownBy(() -> workspaceService.removeMember("alex-morgan", workspace.getId(), "alex-morgan"))
                .isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> workspaceService.removeMember("sarah-chen", workspace.getId(), "sarah-chen"))
                .isInstanceOf(IllegalArgumentException.class);

        Workspace updated = workspaceService.removeMember("sarah-chen", workspace.getId(), "alex-morgan");
        assertThat(updated.getMembers()).extracting(Person::getId).containsExactly("sarah-chen");
    }

    @Test
    void workspacePersistsAcrossAReload() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "Durability Check", "DC");
        String id = workspace.getId();
        entityManager.flush();
        entityManager.clear();

        Workspace reloaded = workspaceRepository.findById(id).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Durability Check");
        assertThat(reloaded.getInitials()).isEqualTo("DC");
        assertThat(reloaded.getOwner().getId()).isEqualTo("sarah-chen");
        assertThat(reloaded.getMembers()).extracting(Person::getId).containsExactly("sarah-chen");
    }

    @Test
    void createWorkspaceRejectsUnknownActor() {
        assertThatThrownBy(() -> workspaceService.createWorkspace(UUID.randomUUID().toString(), "X", null))
                .isInstanceOf(IllegalStateException.class);
    }
}
