package com.workos.workos_backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.authorization.AccessRoleChecker;
import com.workos.workos_backend.authorization.AccessRoles;
import com.workos.workos_backend.entity.NotificationEventType;
import com.workos.workos_backend.entity.OvertimeRequest;
import com.workos.workos_backend.entity.OvertimeRequestStatus;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.exception.ConflictException;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.exception.ResourceNotFoundException;
import com.workos.workos_backend.repository.OvertimeRequestRepository;
import com.workos.workos_backend.repository.PersonRepository;

/**
 * Business logic for the overtime request/approval workflow (Time Clock +
 * Overtime feature, phase B). The acting person always comes from {@code
 * ActingPersonResolver} (never a request body/param) for {@link
 * #createRequest}, same discipline as {@link TimeEntryService}.
 *
 * <p>Approval/rejection is binary (grants the full requested amount, or
 * none) and gated to CEO/HR (spec section 9's Time Clock reviewer set),
 * mirroring {@link TimeEntryService#listEntries}'s existing CEO/HR check.
 * Only an {@link OvertimeRequestStatus#APPROVED} request ever contributes to
 * a day's approved-OT hours — see {@link TimeSummaryService}.
 */
@Service
public class OvertimeRequestService {

    private final OvertimeRequestRepository overtimeRequestRepository;
    private final PersonRepository personRepository;
    private final AccessRoleChecker accessRoleChecker;
    private final NotificationService notificationService;

    public OvertimeRequestService(OvertimeRequestRepository overtimeRequestRepository,
            PersonRepository personRepository, AccessRoleChecker accessRoleChecker,
            NotificationService notificationService) {
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.personRepository = personRepository;
        this.accessRoleChecker = accessRoleChecker;
        this.notificationService = notificationService;
    }

    @Transactional
    public OvertimeRequest createRequest(String actorId, String workDateRaw, double requestedHours, String reason) {
        LocalDate workDate = parseDate(workDateRaw);

        boolean hasOpenRequest = overtimeRequestRepository.findByPersonIdAndWorkDate(actorId, workDate).stream()
                .anyMatch(existing -> existing.getStatus() != OvertimeRequestStatus.REJECTED);
        if (hasOpenRequest) {
            throw new ConflictException("An overtime request already exists for this date");
        }

        Person person = personRepository.findById(actorId)
                .orElseThrow(() -> new IllegalStateException("Acting person not found: " + actorId));
        OvertimeRequest request = new OvertimeRequest(
                UUID.randomUUID().toString(), person, workDate, requestedHours, reason, Instant.now());
        // saveAndFlush: see the matching comment in WorkspaceService.createWorkspace —
        // OvertimeRequest has the same assigned-id Persistable.isNew() behavior.
        OvertimeRequest saved = overtimeRequestRepository.saveAndFlush(request);

        List<String> reviewerIds = personRepository
                .findByAccessRoleIn(List.of(AccessRoles.CEO, AccessRoles.HR))
                .stream()
                .map(Person::getId)
                .toList();
        notificationService.notifyRecipients(actorId, reviewerIds, NotificationEventType.OVERTIME_REQUESTED,
                person.getName() + " requested " + requestedHours + "h of overtime for " + workDate + ".");
        return saved;
    }

    /**
     * @param actorId the server-resolved caller, whose accessRole decides whether personIdFilter is honored.
     * @param personIdFilter when non-blank and the actor is CEO/HR, scopes to that one person's requests;
     *                       ignored for every other actor, who is always scoped to their own id instead.
     */
    @Transactional(readOnly = true)
    public List<OvertimeRequest> listRequests(String actorId, String personIdFilter) {
        if (!accessRoleChecker.actorHasAnyRole(actorId, AccessRoles.CEO, AccessRoles.HR)) {
            return overtimeRequestRepository.findByPersonIdOrderByWorkDateDesc(actorId);
        }
        if (personIdFilter != null && !personIdFilter.isBlank()) {
            return overtimeRequestRepository.findByPersonIdOrderByWorkDateDesc(personIdFilter);
        }
        return overtimeRequestRepository.findAllByOrderByWorkDateDesc();
    }

    @Transactional
    public OvertimeRequest approve(String actorId, String requestId, String note) {
        return review(actorId, requestId, OvertimeRequestStatus.APPROVED, note,
                NotificationEventType.OVERTIME_APPROVED);
    }

    @Transactional
    public OvertimeRequest reject(String actorId, String requestId, String note) {
        return review(actorId, requestId, OvertimeRequestStatus.REJECTED, note,
                NotificationEventType.OVERTIME_REJECTED);
    }

    private OvertimeRequest review(String actorId, String requestId, OvertimeRequestStatus newStatus, String note,
            NotificationEventType eventType) {
        if (!accessRoleChecker.actorHasAnyRole(actorId, AccessRoles.CEO, AccessRoles.HR)) {
            throw new ForbiddenException("Only CEO/HR may review overtime requests");
        }
        OvertimeRequest request = overtimeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Overtime request not found: " + requestId));
        if (request.getStatus() != OvertimeRequestStatus.PENDING) {
            throw new ConflictException("This overtime request has already been reviewed");
        }
        Person reviewer = personRepository.findById(actorId)
                .orElseThrow(() -> new IllegalStateException("Acting person not found: " + actorId));

        request.setStatus(newStatus);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(Instant.now());
        request.setReviewNote(note);
        OvertimeRequest saved = overtimeRequestRepository.save(request);

        String verb = newStatus == OvertimeRequestStatus.APPROVED ? "approved" : "rejected";
        notificationService.notifyRecipients(actorId, List.of(request.getPerson().getId()), eventType,
                reviewer.getName() + " " + verb + " your overtime request for " + request.getWorkDate() + ".");
        return saved;
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("workDate must be a valid ISO date (yyyy-MM-dd)");
        }
    }
}
