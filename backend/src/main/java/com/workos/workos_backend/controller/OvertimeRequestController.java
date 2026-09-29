package com.workos.workos_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.workos.workos_backend.actor.ActingPersonResolver;
import com.workos.workos_backend.dto.CreateOvertimeRequestRequest;
import com.workos.workos_backend.dto.OvertimeRequestResponse;
import com.workos.workos_backend.dto.ReviewOvertimeRequestRequest;
import com.workos.workos_backend.entity.OvertimeRequest;
import com.workos.workos_backend.service.OvertimeRequestService;

import jakarta.validation.Valid;

/** Time Clock + Overtime feature, phase B: overtime request/approval endpoints. */
@RestController
@RequestMapping("/api/overtime/requests")
public class OvertimeRequestController {

    private final OvertimeRequestService overtimeRequestService;
    private final ActingPersonResolver actingPersonResolver;

    public OvertimeRequestController(OvertimeRequestService overtimeRequestService,
            ActingPersonResolver actingPersonResolver) {
        this.overtimeRequestService = overtimeRequestService;
        this.actingPersonResolver = actingPersonResolver;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OvertimeRequestResponse create(@Valid @RequestBody CreateOvertimeRequestRequest request) {
        String actorId = actingPersonResolver.currentPersonId();
        OvertimeRequest created = overtimeRequestService.createRequest(
                actorId, request.workDate(), request.requestedHours(), request.reason());
        return OvertimeRequestResponse.from(created);
    }

    @GetMapping
    public List<OvertimeRequestResponse> list(@RequestParam(required = false) String personId) {
        String actorId = actingPersonResolver.currentPersonId();
        return overtimeRequestService.listRequests(actorId, personId).stream()
                .map(OvertimeRequestResponse::from)
                .toList();
    }

    @PostMapping("/{requestId}/approve")
    public OvertimeRequestResponse approve(@PathVariable String requestId,
            @Valid @RequestBody(required = false) ReviewOvertimeRequestRequest request) {
        String actorId = actingPersonResolver.currentPersonId();
        String note = request != null ? request.note() : null;
        return OvertimeRequestResponse.from(overtimeRequestService.approve(actorId, requestId, note));
    }

    @PostMapping("/{requestId}/reject")
    public OvertimeRequestResponse reject(@PathVariable String requestId,
            @Valid @RequestBody(required = false) ReviewOvertimeRequestRequest request) {
        String actorId = actingPersonResolver.currentPersonId();
        String note = request != null ? request.note() : null;
        return OvertimeRequestResponse.from(overtimeRequestService.reject(actorId, requestId, note));
    }
}
