package com.workos.workos_backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.OvertimeRequest;
import com.workos.workos_backend.entity.OvertimeRequestStatus;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, String> {

    /** GET /api/overtime/requests with no ?personId= filter — every request, newest work date first. */
    List<OvertimeRequest> findAllByOrderByWorkDateDesc();

    /** GET /api/overtime/requests?personId=... — one person's requests, newest work date first. */
    List<OvertimeRequest> findByPersonIdOrderByWorkDateDesc(String personId);

    /** The create-time duplicate check: does this person already have a non-rejected request for this date. */
    List<OvertimeRequest> findByPersonIdAndWorkDate(String personId, LocalDate workDate);

    /** TimeSummaryService: the one request (if any) that can contribute approved-OT hours for a person+date. */
    Optional<OvertimeRequest> findByPersonIdAndWorkDateAndStatus(
            String personId, LocalDate workDate, OvertimeRequestStatus status);
}
