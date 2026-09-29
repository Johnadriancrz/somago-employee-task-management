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

import com.workos.workos_backend.entity.OvertimeRequest;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.service.OvertimeRequestService;

/**
 * HTTP-level tests for the overtime request/approval endpoints, same
 * approach as TimeEntryControllerTest — runs as the fixed local-dev actor
 * (sarah-chen); role-scoped scenarios mutate sarah-chen's own accessRole
 * directly via PersonRepository.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class OvertimeRequestControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private OvertimeRequestService overtimeRequestService;

    @Autowired
    private PersonRepository personRepository;

    private void grantSarahChenAccessRole(String accessRole) {
        Person sarahChen = personRepository.findById("sarah-chen").orElseThrow();
        sarahChen.setAccessRole(accessRole);
        personRepository.save(sarahChen);
    }

    @Test
    void createReturns201WithDocumentedShapeAndServerDerivedRequester() {
        MvcTestResult result = mvc.post().uri("/api/overtime/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                        + "\"workDate\": \"2026-01-05\","
                        + "\"requestedHours\": 2.5,"
                        + "\"reason\": \"Deadline push\""
                        + "}")
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.personId").isEqualTo("sarah-chen");
        assertThat(result).bodyJson().extractingPath("$.workDate").isEqualTo("2026-01-05");
        assertThat(result).bodyJson().extractingPath("$.requestedHours").isEqualTo(2.5);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
    }

    @Test
    void createWithBlankReasonReturns400() {
        MvcTestResult result = mvc.post().uri("/api/overtime/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                        + "\"workDate\": \"2026-01-05\","
                        + "\"requestedHours\": 2.5,"
                        + "\"reason\": \"\""
                        + "}")
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void createDuplicateForSameDateReturns409() {
        mvc.post().uri("/api/overtime/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workDate\": \"2026-01-05\", \"requestedHours\": 2.5, \"reason\": \"First\"}")
                .exchange();

        MvcTestResult result = mvc.post().uri("/api/overtime/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workDate\": \"2026-01-05\", \"requestedHours\": 1.0, \"reason\": \"Second\"}")
                .exchange();

        assertThat(result).hasStatus(409);
    }

    @Test
    void listForNonPrivilegedActorOnlyReturnsOwnRequests() {
        overtimeRequestService.createRequest("alex-morgan", "2026-01-05", 2.0, "A");
        mvc.post().uri("/api/overtime/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workDate\": \"2026-01-06\", \"requestedHours\": 1.0, \"reason\": \"B\"}")
                .exchange();

        MvcTestResult result = mvc.get().uri("/api/overtime/requests").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$[0].personId").isEqualTo("sarah-chen");
    }

    @Test
    void listForCeoWithNoFilterReturnsEveryPersonsRequests() {
        overtimeRequestService.createRequest("alex-morgan", "2026-01-05", 2.0, "A");
        mvc.post().uri("/api/overtime/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workDate\": \"2026-01-06\", \"requestedHours\": 1.0, \"reason\": \"B\"}")
                .exchange();
        grantSarahChenAccessRole("CEO");

        MvcTestResult result = mvc.get().uri("/api/overtime/requests").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(2);
    }

    @Test
    void approveByNonPrivilegedActorReturns403() {
        OvertimeRequest request = overtimeRequestService.createRequest("alex-morgan", "2026-01-05", 2.0, "A");

        MvcTestResult result = mvc.post().uri("/api/overtime/requests/" + request.getId() + "/approve").exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void approveByCeoReturnsApprovedWithReviewer() {
        OvertimeRequest request = overtimeRequestService.createRequest("alex-morgan", "2026-01-05", 2.0, "A");
        grantSarahChenAccessRole("CEO");

        MvcTestResult result = mvc.post().uri("/api/overtime/requests/" + request.getId() + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": \"Approved\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("APPROVED");
        assertThat(result).bodyJson().extractingPath("$.reviewedByPersonId").isEqualTo("sarah-chen");
    }

    @Test
    void approveWithNoBodyStillSucceeds() {
        OvertimeRequest request = overtimeRequestService.createRequest("alex-morgan", "2026-01-05", 2.0, "A");
        grantSarahChenAccessRole("HR");

        MvcTestResult result = mvc.post().uri("/api/overtime/requests/" + request.getId() + "/approve").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("APPROVED");
    }

    @Test
    void rejectByHrReturnsRejected() {
        OvertimeRequest request = overtimeRequestService.createRequest("alex-morgan", "2026-01-05", 2.0, "A");
        grantSarahChenAccessRole("HR");

        MvcTestResult result = mvc.post().uri("/api/overtime/requests/" + request.getId() + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": \"Not justified\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("REJECTED");
    }

    @Test
    void reviewingAnAlreadyReviewedRequestReturns409() {
        OvertimeRequest request = overtimeRequestService.createRequest("alex-morgan", "2026-01-05", 2.0, "A");
        grantSarahChenAccessRole("CEO");
        mvc.post().uri("/api/overtime/requests/" + request.getId() + "/approve").exchange();

        MvcTestResult result = mvc.post().uri("/api/overtime/requests/" + request.getId() + "/reject").exchange();

        assertThat(result).hasStatus(409);
    }

    @Test
    void reviewingAnUnknownRequestReturns404() {
        grantSarahChenAccessRole("CEO");

        MvcTestResult result = mvc.post().uri("/api/overtime/requests/does-not-exist/approve").exchange();

        assertThat(result).hasStatus(404);
    }
}
