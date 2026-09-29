package com.workos.workos_backend.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workos.workos_backend.actor.ActingPersonResolver;
import com.workos.workos_backend.dto.NotificationPreferencesResponse;
import com.workos.workos_backend.dto.UpdateNotificationPreferencesRequest;
import com.workos.workos_backend.service.NotificationPreferenceService;

/**
 * Settings Phase S1 endpoints. The person whose preferences are read/written
 * is always the server-resolved actor, never a client-supplied id.
 */
@RestController
@RequestMapping("/api/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;
    private final ActingPersonResolver actingPersonResolver;

    public NotificationPreferenceController(NotificationPreferenceService notificationPreferenceService,
            ActingPersonResolver actingPersonResolver) {
        this.notificationPreferenceService = notificationPreferenceService;
        this.actingPersonResolver = actingPersonResolver;
    }

    @GetMapping
    public NotificationPreferencesResponse get() {
        String actorId = actingPersonResolver.currentPersonId();
        return NotificationPreferencesResponse.from(notificationPreferenceService.getPreferences(actorId));
    }

    @PatchMapping
    public NotificationPreferencesResponse update(@Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        String actorId = actingPersonResolver.currentPersonId();
        return NotificationPreferencesResponse.from(
                notificationPreferenceService.updatePreferences(actorId, request));
    }
}
