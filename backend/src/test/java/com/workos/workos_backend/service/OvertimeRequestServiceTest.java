package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.OvertimeRequest;
import com.workos.workos_backend.entity.OvertimeRequestStatus;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.exception.ConflictException;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.exception.ResourceNotFoundException;

/**
 * Exercises OvertimeRequestService's business rules directly, same approach
 * as TimeEntryServiceTest — actor ids are passed explicitly, and role-scoped
 * scenarios mint a fresh CEO/HR account via AccountService since the seeded
 * local-dev people all have a null accessRole.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class OvertimeRequestServiceTest {

    @Autowired
    private OvertimeRequestService overtimeRequestService;

    @Autowired
    private AccountService accountService;

    private String newAccount(String accessRole) {
        Person person = accountService.createAccount(
                "Test " + accessRole, accessRole.toLowerCase().replace(" ", ".") + "-"
                        + UUID.randomUUID() + "@workos.dev",
                "Password123!", accessRole);
        return person.getId();
    }

    @Test
    void createRequestPersistsAPendingRequestForTheActor() {
        OvertimeRequest request = overtimeRequestService.createRequest(
                "sarah-chen", "2026-01-05", 2.0, "Deadline push");

        assertThat(request.getPerson().getId()).isEqualTo("sarah-chen");
        assertThat(request.getWorkDate().toString()).isEqualTo("2026-01-05");
        assertThat(request.getRequestedHours()).isEqualTo(2.0);
        assertThat(request.getStatus()).isEqualTo(OvertimeRequestStatus.PENDING);
    }

    @Test
    void createRequestRejectsInvalidWorkDate() {
        assertThatThrownBy(() -> overtimeRequestService.createRequest(
                "sarah-chen", "not-a-date", 2.0, "Deadline push"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRequestRejectsASecondPendingRequestForTheSameDate() {
        overtimeRequestService.createRequest("sarah-chen", "2026-01-05", 2.0, "Deadline push");

        assertThatThrownBy(() -> overtimeRequestService.createRequest(
                "sarah-chen", "2026-01-05", 1.0, "Second try"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createRequestAllowsResubmissionAfterARejection() {
        OvertimeRequest first = overtimeRequestService.createRequest(
                "sarah-chen", "2026-01-05", 2.0, "Deadline push");
        String ceoId = newAccount("CEO");
        overtimeRequestService.reject(ceoId, first.getId(), null);

        OvertimeRequest second = overtimeRequestService.createRequest(
                "sarah-chen", "2026-01-05", 3.0, "Trying again");

        assertThat(second.getStatus()).isEqualTo(OvertimeRequestStatus.PENDING);
    }

    @Test
    void listRequestsForNonPrivilegedActorOnlyReturnsOwnRequests() {
        overtimeRequestService.createRequest("sarah-chen", "2026-01-05", 2.0, "A");
        overtimeRequestService.createRequest("alex-morgan", "2026-01-05", 2.0, "B");

        List<OvertimeRequest> requests = overtimeRequestService.listRequests("sarah-chen", "alex-morgan");

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getPerson().getId()).isEqualTo("sarah-chen");
    }

    @Test
    void listRequestsForCeoWithNoFilterReturnsEveryPersonsRequests() {
        overtimeRequestService.createRequest("sarah-chen", "2026-01-05", 2.0, "A");
        overtimeRequestService.createRequest("alex-morgan", "2026-01-05", 2.0, "B");
        String ceoId = newAccount("CEO");

        assertThat(overtimeRequestService.listRequests(ceoId, null)).hasSize(2);
    }

    @Test
    void approveRejectsNonCeoHrActorWithForbidden() {
        OvertimeRequest request = overtimeRequestService.createRequest(
                "sarah-chen", "2026-01-05", 2.0, "Deadline push");

        assertThatThrownBy(() -> overtimeRequestService.approve("alex-morgan", request.getId(), null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void approveByCeoTransitionsToApprovedWithReviewerRecorded() {
        OvertimeRequest request = overtimeRequestService.createRequest(
                "sarah-chen", "2026-01-05", 2.0, "Deadline push");
        String ceoId = newAccount("CEO");

        OvertimeRequest approved = overtimeRequestService.approve(ceoId, request.getId(), "Approved, good work");

        assertThat(approved.getStatus()).isEqualTo(OvertimeRequestStatus.APPROVED);
        assertThat(approved.getReviewedBy().getId()).isEqualTo(ceoId);
        assertThat(approved.getReviewedAt()).isNotNull();
        assertThat(approved.getReviewNote()).isEqualTo("Approved, good work");
    }

    @Test
    void rejectByHrTransitionsToRejected() {
        OvertimeRequest request = overtimeRequestService.createRequest(
                "sarah-chen", "2026-01-05", 2.0, "Deadline push");
        String hrId = newAccount("HR");

        OvertimeRequest rejected = overtimeRequestService.reject(hrId, request.getId(), "Not justified");

        assertThat(rejected.getStatus()).isEqualTo(OvertimeRequestStatus.REJECTED);
        assertThat(rejected.getReviewedBy().getId()).isEqualTo(hrId);
    }

    @Test
    void reviewingAnAlreadyReviewedRequestIsAConflict() {
        OvertimeRequest request = overtimeRequestService.createRequest(
                "sarah-chen", "2026-01-05", 2.0, "Deadline push");
        String ceoId = newAccount("CEO");
        overtimeRequestService.approve(ceoId, request.getId(), null);

        assertThatThrownBy(() -> overtimeRequestService.reject(ceoId, request.getId(), null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reviewingAnUnknownRequestIsNotFound() {
        String ceoId = newAccount("CEO");

        assertThatThrownBy(() -> overtimeRequestService.approve(ceoId, "does-not-exist", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
