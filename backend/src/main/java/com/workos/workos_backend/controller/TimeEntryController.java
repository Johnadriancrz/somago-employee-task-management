package com.workos.workos_backend.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.workos.workos_backend.actor.ActingPersonResolver;
import com.workos.workos_backend.dto.TimeEntryResponse;
import com.workos.workos_backend.service.TimeEntryService;

/** Implements the Time clock endpoint table from BACKEND.md. */
@RestController
@RequestMapping("/api/time")
public class TimeEntryController {

    private final TimeEntryService timeEntryService;
    private final ActingPersonResolver actingPersonResolver;

    public TimeEntryController(TimeEntryService timeEntryService, ActingPersonResolver actingPersonResolver) {
        this.timeEntryService = timeEntryService;
        this.actingPersonResolver = actingPersonResolver;
    }

    @PostMapping("/clock-in")
    @ResponseStatus(HttpStatus.CREATED)
    public TimeEntryResponse clockIn() {
        String actorId = actingPersonResolver.currentPersonId();
        return TimeEntryResponse.from(timeEntryService.clockIn(actorId));
    }

    @PostMapping("/clock-out")
    public TimeEntryResponse clockOut() {
        String actorId = actingPersonResolver.currentPersonId();
        return TimeEntryResponse.from(timeEntryService.clockOut(actorId));
    }

    /**
     * Returning {@code Optional} (not just {@code null}) so the response body
     * is the literal JSON {@code null} BACKEND.md documents — a plain
     * {@code null} return value makes Spring MVC write an empty body instead.
     */
    @GetMapping("/status")
    public Optional<TimeEntryResponse> status() {
        String actorId = actingPersonResolver.currentPersonId();
        return timeEntryService.getOpenEntry(actorId).map(TimeEntryResponse::from);
    }

    @GetMapping("/entries")
    public List<TimeEntryResponse> entries(@RequestParam(required = false) String personId) {
        String actorId = actingPersonResolver.currentPersonId();
        return timeEntryService.listEntries(actorId, personId).stream().map(TimeEntryResponse::from).toList();
    }
}
