package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.service.WorkspaceService;

/**
 * HTTP-level tests for the Workspaces + Workspace Members endpoints,
 * verifying status codes and the exact JSON contract from BACKEND.md.
 * Runs as the fixed local-dev actor (sarah-chen, see
 * application-local-dev.properties); scenarios that need a different actor
 * seed a workspace directly via WorkspaceService and check sarah-chen's
 * (the HTTP caller's) permissions against it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class WorkspaceControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private PersonRepository personRepository;

    private void grantSarahChenAccessRole(String accessRole) {
        Person sarahChen = personRepository.findById("sarah-chen").orElseThrow();
        sarahChen.setAccessRole(accessRole);
        personRepository.save(sarahChen);
    }

    @Test
    void createReturns201WithDocumentedShapeAndAutoInitials() {
        MvcTestResult result = mvc.post().uri("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Growth Team\"}")
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Growth Team");
        assertThat(result).bodyJson().extractingPath("$.initials").isEqualTo("GT");
        assertThat(result).bodyJson().extractingPath("$.ownerId").isEqualTo("sarah-chen");
        assertThat(result).bodyJson().extractingPath("$.memberIds").asArray().containsExactly("sarah-chen");
        assertThat(result).bodyJson().extractingPath("$.id").isNotNull();
    }

    @Test
    void createHonorsExplicitInitials() {
        MvcTestResult result = mvc.post().uri("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Growth Team\", \"initials\": \"ZZ\"}")
                .exchange();

        assertThat(result).bodyJson().extractingPath("$.initials").isEqualTo("ZZ");
    }

    @Test
    void createRejectsMissingName() {
        MvcTestResult result = mvc.post().uri("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error").isNotNull();
    }

    @Test
    void createRejectsMalformedJson() {
        MvcTestResult result = mvc.post().uri("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not json")
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void listOnlyReturnsWorkspacesTheActorOwnsOrBelongsTo() {
        Workspace visible = workspaceService.createWorkspace("sarah-chen", "Visible Ws", "VW");
        Workspace hidden = workspaceService.createWorkspace("alex-morgan", "Hidden Ws", "HW");

        MvcTestResult result = mvc.get().uri("/api/workspaces").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='" + visible.getId() + "')]").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='" + hidden.getId() + "')]").asArray().isEmpty();
    }

    @Test
    void deleteReturnsOkForOwner() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "To Delete", "TD");

        MvcTestResult result = mvc.delete().uri("/api/workspaces/{id}", workspace.getId()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.ok").isEqualTo(true);
    }

    @Test
    void deleteForbiddenForNonOwner() {
        Workspace workspace = workspaceService.createWorkspace("alex-morgan", "Not Mine", "NM");
        workspaceService.addMember("alex-morgan", workspace.getId(), "sarah-chen");

        MvcTestResult result = mvc.delete().uri("/api/workspaces/{id}", workspace.getId()).exchange();

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.error").isNotNull();
    }

    @Test
    void deleteUnknownWorkspaceReturns404() {
        MvcTestResult result = mvc.delete().uri("/api/workspaces/does-not-exist").exchange();
        assertThat(result).hasStatus(404);
    }

    @Test
    void addMemberSucceedsForOwnerAndReturnsUpdatedWorkspace() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "Team", "TM");

        MvcTestResult result = mvc.post().uri("/api/workspaces/{id}/members", workspace.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personId\": \"alex-morgan\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.memberIds").asArray()
                .containsExactlyInAnyOrder("sarah-chen", "alex-morgan");
    }

    @Test
    void addMemberRejectsUnknownPersonWith404() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "Team", "TM");

        MvcTestResult result = mvc.post().uri("/api/workspaces/{id}/members", workspace.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personId\": \"nobody-here\"}")
                .exchange();

        assertThat(result).hasStatus(404);
    }

    @Test
    void addMemberForbiddenForNonOwner() {
        Workspace workspace = workspaceService.createWorkspace("alex-morgan", "Team", "TM");
        workspaceService.addMember("alex-morgan", workspace.getId(), "sarah-chen");

        MvcTestResult result = mvc.post().uri("/api/workspaces/{id}/members", workspace.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personId\": \"priya-patel\"}")
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void removeMemberRejectsRemovingOwnerWith400() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "Team", "TM");

        MvcTestResult result = mvc.delete()
                .uri("/api/workspaces/{workspaceId}/members/{personId}", workspace.getId(), "sarah-chen")
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void removeMemberSucceedsForOwner() {
        Workspace workspace = workspaceService.createWorkspace("sarah-chen", "Team", "TM");
        workspaceService.addMember("sarah-chen", workspace.getId(), "alex-morgan");

        MvcTestResult result = mvc.delete()
                .uri("/api/workspaces/{workspaceId}/members/{personId}", workspace.getId(), "alex-morgan")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.memberIds").asArray().containsExactly("sarah-chen");
    }

    @Test
    void addMemberSucceedsForCeoEvenWithoutOwnershipOrMembership() {
        Workspace workspace = workspaceService.createWorkspace("alex-morgan", "Team", "TM");
        grantSarahChenAccessRole("CEO");

        MvcTestResult result = mvc.post().uri("/api/workspaces/{id}/members", workspace.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personId\": \"priya-patel\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.memberIds").asArray()
                .containsExactlyInAnyOrder("alex-morgan", "priya-patel");
    }

    @Test
    void removeMemberSucceedsForCeoEvenWithoutOwnershipOrMembership() {
        Workspace workspace = workspaceService.createWorkspace("alex-morgan", "Team", "TM");
        workspaceService.addMember("alex-morgan", workspace.getId(), "priya-patel");
        grantSarahChenAccessRole("CEO");

        MvcTestResult result = mvc.delete()
                .uri("/api/workspaces/{workspaceId}/members/{personId}", workspace.getId(), "priya-patel")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.memberIds").asArray().containsExactly("alex-morgan");
    }
}
